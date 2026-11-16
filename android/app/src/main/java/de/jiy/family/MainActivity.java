package de.jiy.family;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * JIY Family – Android-App.
 * Die komplette Oberfläche (Ordner web/) steckt IN der APK (assets/web).
 * Anfragen an die bisherige Adresse werden aus der APK beantwortet – dadurch
 * bleiben Anmeldung und gespeicherte Daten erhalten und die App läuft offline.
 * Nur Dateien, die nicht in der APK sind (z. B. Kalender-Abo), kommen aus dem Netz.
 */
public class MainActivity extends Activity {

    private static final int FILE_REQUEST = 42;
    private static final int LOCATION_REQUEST = 43;
    private static final int NOTIF_REQUEST = 44;
    private static final int CAMERA_REQUEST = 45;
    private PermissionRequest camRequest;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String appHost;
    private String appBase = "/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getString(R.string.app_url);
        String h = Uri.parse(url).getHost();
        appHost = h == null ? "" : h.toLowerCase();
        String bp = Uri.parse(url).getPath();
        appBase = (bp == null || bp.isEmpty()) ? "/" : (bp.endsWith("/") ? bp : bp + "/");
        Speaker.init(this);

        web = new WebView(this);
        setContentView(web);
        web.addJavascriptInterface(new NativeBridge(this), "JIYNative");
        Notifier.ensureChannel(this);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_REQUEST);
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setGeolocationEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " JIYFamilyApp");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return fromApk(request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openOutside(request.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                } else {
                    geoOrigin = origin;
                    geoCallback = callback;
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // Kamera für den QR-Code-Login
                boolean wantsCam = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCam = true;
                }
                if (!wantsCam) {
                    request.deny();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    camRequest = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;

                List<String> types = new ArrayList<>();
                String[] accept = params.getAcceptTypes();
                if (accept != null) {
                    for (String a : accept) {
                        if (a == null) continue;
                        for (String part : a.split(",")) {
                            String t = part.trim();
                            if (!t.isEmpty()) types.add(t);
                        }
                    }
                }

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                if (types.size() == 1) {
                    intent.setType(types.get(0));
                } else {
                    intent.setType("*/*");
                    if (!types.isEmpty()) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
                    }
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "Datei wählen"), FILE_REQUEST);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(url);
        }
    }

    /** Liefert Dateien der App aus der APK (assets/web/…) statt aus dem Internet. */
    private WebResourceResponse fromApk(WebResourceRequest request) {
        try {
            if (!"GET".equalsIgnoreCase(request.getMethod())) return null;
            Uri u = request.getUrl();
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            if (!host.equals(appHost)) return null;
            String path = u.getPath() == null ? "/" : u.getPath();
            if (!path.startsWith(appBase)) {
                if ((path + "/").equals(appBase)) path = appBase; else return null;
            }
            String rel = path.substring(appBase.length());
            if (rel.isEmpty() || rel.endsWith("/")) rel = rel + "index.html";
            if (rel.contains("..")) return null;
            java.io.InputStream in;
            try {
                in = getAssets().open("web/" + rel);
            } catch (java.io.IOException notInApk) {
                return null; // nicht in der APK → normal aus dem Netz laden
            }
            WebResourceResponse r = new WebResourceResponse(mime(rel), "UTF-8", in);
            java.util.Map<String, String> hd = new java.util.HashMap<>();
            hd.put("Cache-Control", "no-cache");
            hd.put("Access-Control-Allow-Origin", "*");
            r.setResponseHeaders(hd);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static String mime(String f) {
        String n = f.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "application/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".webmanifest")) return "application/manifest+json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".gltf")) return "model/gltf+json";
        if (n.endsWith(".glb")) return "model/gltf-binary";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".ics")) return "text/calendar";
        return "application/octet-stream";
    }

    /** Eigene Seiten und Firebase bleiben in der App, alles andere (Google Kalender, Outlook, Jitsi) öffnet extern. */
    private boolean openOutside(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean isWeb = "https".equals(scheme) || "http".equals(scheme);
        if (isWeb && (host.equals(appHost)
                || host.endsWith(".firebaseapp.com")
                || host.endsWith(".web.app"))) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Link kann nicht geöffnet werden", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST && geoCallback != null) {
            boolean granted = false;
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_GRANTED) granted = true;
            }
            geoCallback.invoke(geoOrigin, granted, false);
            geoCallback = null;
        }
        if (requestCode == CAMERA_REQUEST && camRequest != null) {
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) camRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else camRequest.deny();
            camRequest = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (AppGuard.isLocked(this)) return; // gesperrt: Zurück-Taste wirkungslos
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }
}
