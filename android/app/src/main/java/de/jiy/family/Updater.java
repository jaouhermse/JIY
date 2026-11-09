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
    static String start(Context c, String url) {
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
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "Update konnte nicht gestartet werden", Toast.LENGTH_LONG).show();
        }
    }
}
