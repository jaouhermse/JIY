package de.jiy.family;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Plant Erinnerungen (Gebet, Termine, Rechnungen) – funktionieren auch, wenn die App geschlossen ist. */
public final class Scheduler {

    private static final String PREFS = "jiy_alarms";
    private static final String KEY = "list";

    private Scheduler() {
    }

    /** Ersetzt alle geplanten Erinnerungen durch die neue Liste (JSON-Array mit id, at, title, body). */
    public static synchronized void replaceAll(Context c, String json) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cancelAll(c, p.getString(KEY, "[]"));
        p.edit().putString(KEY, json == null ? "[]" : json).apply();
        scheduleAll(c, json);
    }

    /** Nach einem Neustart des Handys erneut planen. */
    public static void restore(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        scheduleAll(c, p.getString(KEY, "[]"));
    }

    private static void scheduleAll(Context c, String json) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null || json == null) return;
        long now = System.currentTimeMillis();
        try {
            JSONArray a = new JSONArray(json);
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                long at = o.optLong("at", 0);
                if (at <= now) continue;
                PendingIntent pi = intent(c, o.optString("id"), o.optString("title"), o.optString("body"));
                try {
                    if (exact) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    }
                } catch (SecurityException se) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void cancelAll(Context c, String json) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null || json == null) return;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                am.cancel(intent(c, a.getJSONObject(i).optString("id"), "", ""));
            }
        } catch (Exception ignored) {
        }
    }

    private static PendingIntent intent(Context c, String id, String title, String body) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction("de.jiy.family.ALARM." + id);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getBroadcast(c, id.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
