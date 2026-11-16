package de.jiy.family;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Fernbedienung für Samsung-Fernseher (Tizen, ab ca. 2018) über die lokale Schnittstelle (wss, Port 8002).
 * Beim ersten Verbinden fragt der Fernseher einmal „Zulassen?“ – danach wird ein Schlüssel (Token) gespeichert.
 */
final class SamsungTv {

    private static WebSocket ws;
    private static boolean connected = false;
    private static final List<String> pending = new ArrayList<>();
    private static OkHttpClient client;

    private SamsungTv() {
    }

    private static SharedPreferences prefs(Context c) {
        return HomeNet.prefs(c);
    }

    static void setTv(Context c, String ip, String mac) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (!ip.equals(prefs(c).getString("tv_ip", ""))) e.remove("tv_token");
        e.putString("tv_ip", ip);
        if (mac != null && !mac.isEmpty()) e.putString("tv_mac", mac);
        e.apply();
        close();
    }

    static String tvIp(Context c) {
        return prefs(c).getString("tv_ip", "");
    }

    /** Der Fernseher hat ein selbst ausgestelltes Zertifikat – nur für diese eine Verbindung im Heimnetz akzeptieren. */
    private static synchronized OkHttpClient http() throws Exception {
        if (client != null) return client;
        X509TrustManager tm = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{tm}, new SecureRandom());
        HostnameVerifier any = (h, s) -> true;
        client = new OkHttpClient.Builder()
                .sslSocketFactory(ssl.getSocketFactory(), tm)
                .hostnameVerifier(any)
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
        return client;
    }

    static synchronized void close() {
        if (ws != null) try { ws.close(1000, "bye"); } catch (Exception ignored) { }
        ws = null;
        connected = false;
        pending.clear();
    }

    private static synchronized void connect(final Context c) throws Exception {
        if (ws != null) return;
        String ip = tvIp(c);
        if (ip.isEmpty()) throw new Exception("Kein Fernseher ausgewählt");
        String name = Base64.encodeToString("JIY Family".getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String token = prefs(c).getString("tv_token", "");
        String url = "wss://" + ip + ":8002/api/v2/channels/samsung.remote.control?name=" + name + (token.isEmpty() ? "" : "&token=" + token);
        final Context app = c.getApplicationContext();
        ws = http().newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            @Override
            public void onMessage(WebSocket socket, String text) {
                try {
                    JSONObject m = new JSONObject(text);
                    String ev = m.optString("event");
                    if ("ms.channel.connect".equals(ev)) {
                        JSONObject d = m.optJSONObject("data");
                        if (d != null && d.has("token")) prefs(app).edit().putString("tv_token", d.getString("token")).apply();
                        synchronized (SamsungTv.class) {
                            connected = true;
                            for (String p : pending) socket.send(p);
                            pending.clear();
                        }
                        MainActivity.callback("tv", "{\"event\":\"connected\"}");
                    } else if ("ms.channel.unauthorized".equals(ev)) {
                        MainActivity.callback("tv", "{\"event\":\"denied\"}");
                    }
                } catch (Exception ignored) { }
            }

            @Override
            public void onFailure(WebSocket socket, Throwable t, Response r) {
                synchronized (SamsungTv.class) { ws = null; connected = false; pending.clear(); }
                MainActivity.callback("tv", "{\"event\":\"error\",\"msg\":" + JSONObject.quote(String.valueOf(t.getMessage())) + "}");
            }

            @Override
            public void onClosed(WebSocket socket, int code, String reason) {
                synchronized (SamsungTv.class) { ws = null; connected = false; }
            }
        });
        MainActivity.callback("tv", "{\"event\":\"connecting\",\"paired\":" + !token.isEmpty() + "}");
    }

    private static synchronized void send(Context c, String json) throws Exception {
        connect(c);
        if (connected && ws != null) ws.send(json);
        else if (pending.size() < 20) pending.add(json);
    }

    /** Taste senden, z. B. KEY_VOLUP, KEY_POWER, KEY_HOME */
    static void key(Context c, String key) throws Exception {
        JSONObject p = new JSONObject();
        p.put("Cmd", "Click");
        p.put("DataOfCmd", key);
        p.put("Option", "false");
        p.put("TypeOfRemote", "SendRemoteKey");
        JSONObject o = new JSONObject();
        o.put("method", "ms.remote.control");
        o.put("params", p);
        send(c, o.toString());
    }

    /** App starten (Netflix 3201907018807, YouTube 111299001912, Prime Video 3201910019365, Disney+ 3201901017640) */
    static void app(Context c, String appId) throws Exception {
        JSONObject d = new JSONObject();
        d.put("appId", appId);
        d.put("action_type", "DEEP_LINK");
        JSONObject p = new JSONObject();
        p.put("event", "ed.apps.launch");
        p.put("to", "host");
        p.put("data", d);
        JSONObject o = new JSONObject();
        o.put("method", "ms.channel.emit");
        o.put("params", p);
        send(c, o.toString());
    }

    /** Einschalten per Wake-on-LAN (Fernseher: „Mit Mobilgerät einschalten“ aktivieren). */
    static String wake(Context c) throws Exception {
        String mac = prefs(c).getString("tv_mac", "");
        if (mac.isEmpty()) throw new Exception("MAC-Adresse unbekannt – Fernseher einmal eingeschaltet suchen");
        String[] h = mac.split("[:-]");
        if (h.length != 6) throw new Exception("MAC-Adresse ungültig");
        byte[] m = new byte[6];
        for (int i = 0; i < 6; i++) m[i] = (byte) Integer.parseInt(h[i], 16);
        byte[] pkt = new byte[6 + 16 * 6];
        for (int i = 0; i < 6; i++) pkt[i] = (byte) 0xff;
        for (int i = 6; i < pkt.length; i += 6) System.arraycopy(m, 0, pkt, i, 6);
        String ip = tvIp(c);
        String bc = ip.substring(0, ip.lastIndexOf('.')) + ".255";
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            for (int port : new int[]{9, 7}) s.send(new DatagramPacket(pkt, pkt.length, InetAddress.getByName(bc), port));
        }
        return "{\"ok\":true}";
    }

    static String info(Context c) throws Exception {
        String ip = tvIp(c);
        JSONObject o = new JSONObject();
        o.put("ip", ip);
        o.put("paired", !prefs(c).getString("tv_token", "").isEmpty());
        o.put("mac", prefs(c).getString("tv_mac", ""));
        if (!ip.isEmpty()) {
            try {
                LanHttp.Res r = LanHttp.request(ip, 8001, "GET", "/api/v2/", null, null, 1500);
                JSONObject dev = new JSONObject(r.body).optJSONObject("device");
                if (dev != null) {
                    o.put("name", dev.optString("name"));
                    o.put("model", dev.optString("modelName"));
                    o.put("on", "on".equalsIgnoreCase(dev.optString("PowerState", "on")));
                    String mac = dev.optString("wifiMac", "");
                    if (!mac.isEmpty()) prefs(c).edit().putString("tv_mac", mac).apply();
                }
            } catch (Exception e) {
                o.put("on", false);
            }
        }
        return o.toString();
    }
}
