package br.app.stagebook.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import br.app.stagebook.MainActivity;
import br.app.stagebook.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Player do Stagebook na interface de áudio USB: conecta fora da tela, vigia de 5 s que reconecta, confere o
 * relógio da interface, wake lock só tocando e desliga em segundo plano. Toca o backing track da música aberta
 * no palco (comandos em {@link StagePlayer}).
 * Só sobe no primeiro play com o modo player ligado: antes disso nada de USB, motor ou serviço.
 */
public class PlayerService extends Service {
    static final String TAG = "stagebook";
    static final String ACTION_STOP = "br.app.stagebook.player.STOP";
    static final String ACTION_USB_PERM = "br.app.stagebook.player.USB_PERM";
    static final long WAKE_TIMEOUT_MS = 30 * 60 * 1000L;
    static final long WATCHDOG_MS = 5000;
    // tempo para o relógio da interface travar depois de conectar: quando a conexão vem boa ele chega em
    // ~2 ms; com 3 s + a passada de 5 s do vigia, a reconexão caía no play do usuário (19/10/2026)
    static final long FB_WAIT_MS = 1000;
    static final int FB_RETRIES = 3;
    static final long ATTACH_GRANT_WAIT_MS = 1500;   // espera a autorização automática do Android
    long connectedAt;
    int fbRetries;

    // estado lido pela tela (StagePlayer)
    static volatile boolean running;
    static volatile boolean connected;
    static volatile String status = "";
    // a saída conectada (para o roteamento guardado por saída): "device" ou "usb:vid:pid", nome e quantas saídas
    static volatile String outputKey = "", outputName = "";
    static volatile int outputCount;
    // tocando pela saída do aparelho (sem interface USB). Se a USB já esteve conectada e caiu, não cai para o
    // aparelho no meio do show: espera a USB voltar (o click iria para o alto-falante).
    boolean deviceOut, hadUsb;

    final Handler main = new Handler(Looper.getMainLooper());
    UsbManager um;
    UsbLink link;
    PowerManager.WakeLock wake;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        ServiceCompat.startForeground(this, 1, buildNotification(getString(R.string.player_starting)),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
        if (running) {
            if (link == null) connectUsb();
            return START_STICKY;
        }
        running = true;
        um = (UsbManager) getSystemService(USB_SERVICE);
        ContextCompat.registerReceiver(this, usbReceiver, usbFilter(), ContextCompat.RECEIVER_EXPORTED);
        wake = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stagebook:play");
        connectUsb();
        main.postDelayed(watchdog, WATCHDOG_MS);
        return START_STICKY;
    }

    // O fluxo USB pode morrer sem o aviso de desconexão (a interface reinicia, erro no cabo): com o
    // fluxo parado muitas interfaces repetem o último trecho (apito). A cada 5 s, se o motor caiu, fecha e conecta de novo.
    final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (deviceOut && !Engine.isRunning()) {
                Log.w(TAG, "saída do aparelho caiu (" + Engine.status() + "): abrindo de novo");
                releaseUsb();
                connectUsb();
            } else if (link != null && !Engine.isRunning()) {
                Log.w(TAG, "fluxo USB caiu (" + Engine.status() + "): reconectando");
                releaseUsb();
                connectUsb();
            } else if (link != null && Engine.feedback() == 1) {
                fbRetries = 0;
            } else if (fbRetryDue()) {
                fbRetry();
            } else if (link == null) {
                // autorização dada pela janela do Android, sem o aviso do nosso pedido
                UsbDevice d = UsbLink.find(um);
                if (d != null && um.hasPermission(d)) connectUsb();
            }
            main.postDelayed(this, WATCHDOG_MS);
        }
    };

    // A 1ª conexão depois de plugar ou religar a mesa às vezes vem com a entrada muda: sem o relógio dela,
    // solavanco a cada ~70 s. Reconectar resolve (visto em 19/10/2026). Conferido FB_WAIT_MS depois de cada
    // conexão e, de novo, pelo vigia.
    final Runnable fbCheck = () -> {
        if (running && Engine.isRunning() && Engine.feedback() != 1 && fbRetryDue()) fbRetry();
    };

    boolean fbRetryDue() {
        return link != null && fbRetries < FB_RETRIES && SystemClock.elapsedRealtime() - connectedAt >= FB_WAIT_MS;
    }

    void fbRetry() {
        fbRetries++;
        Log.w(TAG, "relógio da interface não chegou: reconectando (" + fbRetries + "/" + FB_RETRIES + ")");
        releaseUsb();
        connectUsb();
    }

    static IntentFilter usbFilter() {
        IntentFilter f = new IntentFilter(ACTION_USB_PERM);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        return f;
    }

    void setStatus(String s) {
        status = s;
        Log.i(TAG, s);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(1, buildNotification(s));
        StagePlayer.onServiceChanged();
    }

    // Parar o motor espera ~0,5 s de silêncio (sem ele a interface repete o último trecho) e abrir conversa com a interface, que
    // pode não responder: os dois rodam fora da tela (uma interface sem responder ao SET_INTERFACE
    // prendia a tela e dava ANR). Um único executor, em ordem: conectar só começa depois do desligamento
    // anterior, senão a interface ainda estaria presa ao fluxo antigo.
    static final ExecutorService usbOps = Executors.newSingleThreadExecutor();
    static UsbLink usbOpen;   // a conexão que o motor está usando; só lida e escrita dentro do usbOps
    int usbGen;               // muda a cada desligamento: uma conexão que termina depois dele é descartada
    boolean connecting;

    // fecha a saída atual (USB ou do aparelho)
    void releaseUsb() {
        link = null;
        deviceOut = false;
        connected = false;
        connecting = false;
        usbGen++;
        usbOps.execute(() -> {
            Engine.stop();
            if (usbOpen != null) { usbOpen.close(); usbOpen = null; }
        });
    }

    void connectUsb() {
        if (link != null || connecting) return;
        UsbDevice d = UsbLink.find(um);
        if (d == null) {
            if (hadUsb) setStatus(getString(R.string.player_usb_lost));
            else useDevice();
            return;
        }
        if (!um.hasPermission(d)) {
            Intent it = new Intent(ACTION_USB_PERM).setPackage(getPackageName());
            int mutable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
            um.requestPermission(d, PendingIntent.getBroadcast(this, 0, it, mutable));
            setStatus(getString(R.string.player_usb_permission));
            return;
        }
        if (deviceOut) releaseUsb();   // a interface USB chegou: sai do aparelho e vai para ela
        connecting = true;
        int gen = usbGen;
        setStatus(getString(R.string.player_usb_connecting));
        usbOps.execute(() -> {
            UsbLink l = new UsbLink();
            String err = l.open(um, d);
            if (err != null) {
                l.close();
            } else {
                Engine.start(l.conn.getFileDescriptor(), l.ep, l.maxPacket, l.channels, l.subslot,
                        l.outIfNum, l.outAltNum, l.inEp, l.inMaxPacket, l.inChannels * l.inSubslot);
                usbOpen = l;
            }
            // desligou ou caiu no meio: o releaseUsb (na fila depois desta) já fecha esta conexão
            main.post(() -> { if (gen == usbGen) usbReady(l, err); });
        });
    }

    // Sem interface USB: abre a saída do aparelho, também fora da tela.
    void useDevice() {
        if (deviceOut || connecting) return;
        connecting = true;
        int gen = usbGen;
        usbOps.execute(() -> {
            boolean ok = Engine.startDevice();
            if (!ok) Log.w(TAG, Engine.status());
            main.post(() -> { if (gen == usbGen) deviceReady(ok); });
        });
    }

    void deviceReady(boolean ok) {
        connecting = false;
        if (!ok) { setStatus(getString(R.string.player_device_failed)); return; }
        deviceOut = true;
        outputKey = "device";
        outputName = "";
        outputCount = 2;
        connected = true;
        setStatus(getString(R.string.player_device_out));
    }

    // De volta na tela, com a conexão aberta (ou o erro dela).
    void usbReady(UsbLink l, String err) {
        connecting = false;
        if (err != null) { Log.w(TAG, err); setStatus(getString(R.string.player_usb_failed)); return; }
        link = l;
        hadUsb = true;
        outputKey = String.format(java.util.Locale.ROOT, "usb:%04x:%04x", l.dev.getVendorId(), l.dev.getProductId());
        outputName = l.name();
        outputCount = l.channels;
        connected = true;
        connectedAt = SystemClock.elapsedRealtime();
        main.removeCallbacks(fbCheck);
        main.postDelayed(fbCheck, FB_WAIT_MS);
        Log.i(TAG, link.describe());
        setStatus(getString(R.string.player_usb_ready, link.name()));
    }

    final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (ACTION_USB_PERM.equals(i.getAction())) {
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) connectUsb();
                else { setStatus(getString(R.string.player_usb_denied)); if (!hadUsb) useDevice(); }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(i.getAction())) {
                UsbDevice d = IntentCompat.getParcelableExtra(i, UsbManager.EXTRA_DEVICE, UsbDevice.class);
                if (d != null && UsbLink.isAudioOut(d) && link == null) {
                    if (um.hasPermission(d)) connectUsb();
                    else main.postDelayed(() -> { if (running && link == null) connectUsb(); }, ATTACH_GRANT_WAIT_MS);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(i.getAction())) {
                UsbDevice d = IntentCompat.getParcelableExtra(i, UsbManager.EXTRA_DEVICE, UsbDevice.class);
                if (d != null && link != null && d.equals(link.dev)) {
                    releaseUsb();
                    fbRetries = 0;   // cabo novo: novas tentativas de pegar o relógio
                    holdWake(false);
                    setStatus(getString(R.string.player_usb_lost));
                }
            }
        }
    };

    // Só segura o wake lock tocando; tempo limite maior que qualquer música, pego de novo a cada play.
    void holdWake(boolean on) {
        if (wake == null) return;
        if (on && !wake.isHeld()) wake.acquire(WAKE_TIMEOUT_MS);
        else if (!on && wake.isHeld()) wake.release();
    }

    static PlayerService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    /** Tocando ou parado (StagePlayer): o wake lock acompanha. */
    static void playing(boolean on) {
        PlayerService s = instance;
        if (s != null) s.main.post(() -> s.holdWake(on));
    }

    Notification buildNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("play", getString(R.string.player_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, PlayerService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "play")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.player_turn_off), stop).build())
                .build();
    }

    void shutdown() {
        main.removeCallbacks(watchdog);
        main.removeCallbacks(fbCheck);
        releaseUsb();
        if (running) {
            try { unregisterReceiver(usbReceiver); } catch (IllegalArgumentException ignored) {}
        }
        holdWake(false);
        running = false;
        status = "";
        StagePlayer.onServiceChanged();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (running) shutdown();
        instance = null;
        super.onDestroy();
    }
}
