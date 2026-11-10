package de.jiy.family;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Kinderschutz: nur freigegebene Apps dürfen geöffnet werden. */
public final class AppGuard {

    static final String PREFS = "jiy_guard";

    private AppGuard() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isOn(Context c) {
        return prefs(c).getBoolean("on", false);
    }

    static Set<String> allowed(Context c) {
        return new HashSet<>(prefs(c).getStringSet("allowed", Collections.emptySet()));
    }

    /** json: {"on":true,"allowed":["com.whatsapp", ...],"limits":{"com.google.android.youtube":30}} */
    static void apply(Context c, String json) {
        boolean on = false;
        Set<String> set = new HashSet<>();
        String lim = "{}";
        try {
            JSONObject o = new JSONObject(json);
            on = o.optBoolean("on", false);
            JSONArray a = o.optJSONArray("allowed");
            if (a != null) for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
            JSONObject l = o.optJSONObject("limits");
            if (l != null) lim = l.toString();
        } catch (Exception ignored) {
        }
        prefs(c).edit().putBoolean("on", on).putStringSet("allowed", set).putString("limits", lim).apply();
        synchronized (AppGuard.class) {
            limits = null;
        }
        if ((on || isLocked(c)) && hasUsage(c)) start(c);
        else stop(c);
    }

    /* ---- Komplettsperre (Gerät gesperrt / Durchsage): nur JIY und Telefon/Notruf ---- */
    private static final String[] LOCK_KINDS = {"device", "ann"};

    static void setLock(Context c, String kind, String text, long until) {
        prefs(c).edit().putString("lock_" + kind, text == null ? "" : text).putLong("lockU_" + kind, until).apply();
        if (hasUsage(c)) start(c);
    }

    static void clearLock(Context c, String kind) {
        prefs(c).edit().remove("lock_" + kind).remove("lockU_" + kind).apply();
        if (!isOn(c) && !isLocked(c)) stop(c);
    }

    static boolean isLocked(Context c) {
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        for (String k : LOCK_KINDS) {
            if (p.contains("lock_" + k)) {
                long u = p.getLong("lockU_" + k, 0);
                if (u == 0 || u > now) return true;
            }
        }
        return false;
    }

    /** Apps, die auch bei Komplettsperre gehen: Telefon, Notruf, Systemoberfläche. */
    static boolean lockAllowed(Context c, String pkg) {
        if (pkg == null) return true;
        if (pkg.equals(c.getPackageName())) return true;
        switch (pkg) {
            case "android": case "com.android.systemui": case "com.android.phone": case "com.android.server.telecom":
            case "com.android.emergency": case "com.google.android.dialer": case "com.samsung.android.dialer":
            case "com.android.dialer": case "com.android.incallui": case "com.android.contacts":
            case "com.samsung.android.incallui": case "com.miui.voiceassist":
                return true;
            default:
                return false;
        }
    }

    static void start(Context c) {
        Intent i = new Intent(c, GuardService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
        }
    }

    static void stop(Context c) {
        try {
            c.stopService(new Intent(c, GuardService.class));
        } catch (Exception ignored) {
        }
    }

    static boolean hasUsage(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean hasOverlay(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** Alle Apps mit Symbol auf dem Startbildschirm als JSON [{"p":paket,"l":name}] */
    static String listApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        Set<String> seen = new HashSet<>();
        List<String[]> apps = new ArrayList<>();
        for (ResolveInfo r : list) {
            String p = r.activityInfo.packageName;
            if (p.equals(c.getPackageName()) || !seen.add(p)) continue;
            apps.add(new String[]{p, String.valueOf(r.loadLabel(pm))});
        }
        apps.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        JSONArray out = new JSONArray();
        for (String[] a : apps) {
            try {
                out.put(new JSONObject().put("p", a[0]).put("l", a[1]));
            } catch (Exception ignored) {
            }
        }
        return out.toString();
    }

    // ---------- Tageslimit pro App ----------
    private static Map<String, Integer> limits;
    private static final Map<String, Long> use = new HashMap<>();
    private static String useDay;
    private static long lastFlush;

    static synchronized int limitMin(Context c, String pkg) {
        if (limits == null) {
            limits = new HashMap<>();
            try {
                JSONObject o = new JSONObject(prefs(c).getString("limits", "{}"));
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    limits.put(k, o.optInt(k, 0));
                }
            } catch (Exception ignored) {
            }
        }
        Integer v = limits.get(pkg);
        return v == null ? 0 : v;
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private static void ensureDay(Context c) {
        String t = today();
        if (t.equals(useDay)) return;
        if (useDay != null) flush(c);
        use.clear();
        useDay = t;
        try {
            JSONObject o = new JSONObject(prefs(c).getString("use_" + t, "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                use.put(k, o.optLong(k, 0));
            }
        } catch (Exception ignored) {
        }
    }

    private static void flush(Context c) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Long> e : use.entrySet()) o.put(e.getKey(), e.getValue());
            prefs(c).edit().putString("use_" + useDay, o.toString()).apply();
        } catch (Exception ignored) {
        }
        lastFlush = System.currentTimeMillis();
    }

    static synchronized void addUsage(Context c, String pkg, long ms) {
        ensureDay(c);
        Long old = use.get(pkg);
        use.put(pkg, (old == null ? 0 : old) + ms);
        if (System.currentTimeMillis() - lastFlush > 15000) flush(c);
    }

    static synchronized long usedSec(Context c, String pkg) {
        ensureDay(c);
        Long v = use.get(pkg);
        return v == null ? 0 : v / 1000;
    }

    /** Heutige Nutzung in Minuten als JSON {"day":"20261110","m":{"paket":12}} */
    static synchronized String usageJson(Context c) {
        ensureDay(c);
        try {
            JSONObject m = new JSONObject();
            for (Map.Entry<String, Long> e : use.entrySet()) {
                long min = e.getValue() / 60000;
                if (min > 0) m.put(e.getKey(), min);
            }
            return new JSONObject().put("day", useDay).put("m", m).toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Apps, die immer erlaubt sind: JIY, Startbildschirm, Telefon/Notruf, System-Dialoge. */
    static Set<String> alwaysAllowed(Context c) {
        Set<String> s = new HashSet<>();
        s.add(c.getPackageName());
        s.add("android");
        s.add("com.android.systemui");
        s.add("com.android.phone");
        s.add("com.android.server.telecom");
        s.add("com.android.emergency");
        s.add("com.google.android.dialer");
        s.add("com.samsung.android.dialer");
        s.add("com.android.dialer");
        s.add("com.android.incallui");
        s.add("com.android.permissioncontroller");
        s.add("com.google.android.permissioncontroller");
        s.add("com.android.packageinstaller");
        s.add("com.google.android.packageinstaller");
        s.add("com.teamviewer.quicksupport.market");
        s.add("com.teamviewer.host.market");
        s.add("com.google.android.deskclock");
        s.add("com.android.deskclock");
        s.add("com.sec.android.app.clockpackage");
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : c.getPackageManager().queryIntentActivities(home, 0)) {
                s.add(r.activityInfo.packageName);
            }
        } catch (Exception ignored) {
        }
        try {
            android.telecom.TelecomManager tm = (android.telecom.TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null && tm.getDefaultDialerPackage() != null) s.add(tm.getDefaultDialerPackage());
        } catch (Exception ignored) {
        }
        return s;
    }
}
