package de.jiy.family;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;

/** Lädt die neueste APK herunter und startet die Installation (Update in der App). */
public final class Updater {

    private static long downloadId = -1;
    private static BroadcastReceiver receiver;

    private Updater() {
    }

    /** @return "ok" = Download läuft, "perm" = Erlaubnis „Unbekannte Apps installieren“ fehlt, "error" */
    /** Nur Updates aus dem eigenen GitHub-Repository (Releases) sind erlaubt. */
    static boolean allowedUrl(String url) {
        try {
            Uri u = Uri.parse(url);
            if (!"https".equals(u.getScheme())) return false;
            String h = u.getHost() == null ? "" : u.getHost().toLowerCase(java.util.Locale.ROOT);
            String p = u.getPath() == null ? "" : u.getPath();
            if (h.equals("github.com")) return p.toLowerCase(java.util.Locale.ROOT).startsWith("/jaouhermse/jiy/releases/download/");
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    static String start(Context c, String url) {
        if (!allowedUrl(url)) return "error";
        try {
            if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return "perm";
            }
            File dir = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null) {
                File old = new File(dir, "JIY-Family.apk");
                if (old.exists()) old.delete();
            }
            DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle("JIY Family Update");
            r.setDescription("Neue Version wird geladen …");
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            r.setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, "JIY-Family.apk");
            listen(c.getApplicationContext());
            downloadId = dm.enqueue(r);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    private static void listen(Context app) {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2);
                if (id != downloadId) return;
                install(context, id);
            }
        };
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else app.registerReceiver(receiver, f);
    }

    private static void install(Context c, long id) {
        try {
            DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id));
            boolean ok = false;
            if (cur != null) {
                if (cur.moveToFirst()) {
                    int col = cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    ok = col >= 0 && cur.getInt(col) == DownloadManager.STATUS_SUCCESSFUL;
                }
                cur.close();
            }
            if (!ok) {
                Toast.makeText(c, "Update-Download fehlgeschlagen", Toast.LENGTH_LONG).show();
                return;
            }
            Uri uri = dm.getUriForDownloadedFile(id);
            // Sicherheitsprüfung: gleiche App (Paketname) und gleicher Signierschlüssel wie die installierte Version
            File apk = new File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "JIY-Family.apk");
            if (!sameSigner(c, apk)) {
                if (apk.exists()) apk.delete();
                Toast.makeText(c, "Update abgelehnt: Datei ist nicht von JIY signiert", Toast.LENGTH_LONG).show();
                return;
            }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "Update konnte nicht gestartet werden", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean sameSigner(Context c, File apk) {
        try {
            if (!apk.exists()) return false;
            android.content.pm.PackageManager pm = c.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28 ? android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES : android.content.pm.PackageManager.GET_SIGNATURES;
            android.content.pm.PackageInfo neu = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (neu == null || !c.getPackageName().equals(neu.packageName)) return false;
            android.content.pm.PackageInfo alt = pm.getPackageInfo(c.getPackageName(), flags);
            return java.util.Arrays.equals(certs(alt), certs(neu));
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static byte[][] certs(android.content.pm.PackageInfo pi) {
        android.content.pm.Signature[] sig;
        if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
            sig = pi.signingInfo.hasMultipleSigners() ? pi.signingInfo.getApkContentsSigners() : pi.signingInfo.getSigningCertificateHistory();
        } else {
            sig = pi.signatures;
        }
        if (sig == null || sig.length == 0) return new byte[0][];
        byte[][] out = new byte[sig.length][];
        for (int i = 0; i < sig.length; i++) out[i] = sig[i].toByteArray();
        java.util.Arrays.sort(out, (a, b) -> {
            int n = Math.min(a.length, b.length);
            for (int i = 0; i < n; i++) if (a[i] != b[i]) return a[i] - b[i];
            return a.length - b.length;
        });
        return out;
    }
}
