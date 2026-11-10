package de.jiy.family;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;

/** Spielt den Adhan oder den Weckton ab – mit „Stopp“ in der Benachrichtigung. */
public class SoundService extends Service {

    static final String CHANNEL = "jiy_sound";
    static final String STOP = "de.jiy.family.SOUND_STOP";
    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static File adhanFile(Context c) {
        return new File(c.getFilesDir(), "adhan.mp3");
    }

    static void start(Context c, String mode, String title, String body) {
        Intent i = new Intent(c, SoundService.class);
        i.putExtra("mode", mode);
        i.putExtra("title", title);
        i.putExtra("body", body);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {
            Notifier.show(c, (title == null ? "x" : title).hashCode(), title, body);
        }
    }

    static void stop(Context c) {
        try {
            c.stopService(new Intent(c, SoundService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String mode = intent == null ? "ring" : intent.getStringExtra("mode");
        String title = intent == null ? "JIY" : intent.getStringExtra("title");
        String body = intent == null ? "" : intent.getStringExtra("body");
        boolean ring = !"adhan".equals(mode);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Adhan & Wecker", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Intent stopI = new Intent(this, SoundService.class).setAction(STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 9, stopI, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 8, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title == null ? (ring ? "⏰ Wecker" : "🕌 Adhan") : title)
                .setContentText(body == null ? "" : body)
                .setCategory(ring ? Notification.CATEGORY_ALARM : Notification.CATEGORY_REMINDER)
                .setOngoing(true)
                .setContentIntent(openPi)
                .setDeleteIntent(stopPi)
                .addAction(new Notification.Action.Builder(null, "■ Stopp", stopPi).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(78, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(78, n);

        release();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(ring ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(ring ? AudioAttributes.CONTENT_TYPE_SONIFICATION : AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            File f = adhanFile(this);
            if (!ring && f.exists()) {
                player.setDataSource(f.getAbsolutePath());
            } else {
                Uri u = RingtoneManager.getDefaultUri(ring ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION);
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                player.setDataSource(this, u);
                player.setLooping(ring);
            }
            player.setOnCompletionListener(mp -> stopSelf());
            player.setOnErrorListener((mp, w, e) -> {
                stopSelf();
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopSelf();
        }
        // Sicherheit: Wecker nach 3 Minuten, Adhan nach 6 Minuten automatisch beenden
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopSelf, ring ? 180000 : 360000);
        return START_NOT_STICKY;
    }

    private void release() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
