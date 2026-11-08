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
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
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
    // the notification swiped away (Android 14+ lets that happen even to a foreground service's)
    static final String ACTION_DISMISSED = "br.app.stagebook.player.DISMISSED";
    // with nothing playing, the app out of sight or the USB interface gone this long turns the player off: it stayed on
    // for 16 h on the S26 with no notification left to turn it off (user, 07/11)
    static final long IDLE_OFF_MS = 10 * 60 * 1000L;
    static final long WAKE_TIMEOUT_MS = 30 * 60 * 1000L;
    static final long WATCHDOG_MS = 5000;
    // time for the interface clock to lock after connecting: when the connection is good it arrives in ~2 ms; with 3 s
    // + the watchdog's 5 s pass, the reconnection landed on the user's play (19/10/2026). The first try waits only
    // 400 ms: after a replug the silent first connection was a 1 s gap of silence, heard. A slow device is safe anyway:
    // each retry waits one step longer (400, 800, 1200 ms, see fbWait), so one whose first input packet legitimately
    // takes longer gets the time on the next try instead of looping, and FB_RETRIES still ends it
    static final long FB_WAIT_MS = 400;
    static final int FB_RETRIES = 3;
    static final int ANDROID_MISSES = 6;   // Android takes a while to list an interface just plugged in
    static final long ATTACH_GRANT_WAIT_MS = 1500;   // waits for Android's automatic permission
    long connectedAt;
    int fbRetries;
    int androidMisses;   // watchdog rounds with the interface plugged in and Android offering no output for it

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
    boolean isPlaying, idleArmed;
    static volatile boolean appVisible = true;
    // the USB interface opened through Android's audio (Settings, off by default): where talking to USB directly gets no
    // packets back (S26, B-33) but Android plays on it (other apps do). Set by StagePlayer from Settings.
    static volatile boolean viaAndroid;
    UsbDevice androidUsb;   // the interface open that way (deviceOut is true then)

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
        if (intent != null && ACTION_DISMISSED.equals(intent.getAction())) {
            // swiping it away with nothing playing says "turn it off"; while playing it can't go: it comes back
            if (!running) stopSelf();
            else if (isPlaying) getSystemService(NotificationManager.class).notify(1, buildNotification(status));
            else shutdown();
            return running ? START_STICKY : START_NOT_STICKY;
        }
        ServiceCompat.startForeground(this, 1, buildNotification(getString(R.string.player_starting)),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
        if (running) {
            if (link == null) connectUsb();
            return START_STICKY;
        }
        running = true;
        um = (UsbManager) getSystemService(USB_SERVICE);
        // only the app's own permission answer (its PendingIntent) and Android's attach/detach notices, which reach a
        // not-exported receiver too: another app can't fake a "permission denied" (audio audit P3)
        ContextCompat.registerReceiver(this, usbReceiver, usbFilter(), ContextCompat.RECEIVER_NOT_EXPORTED);
        wake = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stagebook:play");
        // Android reads its USB routing when the interface connects: written first (off the main thread), only now
        // that the player really runs, and given back in shutdown()
        StagePlayer.routeUsbThen(this, !viaAndroid, () -> { if (running && link == null && !connecting) connectUsb(); });
        main.postDelayed(watchdog, WATCHDOG_MS);
        checkIdle();
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
                usbQueues().edit().putInt(usbKey(link.dev), Engine.queueMs()).apply();   // it may have halved its queue
                releaseUsb();
                connectUsb();
            } else if (link != null && Engine.feedback() == 1) {
                fbRetries = 0;
            } else if (fbRetryDue()) {
                fbRetry();
            } else if (link == null && androidUsb == null) {
                // permission granted through Android's window, without our request's notice; through Android no
                // permission is needed, only Android listing the interface (it may take a moment after plugging in)
                UsbDevice d = UsbLink.find(um);
                if (d != null && (viaAndroid || um.hasPermission(d))) connectUsb();
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

    private android.content.SharedPreferences usbQueues() { return getSharedPreferences("usb_queue", MODE_PRIVATE); }

    private static String usbKey(UsbDevice d) { return String.format(java.util.Locale.ROOT, "%04x:%04x", d.getVendorId(), d.getProductId()); }

    boolean fbRetryDue() {
        // no input on purpose (an adaptive output follows us): there's no clock to wait for
        return link != null && link.inEp >= 0 && fbRetries < FB_RETRIES && SystemClock.elapsedRealtime() - connectedAt >= fbWait();
    }

    long fbWait() { return FB_WAIT_MS * (1 + fbRetries); }

    void fbRetry() {
        fbRetries++;
        Log.w(TAG, "interface clock didn't arrive: reconnecting (" + fbRetries + "/" + FB_RETRIES + ")");
        releaseUsb(true);   // the stream starts again at once: no silent tail
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
    void releaseUsb() { releaseUsb(false); }

    void releaseUsb(boolean fast) {
        link = null;
        deviceOut = false;
        androidUsb = null;
        connected = false;
        connecting = false;
        usbGen++;
        usbOps.execute(() -> {
            if (fast) Engine.stopFast(); else Engine.stop();
            if (usbOpen != null) { usbOpen.close(); usbOpen = null; }
        });
    }

    void connectUsb() {
        if (link != null || connecting) return;
        UsbDevice d = UsbLink.find(um);
        if (d == null) {
            if (hadUsb) setStatus(getString(R.string.player_usb_lost));
            else useDevice();
            checkIdle();
            return;
        }
        if (viaAndroid) { useAndroidUsb(d); return; }
        if (!um.hasPermission(d)) {
            Intent it = new Intent(ACTION_USB_PERM).setPackage(getPackageName());
            int mutable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
            um.requestPermission(d, PendingIntent.getBroadcast(this, 0, it, mutable));
            setStatus(getString(R.string.player_usb_permission_note));   // short, so the stage shows it as a note
            return;
        }
        if (deviceOut) releaseUsb();   // the USB interface arrived: leave the device and go to it
        connecting = true;
        int gen = usbGen;
        setStatus(getString(R.string.player_usb_connecting));
        usbOps.execute(() -> {
            UsbLink l = new UsbLink();
            String err;
            try {
                err = l.open(um, d);
            } catch (RuntimeException e) {
                // the interface unplugged mid-conversation, a descriptor Android refused…: a failed connection, and
                // "connecting" doesn't stay on forever (the watchdog tries again) (audio audit P1)
                err = "USB open failed: " + e;
            }
            if (err != null) {
                try { l.close(); } catch (RuntimeException ignored) {}
            } else {
                Engine.setRate(l.rate);
                Engine.setQueue(usbQueues().getInt(usbKey(d), 64));
                Engine.start(l.conn.getFileDescriptor(), l.ep, l.maxPacket, l.channels, l.subslot,
                        l.outIfNum, l.outAltNum, l.inEp, l.inMaxPacket, l.inChannels * l.inSubslot);
                usbOpen = l;
            }
            String result = err;
            // turned off or dropped midway: releaseUsb (queued after this one) already closes this connection
            main.post(() -> { if (gen == usbGen) usbReady(l, result); });
        });
    }

    // No USB interface: opens the device's output, also off the UI thread.
    void useDevice() {
        if (deviceOut || connecting) return;
        connecting = true;
        int gen = usbGen;
        usbOps.execute(() -> {
            Engine.setRate(48000);
            boolean ok = Engine.startDevice(0, 2);
            if (!ok) Log.w(TAG, Engine.status());
            main.post(() -> { if (gen == usbGen) deviceReady(ok); });
        });
    }

    // The USB interface through Android's audio, with all its channels: the engine's device path aimed at it.
    void useAndroidUsb(UsbDevice d) {
        if (androidUsb != null || connecting) return;
        if (deviceOut) releaseUsb();   // was on the device's own output: the interface arrived
        // the output of this interface (same product name): with a USB headset on a hub too, the first USB output could
        // be the headset (audit 02/11 #6); the first one only if no name matches
        AudioDeviceInfo out = null;
        String wanted = d.getProductName() != null ? d.getProductName().trim() : "";
        for (AudioDeviceInfo a : getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            // a card with input and output is listed as a headset on some phones (seen on the S26)
            if (a.getType() != AudioDeviceInfo.TYPE_USB_DEVICE && a.getType() != AudioDeviceInfo.TYPE_USB_HEADSET) continue;
            if (out == null) out = a;
            CharSequence name = a.getProductName();
            // Android names it "USB-Audio - <interface name>" (seen on the S26): the interface's name inside it
            if (!wanted.isEmpty() && name != null && name.toString().toLowerCase(java.util.Locale.ROOT).contains(wanted.toLowerCase(java.util.Locale.ROOT))) { out = a; break; }
        }
        // Android hasn't listed it yet (just plugged in): the watchdog tries again
        if (out == null) {
            // after a while it's no longer a matter of waiting: say so instead of "connecting" for ever
            setStatus(getString(++androidMisses < ANDROID_MISSES ? R.string.player_usb_connecting : R.string.player_usb_no_android, d.getProductName()));
            return;
        }
        androidMisses = 0;
        int ch = 2;
        for (int n : out.getChannelCounts()) ch = Math.max(ch, n);
        final int id = out.getId(), count = Math.min(ch, 8);
        connecting = true;
        int gen = usbGen;
        setStatus(getString(R.string.player_usb_connecting));
        usbOps.execute(() -> {
            Engine.setRate(48000);
            boolean ok = Engine.startDevice(id, count);
            if (!ok) Log.w(TAG, Engine.status());
            main.post(() -> { if (gen == usbGen) androidUsbReady(ok, d, count); });
        });
    }

    void androidUsbReady(boolean ok, UsbDevice d, int count) {
        connecting = false;
        if (!ok) { setStatus(getString(R.string.player_usb_failed)); return; }
        deviceOut = true;
        androidUsb = d;
        hadUsb = true;
        outputKey = String.format(java.util.Locale.ROOT, "usb:%04x:%04x", d.getVendorId(), d.getProductId());
        String n = d.getProductName();
        outputName = n != null && !n.trim().isEmpty() ? n.trim() : "USB";
        outputCount = count;
        connected = true;
        checkIdle();
        Log.i(TAG, "USB through Android: " + outputName + ", " + count + " channels");
        setStatus(getString(R.string.player_usb_ready, outputName));
    }

    /** The Settings choice changed (StagePlayer): connect again the new way. */
    static void reconnect() {
        PlayerService s = instance;
        if (s != null && running) s.main.post(() -> { s.releaseUsb(); s.connectUsb(); });
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
        main.postDelayed(fbCheck, fbWait());
        checkIdle();
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
                if (d != null && UsbLink.isAudioOut(d) && link == null && androidUsb == null) {
                    if (!viaAndroid && um.hasPermission(d)) connectUsb();
                    else main.postDelayed(() -> { if (running && link == null) connectUsb(); }, ATTACH_GRANT_WAIT_MS);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(i.getAction())) {
                UsbDevice d = IntentCompat.getParcelableExtra(i, UsbManager.EXTRA_DEVICE, UsbDevice.class);
                if (d != null && (link != null && d.equals(link.dev) || d.equals(androidUsb))) {
                    releaseUsb();
                    fbRetries = 0;   // new cable: new attempts to get the clock
                    holdWake(false);
                    setStatus(getString(R.string.player_usb_lost));
                    checkIdle();
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
        if (s != null) s.main.post(() -> {
            s.isPlaying = on;
            s.holdWake(on);
            if (on && s.muted()) s.setStatus(s.getString(R.string.player_usb_muted));
            s.checkIdle();
        });
    }

    /** The app came to the front or left it (MainActivity). */
    public static void setAppVisible(boolean on) {
        appVisible = on;
        PlayerService s = instance;
        if (s != null) s.main.post(s::checkIdle);
    }

    // the USB interface dropped and hasn't come back: the stage keeps waiting for it instead of the speaker
    boolean usbLost() {
        return hadUsb && link == null && androidUsb == null && !connecting;
    }

    boolean idle() {
        return running && !isPlaying && (!appVisible || usbLost());
    }

    // counts from the moment it went idle: a later check while still idle doesn't restart the clock
    void checkIdle() {
        boolean now = idle();
        if (now && !idleArmed) main.postDelayed(idleOff, IDLE_OFF_MS);
        else if (!now && idleArmed) main.removeCallbacks(idleOff);
        idleArmed = now;
    }

    final Runnable idleOff = () -> {
        idleArmed = false;
        if (idle()) {
            Log.i(TAG, "idle for " + IDLE_OFF_MS / 60000 + " min: turning the player off");
            shutdown();
        }
    };

    /** Through Android the media volume counts, and it's kept per output: at zero the song plays and nothing comes out. */
    boolean muted() {
        return androidUsb != null && getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
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
                .setDeleteIntent(PendingIntent.getService(this, 2,
                        new Intent(this, PlayerService.class).setAction(ACTION_DISMISSED), PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(null, getString(R.string.player_turn_off), stop).build())
                .build();
    }

    void shutdown() {
        main.removeCallbacks(watchdog);
        main.removeCallbacks(fbCheck);
        main.removeCallbacks(idleOff);
        idleArmed = false;
        releaseUsb();
        if (running) {
            try { unregisterReceiver(usbReceiver); } catch (IllegalArgumentException ignored) {}
        }
        holdWake(false);
        running = false;
        status = "";
        StagePlayer.releaseUsbRoute(this);
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
