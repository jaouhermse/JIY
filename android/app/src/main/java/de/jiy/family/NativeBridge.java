package de.jiy.family;

import android.content.Context;
import android.webkit.JavascriptInterface;

/** Verbindung zwischen der Web-App (JavaScript) und Android: window.JIYNative */
public class NativeBridge {

    private final Context ctx;

    NativeBridge(Context c) {
        ctx = c.getApplicationContext();
    }

    @JavascriptInterface
    public boolean available() {
        return true;
    }

    @JavascriptInterface
    public int appVersion() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    @JavascriptInterface
    public String deviceName() {
        String man = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER;
        String model = android.os.Build.MODEL == null ? "" : android.os.Build.MODEL;
        if (!man.isEmpty()) man = Character.toUpperCase(man.charAt(0)) + man.substring(1);
        return model.toLowerCase().startsWith(man.toLowerCase()) ? model : (man + " " + model).trim();
    }

    @JavascriptInterface
    public void notify(String title, String body, String tag) {
        Notifier.show(ctx, tag == null ? 0 : tag.hashCode(), title, body);
    }

    @JavascriptInterface
    public String listApps() {
        return AppGuard.listApps(ctx);
    }

    @JavascriptInterface
    public void setAppGuard(String json) {
        AppGuard.apply(ctx, json);
    }

    @JavascriptInterface
    public boolean saveAdhan(String base64) {
        try {
            byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            java.io.FileOutputStream out = new java.io.FileOutputStream(SoundService.adhanFile(ctx));
            out.write(data);
            out.close();
            return data.length > 1000;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean hasAdhan() {
        return SoundService.adhanFile(ctx).exists();
    }

    @JavascriptInterface
    public void adhanTest() {
        SoundService.start(ctx, "adhan", "🕌 Adhan (Test)", "Tippe auf Stopp zum Beenden");
    }

    @JavascriptInterface
    public void adhanStop() {
        SoundService.stop(ctx);
    }

    @JavascriptInterface
    public void ringTest() {
        SoundService.start(ctx, "ring", "⏰ Wecker (Test)", "Tippe auf Stopp zum Beenden");
    }

    @JavascriptInterface
    public boolean launchApp(String pkg) {
        try {
            android.content.Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg));
            }
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public void uninstallApp(String pkg) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:" + pkg));
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public String appUsage() {
        return AppGuard.usageJson(ctx);
    }

    @JavascriptInterface
    public String guardPerms() {
        return "{\"usage\":" + AppGuard.hasUsage(ctx) + ",\"overlay\":" + AppGuard.hasOverlay(ctx) + ",\"on\":" + AppGuard.isOn(ctx) + "}";
    }

    @JavascriptInterface
    public void openUsageSettings() {
        open(new android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    @JavascriptInterface
    public void openOverlaySettings() {
        open(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + ctx.getPackageName())));
    }

    private void open(android.content.Intent i) {
        try {
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                ctx.startActivity(new android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    @JavascriptInterface
    public String installUpdate(String url) {
        return Updater.start(ctx, url);
    }

    @JavascriptInterface
    public void schedule(String json) {
        Scheduler.replaceAll(ctx, json);
    }
}
