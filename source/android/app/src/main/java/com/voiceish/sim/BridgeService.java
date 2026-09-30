package com.voiceish.sim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public final class BridgeService extends Service {
    static final String STOP = "com.voiceish.sim.STOP";
    static volatile boolean running;
    static volatile String lastError = "";
    static volatile String simLabel = "";
    static volatile int subscriptionId = -1;
    private static volatile BridgeService instance;
    private LocalServer server;
    private PowerManager.WakeLock wakeLock;
    private SmsEngine engine;
    @Override public void onCreate() { super.onCreate(); instance = this; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { shutdown(); stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        try {
            SimState sim = SimState.resolve(this);
            NotificationManager notifications = getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel("bridge", "Phone texting bridge", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, BridgeService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, "bridge").setSmallIcon(R.drawable.ic_voiceish)
                .setContentTitle("Voiceish SIM bridge is running").setContentText("Texting bridge · " + sim.label)
                .setStyle(new Notification.BigTextStyle().bigText("Your paired Chrome extension can read and send SMS through " + sim.label + " on this local network."))
                .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "Stop bridge", stop).build()).build();
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
            else startForeground(1, notification);
            engine = new SmsEngine(this, sim);
            server = new LocalServer(new Protocol(KeyStoreHelper.get(this), engine)); server.start();
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceishSIM:Bridge");
            // User explicitly starts and stops this foreground session. Always release in shutdown.
            wakeLock.acquire();
            simLabel = sim.label; subscriptionId = sim.id; lastError = ""; running = true;
        } catch (Exception error) {
            lastError = error instanceof BridgeException ? error.getMessage() : error instanceof SecurityException ? "Android blocked bridge startup: " + error.getMessage() : "Unable to start the bridge. Check permissions and make sure port 8765 is free, then try again.";
            shutdown(); stopSelf();
        }
        return START_NOT_STICKY;
    }
    static void stopRunning(Context context) {
        BridgeService service = instance;
        if (service != null) service.shutdown();
        context.stopService(new Intent(context, BridgeService.class));
    }
    private synchronized void shutdown() {
        running = false;
        if (engine != null) engine.stop();
        if (server != null) { server.close(); server = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null; stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onDestroy() { shutdown(); if (instance == this) instance = null; super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
