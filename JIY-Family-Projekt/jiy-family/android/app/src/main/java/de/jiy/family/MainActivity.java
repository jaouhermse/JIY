package de.jiy.family;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * JIY Family – Android-Hülle.
 * Lädt die Web-App von GitHub Pages. Änderungen an web/index.html
 * erscheinen dadurch automatisch in der App, ohne neue APK.
 */
public class MainActivity extends Activity {

    private static final int FILE_REQUEST = 42;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String appHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getString(R.string.app_url);
        String h = Uri.parse(url).getHost();
        appHost = h == null ? "" : h.toLowerCase();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " JIYFamilyApp");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openOutside(request.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
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
