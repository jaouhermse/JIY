package de.jiy.family;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heimnetz: Geräte suchen und FRITZ!Box steuern (TR-064, offizielle AVM-Schnittstelle).
 * Funktioniert nur im eigenen WLAN. Zugangsdaten liegen verschlüsselt nur auf diesem Handy.
 */
final class HomeNet {

    static final ExecutorService EX = Executors.newFixedThreadPool(3);
    private static final String PREFS = "jiy_homenet";

    private HomeNet() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String err(Exception e) {
        JSONObject o = new JSONObject();
        try {
            o.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
        } catch (Exception ignored) { }
        return o.toString();
    }

    /* ======================= Netz-Infos ======================= */

    /** @return {ip, prefix, gateway, wifi} oder null */
    static JSONObject netInfo(Context c) throws Exception {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        if (n == null) return null;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        boolean wifi = caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        LinkProperties lp = cm.getLinkProperties(n);
        if (lp == null) return null;
        JSONObject o = new JSONObject();
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (la.getAddress() instanceof Inet4Address) {
                o.put("ip", la.getAddress().getHostAddress());
                o.put("prefix", la.getPrefixLength());
            }
        }
        for (RouteInfo r : lp.getRoutes()) {
            if (r.isDefaultRoute() && r.getGateway() instanceof Inet4Address) {
                o.put("gateway", r.getGateway().getHostAddress());
            }
        }
        o.put("wifi", wifi);
        return o;
    }

    /* ======================= Gerätesuche ======================= */

    private static final int[] PORTS = {80, 443, 49000, 8001, 8002, 8008, 8009, 9100, 631, 445, 5000, 5001, 22, 554, 1883, 62078};

    static String scan(Context c) throws Exception {
        JSONObject ni = netInfo(c);
        if (ni == null || !ni.optBoolean("wifi")) throw new Exception("Nicht im WLAN – bitte mit dem Heim-WLAN verbinden.");
        String own = ni.getString("ip");
        String[] p = own.split("\\.");
        final String base = p[0] + "." + p[1] + "." + p[2] + ".";
        ExecutorService pool = Executors.newFixedThreadPool(48);
        List<Future<JSONObject>> fs = new ArrayList<>();
        for (int i = 1; i < 255; i++) {
            final String ip = base + i;
            fs.add(pool.submit(() -> probe(ip, ip.equals(own))));
        }
        JSONArray devs = new JSONArray();
        for (Future<JSONObject> f : fs) {
            try {
                JSONObject d = f.get(20, TimeUnit.SECONDS);
                if (d != null) devs.put(d);
            } catch (Exception ignored) { }
        }
        pool.shutdownNow();
        JSONObject out = new JSONObject();
        out.put("net", ni);
        out.put("devices", devs);
        return out.toString();
    }

    private static JSONObject probe(String ip, boolean self) {
        try {
            List<Integer> open = new ArrayList<>();
            for (int port : PORTS) if (LanHttp.open(ip, port, 220)) open.add(port);
            boolean alive = !open.isEmpty() || self;
            if (!alive) {
                try {
                    alive = InetAddress.getByName(ip).isReachable(350);
                } catch (Exception ignored) { }
            }
            if (!alive) return null;
            JSONObject d = new JSONObject();
            d.put("ip", ip);
            d.put("ports", new JSONArray(open));
            String kind = "Gerät", icon = "🧩";
            if (self) { kind = "Dieses Handy"; icon = "📱"; }
            else if (open.contains(49000)) { kind = "FRITZ!Box / AVM"; icon = "🌐"; }
            else if (open.contains(8001) || open.contains(8002)) { kind = "Samsung-Fernseher"; icon = "📺"; }
            else if (open.contains(8008) || open.contains(8009)) { kind = "Chromecast / Google"; icon = "📺"; }
            else if (open.contains(9100) || open.contains(631)) { kind = "Drucker"; icon = "🖨️"; }
            else if (open.contains(5000) || open.contains(5001)) { kind = "NAS"; icon = "💾"; }
            else if (open.contains(62078)) { kind = "iPhone / iPad"; icon = "📱"; }
            else if (open.contains(445)) { kind = "PC / NAS"; icon = "💻"; }
            else if (open.contains(554)) { kind = "Kamera"; icon = "📷"; }
            else if (open.contains(1883)) { kind = "Smart Home"; icon = "💡"; }
            else if (open.contains(22)) { kind = "Server / NAS"; icon = "🖥️"; }
            d.put("kind", kind);
            d.put("icon", icon);
            String name = "";
            if (open.contains(8001)) {
                try {
                    LanHttp.Res r = LanHttp.request(ip, 8001, "GET", "/api/v2/", null, null, 1500);
                    JSONObject dev = new JSONObject(r.body).optJSONObject("device");
                    if (dev != null) {
                        name = dev.optString("name", "");
                        d.put("model", dev.optString("modelName", ""));
                        d.put("mac", dev.optString("wifiMac", ""));
                    }
                } catch (Exception ignored) { }
            }
            if (name.isEmpty()) {
                try {
                    String h = InetAddress.getByName(ip).getCanonicalHostName();
                    if (h != null && !h.equals(ip)) name = h.replace(".fritz.box", "");
                } catch (Exception ignored) { }
            }
            d.put("name", name);
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /* ======================= FRITZ!Box (TR-064) ======================= */

    static void fritzSave(Context c, String host, String user, String pw) throws Exception {
        SharedPreferences.Editor e = prefs(c).edit();
        e.putString("fritz_host", host == null || host.trim().isEmpty() ? "fritz.box" : host.trim());
        e.putString("fritz_user", user == null ? "" : user.trim());
        if (pw != null && !pw.isEmpty()) e.putString("fritz_pw", Secure.enc(pw));
        e.apply();
    }

    static boolean fritzHas(Context c) {
        return prefs(c).contains("fritz_pw");
    }

    static void fritzForget(Context c) {
        prefs(c).edit().remove("fritz_pw").remove("fritz_user").remove("fritz_guest").apply();
    }

    private static String md5(String s) throws Exception {
        byte[] h = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte x : h) b.append(String.format(Locale.ROOT, "%02x", x));
        return b.toString();
    }

    private static String attr(String hdr, String name) {
        Matcher m = Pattern.compile(name + "=\"?([^\",]*)\"?").matcher(hdr);
        return m.find() ? m.group(1) : "";
    }

    /** SOAP-Aufruf mit Digest-Anmeldung. @return Ausgabewerte (z. B. NewEnable → 1) */
    static Map<String, String> soap(Context c, String control, String service, String action, Map<String, String> args) throws Exception {
        SharedPreferences p = prefs(c);
        String host = p.getString("fritz_host", "fritz.box");
        String user = p.getString("fritz_user", "");
        String pw = Secure.dec(p.getString("fritz_pw", ""));
        StringBuilder a = new StringBuilder();
        if (args != null) for (Map.Entry<String, String> e : args.entrySet()) {
            a.append('<').append(e.getKey()).append('>').append(xmlEsc(e.getValue())).append("</").append(e.getKey()).append('>');
        }
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
                + "<u:" + action + " xmlns:u=\"" + service + "\">" + a + "</u:" + action + "></s:Body></s:Envelope>";
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "text/xml; charset=\"utf-8\"");
        h.put("SOAPAction", service + "#" + action);
        LanHttp.Res r = LanHttp.request(host, 49000, "POST", control, h, body, 4000);
        if (r.status == 401) {
            String www = r.headers.get("www-authenticate");
            if (www == null) throw new Exception("FRITZ!Box verlangt Anmeldung");
            String realm = attr(www, "realm"), nonce = attr(www, "nonce");
            String cnonce = Long.toHexString(new SecureRandom().nextLong()), nc = "00000001";
            String ha1 = md5(user + ":" + realm + ":" + pw), ha2 = md5("POST:" + control);
            String resp = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2);
            h.put("Authorization", "Digest username=\"" + user + "\", realm=\"" + realm + "\", nonce=\"" + nonce + "\", uri=\"" + control
                    + "\", response=\"" + resp + "\", qop=auth, nc=" + nc + ", cnonce=\"" + cnonce + "\", algorithm=MD5");
            r = LanHttp.request(host, 49000, "POST", control, h, body, 4000);
        }
        if (r.status == 401) throw new Exception("FRITZ!Box: Benutzer oder Passwort falsch");
        if (r.status >= 400) {
            String fault = between(r.body, "errorDescription");
            throw new Exception("FRITZ!Box-Fehler " + r.status + (fault.isEmpty() ? "" : ": " + fault));
        }
        Map<String, String> out = new HashMap<>();
        Matcher m = Pattern.compile("<(New[\\w-]+)>([^<]*)</\\1>").matcher(r.body);
        while (m.find()) out.put(m.group(1), xmlUnesc(m.group(2)));
        return out;
    }

    private static String between(String s, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">([^<]*)</" + tag + ">").matcher(s == null ? "" : s);
        return m.find() ? m.group(1) : "";
    }

    private static String xmlEsc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String xmlUnesc(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    /** Gäste-WLAN = WLANConfiguration mit der höchsten Nummer (2 bei 2,4 GHz, 3 bei Dualband, 4 bei Triband). */
    private static int guestIndex(Context c) throws Exception {
        int saved = prefs(c).getInt("fritz_guest", 0);
        if (saved > 0) return saved;
        String host = prefs(c).getString("fritz_host", "fritz.box");
        LanHttp.Res r = LanHttp.request(host, 49000, "GET", "/tr64desc.xml", null, null, 4000);
        Matcher m = Pattern.compile("WLANConfiguration:(\\d)").matcher(r.body);
        int max = 0;
        while (m.find()) max = Math.max(max, Integer.parseInt(m.group(1)));
        if (max < 2) throw new Exception("Gäste-WLAN nicht gefunden");
        prefs(c).edit().putInt("fritz_guest", max).apply();
        return max;
    }

    private static final String HOSTS = "urn:dslforum-org:service:Hosts:1";

    static String fritz(Context c, String what, String arg) throws Exception {
        JSONObject o = new JSONObject();
        switch (what) {
            case "info": {
                Map<String, String> d = soap(c, "/upnp/control/deviceinfo", "urn:dslforum-org:service:DeviceInfo:1", "GetInfo", null);
                o.put("model", d.get("NewModelName"));
                o.put("version", d.get("NewSoftwareVersion"));
                o.put("uptime", d.get("NewUpTime"));
                try {
                    Map<String, String> l = soap(c, "/upnp/control/wancommonifconfig1", "urn:dslforum-org:service:WANCommonInterfaceConfig:1", "GetCommonLinkProperties", null);
                    o.put("link", l.get("NewPhysicalLinkStatus"));
                    o.put("down", l.get("NewLayer1DownstreamMaxBitRate"));
                    o.put("up", l.get("NewLayer1UpstreamMaxBitRate"));
                } catch (Exception ignored) { }
                try {
                    int g = guestIndex(c);
                    Map<String, String> w = soap(c, "/upnp/control/wlanconfig" + g, "urn:dslforum-org:service:WLANConfiguration:" + g, "GetInfo", null);
                    o.put("guest", "1".equals(w.get("NewEnable")));
                    o.put("guestSsid", w.get("NewSSID"));
                } catch (Exception ignored) { }
                break;
            }
            case "hosts": {
                Map<String, String> pth = soap(c, "/upnp/control/hosts", HOSTS, "X_AVM-DE_GetHostListPath", null);
                String path = pth.get("NewX_AVM-DE_HostListPath");
                JSONArray arr = new JSONArray();
                if (path != null && !path.isEmpty()) {
                    String host = prefs(c).getString("fritz_host", "fritz.box");
                    LanHttp.Res r = LanHttp.request(host, 49000, "GET", path, null, null, 5000);
                    Matcher m = Pattern.compile("<Item>(.*?)</Item>", Pattern.DOTALL).matcher(r.body);
                    while (m.find()) {
                        String it = m.group(1);
                        JSONObject h = new JSONObject();
                        h.put("ip", between(it, "IPAddress"));
                        h.put("mac", between(it, "MACAddress"));
                        h.put("name", xmlUnesc(between(it, "HostName")));
                        h.put("active", "1".equals(between(it, "Active")));
                        h.put("type", between(it, "InterfaceType"));
                        arr.put(h);
                    }
                } else {
                    Map<String, String> cnt = soap(c, "/upnp/control/hosts", HOSTS, "GetHostNumberOfEntries", null);
                    int n = Integer.parseInt(cnt.getOrDefault("NewHostNumberOfEntries", "0"));
                    for (int i = 0; i < Math.min(n, 200); i++) {
                        Map<String, String> args = new LinkedHashMap<>();
                        args.put("NewIndex", String.valueOf(i));
                        Map<String, String> e = soap(c, "/upnp/control/hosts", HOSTS, "GetGenericHostEntry", args);
                        JSONObject h = new JSONObject();
                        h.put("ip", e.get("NewIPAddress"));
                        h.put("mac", e.get("NewMACAddress"));
                        h.put("name", e.get("NewHostName"));
                        h.put("active", "1".equals(e.get("NewActive")));
                        h.put("type", e.get("NewInterfaceType"));
                        arr.put(h);
                    }
                }
                o.put("hosts", arr);
                break;
            }
            case "guest": {
                int g = guestIndex(c);
                Map<String, String> args = new LinkedHashMap<>();
                args.put("NewEnable", "1".equals(arg) ? "1" : "0");
                soap(c, "/upnp/control/wlanconfig" + g, "urn:dslforum-org:service:WLANConfiguration:" + g, "SetEnable", args);
                o.put("guest", "1".equals(arg));
                break;
            }
            case "block": {
                // arg = "IP|1" sperren, "IP|0" freigeben (Internetzugang dieses Geräts)
                String[] a = arg == null ? new String[0] : arg.split("\\|");
                if (a.length < 2) throw new Exception("Gerät fehlt");
                Map<String, String> args = new LinkedHashMap<>();
                args.put("NewIPv4Address", a[0]);
                args.put("NewDisallow", "1".equals(a[1]) ? "1" : "0");
                soap(c, "/upnp/control/x_hostfilter", "urn:dslforum-org:service:X_AVM-DE_HostFilter:1", "DisallowWANAccessByIP", args);
                o.put("blocked", "1".equals(a[1]));
                break;
            }
            case "reboot": {
                soap(c, "/upnp/control/deviceconfig", "urn:dslforum-org:service:DeviceConfig:1", "Reboot", null);
                o.put("reboot", true);
                break;
            }
            default:
                throw new Exception("Unbekannte Aktion");
        }
        o.put("ok", true);
        return o.toString();
    }
}
