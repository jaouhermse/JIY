package de.jiy.family;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.Set;

/** Läuft im Hintergrund und schließt Apps, die nicht freigegeben sind. */
public class GuardService extends Service {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String current = null;
    private long lastBlock = 0;
    private Set<String> always;
    private String lastPkg = null;
    private long lastTick = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            check();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Notifier.ensureChannel(this);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, Notifier.CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("JIY Kinderschutz aktiv")
                .setContentText("Nur freigegebene Apps können geöffnet werden.")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(77, n);
        }
        always = AppGuard.alwaysAllowed(this);
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!AppGuard.isOn(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        always = AppGuard.alwaysAllowed(this);
        return START_STICKY;
    }

    private void check() {
        if (!AppGuard.isOn(this)) {
            stopSelf();
            return;
        }
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents ev = usm.queryEvents(now - 15000, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) current = e.getPackageName();
            }
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean screenOn = pm == null || pm.isInteractive();
            if (screenOn && current != null && current.equals(lastPkg) && now - lastTick < 5000) {
                AppGuard.addUsage(this, current, now - lastTick);
            }
            lastPkg = screenOn ? current : null;
            lastTick = now;
            if (current == null || always.contains(current)) return;
            String reason = null;
            int lim = 0;
            if (!AppGuard.allowed(this).contains(current)) {
                reason = "deny";
            } else {
                lim = AppGuard.limitMin(this, current);
                if (lim > 0 && AppGuard.usedSec(this, current) >= lim * 60L) reason = "time";
            }
            if (reason == null) return;
            if (now - lastBlock < 1500) return;
            lastBlock = now;
            Intent b = new Intent(this, BlockActivity.class);
            b.putExtra("pkg", current);
            b.putExtra("reason", reason);
            b.putExtra("limit", lim);
            b.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(b);
            current = getPackageName();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
