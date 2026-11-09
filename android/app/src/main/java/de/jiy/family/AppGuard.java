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

import java.util.ArrayList;
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

    /** json: {"on":true,"allowed":["com.whatsapp", ...]} */
    static void apply(Context c, String json) {
        boolean on = false;
        Set<String> set = new HashSet<>();
        try {
            JSONObject o = new JSONObject(json);
            on = o.optBoolean("on", false);
            JSONArray a = o.optJSONArray("allowed");
            if (a != null) for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
        } catch (Exception ignored) {
        }
        prefs(c).edit().putBoolean("on", on).putStringSet("allowed", set).apply();
        if (on && hasUsage(c)) start(c);
        else stop(c);
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
