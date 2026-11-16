package de.jiy.family;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimaler HTTP/1.0-Client NUR fürs Heimnetz (FRITZ!Box, Fernseher).
 * Bewusst über einen einfachen Socket, damit die App sonst keinen unverschlüsselten
 * Datenverkehr erlaubt (usesCleartextTraffic bleibt aus).
 */
final class LanHttp {

    static final class Res {
        int status;
        Map<String, String> headers = new LinkedHashMap<>();
        String body = "";
    }

    private LanHttp() {
    }

    static Res request(String host, int port, String method, String path,
                       Map<String, String> headers, String body, int timeoutMs) throws Exception {
        byte[] data = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        StringBuilder req = new StringBuilder();
        req.append(method).append(' ').append(path).append(" HTTP/1.0\r\n");
        req.append("Host: ").append(host).append(':').append(port).append("\r\n");
        req.append("Connection: close\r\n");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) {
            req.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        if (body != null) req.append("Content-Length: ").append(data.length).append("\r\n");
        req.append("\r\n");
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs * 3);
            OutputStream os = s.getOutputStream();
            os.write(req.toString().getBytes(StandardCharsets.UTF_8));
            os.write(data);
            os.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
                if (buf.size() > 4 * 1024 * 1024) break;
            }
            String all = buf.toString("UTF-8");
            Res r = new Res();
            int sep = all.indexOf("\r\n\r\n");
            String head = sep < 0 ? all : all.substring(0, sep);
            r.body = sep < 0 ? "" : all.substring(sep + 4);
            String[] lines = head.split("\r\n");
            if (lines.length > 0) {
                String[] p = lines[0].split(" ");
                if (p.length > 1) try { r.status = Integer.parseInt(p[1]); } catch (NumberFormatException ignored) { }
            }
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c > 0) r.headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
            }
            return r;
        }
    }

    /** Prüft, ob ein TCP-Port erreichbar ist. */
    static boolean open(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
