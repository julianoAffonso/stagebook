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
 * Stagebook's player on the USB audio interface: connects off the UI thread, a 5 s watchdog that reconnects, checks the
 * interface clock, wake lock only while playing and turns off in the background. Plays the backing track of the song
 * open on stage (commands in {@link StagePlayer}). Only starts on the first play with player mode on: before that no
 * USB, engine or service.
 */
public class PlayerService extends Service {
    static final String TAG = "stagebook";
    static final String ACTION_STOP = "br.app.stagebook.player.STOP";
    static final String ACTION_USB_PERM = "br.app.stagebook.player.USB_PERM";
    static final long WAKE_TIMEOUT_MS = 30 * 60 * 1000L;
    static final long WATCHDOG_MS = 5000;
    // time for the interface clock to lock after connecting: when the connection is good it arrives in ~2 ms; with 3 s
    // + the watchdog's 5 s pass, the reconnection landed on the user's play (19/10/2026)
    static final long FB_WAIT_MS = 1000;
    static final int FB_RETRIES = 3;
    static final long ATTACH_GRANT_WAIT_MS = 1500;   // waits for Android's automatic permission
    long connectedAt;
    int fbRetries;

    // state read by the screen (StagePlayer)
    static volatile boolean running;
    static volatile boolean connected;
    static volatile String status = "";
    // the connected output (for routing saved per output): "device" or "usb:vid:pid", name and how many outputs
    static volatile String outputKey = "", outputName = "";
    static volatile int outputCount;
    // playing on the device's output (no USB interface). If USB was connected and dropped, it doesn't fall back to the
    // device mid-show: it waits for USB to come back (the click would go to the speaker).
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

    // The USB stream can die without the disconnect notice (the interface restarts, a cable error): with the stream
    // stopped many interfaces repeat the last chunk (a whine). Every 5 s, if the engine dropped, close and connect
    // again.
    final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (deviceOut && !Engine.isRunning()) {
                Log.w(TAG, "device output dropped (" + Engine.status() + "): reopening");
                releaseUsb();
                connectUsb();
            } else if (link != null && !Engine.isRunning()) {
                Log.w(TAG, "USB stream dropped (" + Engine.status() + "): reconnecting");
                releaseUsb();
                connectUsb();
            } else if (link != null && Engine.feedback() == 1) {
                fbRetries = 0;
            } else if (fbRetryDue()) {
                fbRetry();
            } else if (link == null) {
                // permission granted through Android's window, without our request's notice
                UsbDevice d = UsbLink.find(um);
                if (d != null && um.hasPermission(d)) connectUsb();
            }
            main.postDelayed(this, WATCHDOG_MS);
        }
    };

    // The 1st connection after plugging in or powering the desk back on sometimes comes with the input silent: without
    // its clock, a hiccup every ~70 s. Reconnecting fixes it (seen on 19/10/2026). Checked FB_WAIT_MS after each
    // connection and again by the watchdog.
    final Runnable fbCheck = () -> {
        if (running && Engine.isRunning() && Engine.feedback() != 1 && fbRetryDue()) fbRetry();
    };

    boolean fbRetryDue() {
        return link != null && fbRetries < FB_RETRIES && SystemClock.elapsedRealtime() - connectedAt >= FB_WAIT_MS;
    }

    void fbRetry() {
        fbRetries++;
        Log.w(TAG, "interface clock didn't arrive: reconnecting (" + fbRetries + "/" + FB_RETRIES + ")");
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

    // Stopping the engine waits ~0.5 s of silence (without it the interface repeats the last chunk) and opening a
    // conversation with the interface, which may not answer: both run off the UI thread (an interface not answering
    // SET_INTERFACE froze the screen and caused an ANR). A single executor, in order: connecting only starts after the
    // previous shutdown, otherwise the interface would still be tied to the old stream.
    static final ExecutorService usbOps = Executors.newSingleThreadExecutor();
    static UsbLink usbOpen;   // the connection the engine is using; only read and written inside usbOps
    int usbGen;               // changes on every shutdown: a connection that finishes after it is discarded
    boolean connecting;

    // closes the current output (USB or device)
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
        if (deviceOut) releaseUsb();   // the USB interface arrived: leave the device and go to it
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
            // turned off or dropped midway: releaseUsb (queued after this one) already closes this connection
            main.post(() -> { if (gen == usbGen) usbReady(l, err); });
        });
    }

    // No USB interface: opens the device's output, also off the UI thread.
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

    // Back on the UI thread, with the connection open (or its error).
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
                    fbRetries = 0;   // new cable: new attempts to get the clock
                    holdWake(false);
                    setStatus(getString(R.string.player_usb_lost));
                }
            }
        }
    };

    // Only holds the wake lock while playing; timeout longer than any song, taken again on every play.
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

    /** Playing or stopped (StagePlayer): the wake lock follows. */
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
