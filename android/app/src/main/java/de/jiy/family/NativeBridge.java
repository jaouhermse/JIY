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
    public void notify(String title, String body, String tag) {
        Notifier.show(ctx, tag == null ? 0 : tag.hashCode(), title, body);
    }

    @JavascriptInterface
    public void schedule(String json) {
        Scheduler.replaceAll(ctx, json);
    }
}
