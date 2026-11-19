package dev.maelab.haframe;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Short-lived, Wi-Fi-only configuration endpoint. Never enabled by default. */
final class ConfigWebServer {
    static final String ACTION_CONFIG_UPDATED = "dev.maelab.haframe.CONFIG_UPDATED";
    static final int PORT = 8765;
    private static final long DURATION_MS = 15 * 60 * 1000L;
    private static final int MAX_BODY = 32 * 1024;
    private static final String TAG = "HAFrameConfig";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static ConfigWebServer current;

    private final Context context;
    private final String ip;
    private final String pin;
    private final String session;
    private final String origin;
    private final long deadline;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private ServerSocket listener;
    private Thread acceptThread;
    private volatile boolean stopped;
    private int failedLogins = 0;
    private boolean pinUsed = false;

    private ConfigWebServer(Context context, Inet4Address address) {
        this.context = context.getApplicationContext();
        this.ip = address.getHostAddress();
        this.origin = "http://" + ip + ":" + PORT;
        this.deadline = SystemClock.elapsedRealtime() + DURATION_MS;
        this.pin = String.format(Locale.US, "%06d", RANDOM.nextInt(1000000));
        byte[] secret = new byte[24];
        RANDOM.nextBytes(secret);
        StringBuilder hex = new StringBuilder();
        for (byte b : secret) hex.append(String.format(Locale.US, "%02x", b & 255));
        this.session = hex.toString();
    }

    static synchronized String start(Context context) throws Exception {
        if (current != null) current.shutdown();
        current = null;
        Inet4Address wifi = wifiAddress();
        if (wifi == null) throw new IllegalStateException("Connecter le Wi-Fi du cadre d'abord");
        ConfigWebServer instance = new ConfigWebServer(context, wifi);
        instance.listener = new ServerSocket(PORT, 8, wifi);
        instance.listener.setSoTimeout(1000);
        current = instance;
        instance.acceptThread = new Thread(instance::acceptLoop, "haframe-config-listener");
        instance.acceptThread.setDaemon(true);
        instance.acceptThread.start();
        return instance.url();
    }

    static synchronized void stop() {
        if (current != null) current.shutdown();
        current = null;
    }

    static synchronized ConfigWebServer active() {
        if (current != null && (current.stopped || current.remainingSeconds() == 0)) {
            current.shutdown();
            current = null;
        }
        return current;
    }

    String url() { return origin; }
    String pin() { return pin; }
    long remainingSeconds() {
        return Math.max(0, (deadline - SystemClock.elapsedRealtime() + 999) / 1000);
    }

    private static Inet4Address wifiAddress() throws Exception {
        NetworkInterface wifi = NetworkInterface.getByName("wlan0");
        if (wifi == null || !wifi.isUp()) return null;
        Enumeration<InetAddress> addresses = wifi.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress candidate = addresses.nextElement();
            if (candidate instanceof Inet4Address && !candidate.isLoopbackAddress()) {
                return (Inet4Address) candidate;
            }
        }
        return null;
    }

    private void acceptLoop() {
        while (!stopped && remainingSeconds() > 0) {
            try {
                Socket incoming = listener.accept();
                incoming.setSoTimeout(6500);
                workers.execute(() -> handle(incoming));
            } catch (SocketTimeoutException ignored) {
            } catch (Exception e) {
                if (!stopped) Log.w(TAG, "Config listener error", e);
            }
        }
        shutdown();
    }

    private synchronized void shutdown() {
        if (stopped) return;
        stopped = true;
        try { if (listener != null) listener.close(); } catch (Exception ignored) {}
        workers.shutdownNow();
    }

    private static final class Incoming {
        String method;
        String path;
        Map<String, String> headers = new HashMap<>();
        String body = "";
    }

    private static String readLine(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int previous = -1;
        while (output.size() < 8192) {
            int next = stream.read();
            if (next < 0) throw new IllegalArgumentException("Request incomplete");
            if (previous == '\r' && next == '\n') {
                byte[] bytes = output.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.ISO_8859_1);
            }
            output.write(next);
            previous = next;
        }
        throw new IllegalArgumentException("Header too long");
    }

    private static Incoming parse(InputStream stream) throws Exception {
        Incoming req = new Incoming();
        String[] first = readLine(stream).split(" ");
        if (first.length != 3) throw new IllegalArgumentException("Invalid request");
        req.method = first[0];
        req.path = first[1];
        for (int i = 0; i < 32; i++) {
            String line = readLine(stream);
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("Invalid header");
            req.headers.put(line.substring(0, colon).toLowerCase(Locale.US).trim(),
                line.substring(colon + 1).trim());
        }
        if ("POST".equals(req.method)) {
            if (!"application/json".equals(req.headers.get("content-type")))
                throw new IllegalArgumentException("JSON required");
            int length = Integer.parseInt((req.headers.containsKey("content-length") ? req.headers.get("content-length") : "-1"));
            if (length < 0 || length > MAX_BODY) throw new IllegalArgumentException("Body too large");
            byte[] body = new byte[length];
            int offset = 0;
            while (offset < length) {
                int count = stream.read(body, offset, length - offset);
                if (count < 0) throw new IllegalArgumentException("Body incomplete");
                offset += count;
            }
            req.body = new String(body, StandardCharsets.UTF_8);
        }
        return req;
    }

    private void handle(Socket socket) {
        try (Socket client = socket) {
          try {
            Incoming req = parse(client.getInputStream());
            OutputStream output = client.getOutputStream();
            if (stopped || remainingSeconds() == 0) { reply(output, 503, "text/plain", "Expired", null); return; }
            if (!origin.substring(7).equals(req.headers.get("host"))) {
                reply(output, 403, "text/plain", "Invalid host", null); return;
            }
            boolean authed = authorized(req);
            if ("GET".equals(req.method) && "/".equals(req.path)) {
                asset(output, "config/index.html", "text/html; charset=utf-8");
            } else if ("GET".equals(req.method) && "/app.js".equals(req.path)) {
                asset(output, "config/app.js", "application/javascript; charset=utf-8");
            } else if ("GET".equals(req.method) && "/style.css".equals(req.path)) {
                asset(output, "config/style.css", "text/css; charset=utf-8");
            } else if ("GET".equals(req.method) && "/api/status".equals(req.path)) {
                JSONObject status = new JSONObject().put("authenticated", authed).put("remaining", remainingSeconds());
                json(output, 200, status, null);
            } else if ("POST".equals(req.method) && "/api/login".equals(req.path)) {
                if (!validOrigin(req)) { jsonError(output, 403, "Origin invalid"); return; }
                JSONObject payload = new JSONObject(req.body);
                boolean permitted;
                synchronized (this) {
                    permitted = !pinUsed && failedLogins < 5 && pin.equals(payload.optString("pin", ""));
                    if (permitted) pinUsed = true;
                    else failedLogins++;
                }
                if (permitted) {
                    json(output, 200, new JSONObject().put("ok", true),
                        "Set-Cookie: haframe=" + session + "; HttpOnly; SameSite=Strict; Path=/\r\n");
                } else jsonError(output, 401, "Code incorrect ou expiré (5 essais maximum)");
            } else if (!authed) {
                jsonError(output, 401, "Non autorisé");
            } else if ("GET".equals(req.method) && "/api/config".equals(req.path)) {
                FrameConfig config = FrameConfig.load(context);
                json(output, 200, new JSONObject()
                    .put("url", config.baseUrl)
                    .put("dashboard_title", config.title)
                    .put("custom_css", config.customCss)
                    .put("display", DisplayPrefs.load(context).toJson())
                    .put("token_set", TokenVault.hasToken(context))
                    .put("pages", config.pagesJson()), null);
            } else if ("GET".equals(req.method) && "/api/entities".equals(req.path)) {
                json(output, 200, fetchEntities(), null);
            } else if ("POST".equals(req.method) && "/api/config".equals(req.path)) {
                if (!validOrigin(req)) { jsonError(output, 403, "Origin invalid"); return; }
                saveConfiguration(new JSONObject(req.body));
                json(output, 200, new JSONObject().put("ok", true), null);
            } else if ("POST".equals(req.method) && "/api/logout".equals(req.path)) {
                if (!validOrigin(req)) { jsonError(output, 403, "Origin invalid"); return; }
                json(output, 200, new JSONObject().put("ok", true),
                    "Set-Cookie: haframe=; Max-Age=0; HttpOnly; SameSite=Strict; Path=/\r\n");
                shutdown();
            } else jsonError(output, 404, "Route introuvable");
          } catch (Exception e) {
            // Never log credentials or request bodies.
            Log.w(TAG, "Config request failed: " + e.getClass().getSimpleName());
            try {
                String message = (e instanceof IllegalStateException || e instanceof IllegalArgumentException)
                    ? e.getMessage() : "Échec de la requête ou connexion Home Assistant";
                jsonError(client.getOutputStream(), 400, message == null ? "Requête invalide" : message);
            } catch (Exception ignored) {}
          }
        } catch (Exception ignored) {}
    }

    private boolean authorized(Incoming req) {
        String cookie = (req.headers.containsKey("cookie") ? req.headers.get("cookie") : "");
        for (String part : cookie.split(";")) {
            String value = part.trim();
            if (value.startsWith("haframe=")) {
                return MessageDigest.isEqual(value.substring(8).getBytes(StandardCharsets.US_ASCII),
                    session.getBytes(StandardCharsets.US_ASCII));
            }
        }
        return false;
    }

    private boolean validOrigin(Incoming req) { return origin.equals(req.headers.get("origin")); }

    private static void reply(OutputStream stream, int status, String contentType, String content, String extra) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String code = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 403 ? "Forbidden"
            : status == 404 ? "Not Found" : status == 503 ? "Service Unavailable" : "Bad Request";
        String headers = "HTTP/1.1 " + status + " " + code + "\r\n" +
            "Content-Type: " + contentType + "\r\n" +
            "Content-Length: " + bytes.length + "\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "X-Frame-Options: DENY\r\n" +
            "Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'none'; object-src 'none'; frame-ancestors 'none'\r\n" +
            (extra == null ? "" : extra) + "Connection: close\r\n\r\n";
        stream.write(headers.getBytes(StandardCharsets.UTF_8));
        stream.write(bytes);
        stream.flush();
    }

    private void asset(OutputStream stream, String path, String type) throws Exception {
        try (InputStream input = context.getAssets().open(path)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int count;
            while ((count = input.read(chunk)) != -1) buffer.write(chunk, 0, count);
            reply(stream, 200, type, new String(buffer.toByteArray(), StandardCharsets.UTF_8), null);
        }
    }

    private static void json(OutputStream stream, int status, JSONObject data, String extra) throws Exception {
        reply(stream, status, "application/json; charset=utf-8", data.toString(), extra);
    }

    private static void jsonError(OutputStream stream, int status, String message) throws Exception {
        json(stream, status, new JSONObject().put("error", message), null);
    }

    private JSONObject fetchEntities() throws Exception {
        FrameConfig config = FrameConfig.load(context);
        String token = TokenVault.load(context);
        if (config.baseUrl.isEmpty() || token.isEmpty()) throw new IllegalStateException("Home Assistant non configuré");
        URL url = new URL(config.restStatesUrl());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) throw new IllegalStateException("Home Assistant HTTP " + responseCode);
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (raw.size() + n > 5_000_000) throw new IllegalStateException("Trop d'entités Home Assistant");
                    raw.write(buffer, 0, n);
                }
            }
            JSONArray source = new JSONArray(new String(raw.toByteArray(), StandardCharsets.UTF_8));
            JSONArray items = new JSONArray();
            for (int i = 0; i < source.length() && i < 10000; i++) {
                JSONObject original = source.optJSONObject(i);
                if (original == null) continue;
                String id = original.optString("entity_id", "");
                if (!id.matches("[a-z0-9_]+\\.[a-z0-9_]+")) continue;
                JSONObject attrs = original.optJSONObject("attributes");
                String friendly = attrs == null ? "" : attrs.optString("friendly_name", "");
                items.put(new JSONObject().put("entity_id", id)
                    .put("name", friendly));
            }
            return new JSONObject().put("items", items);
        } finally {
            connection.disconnect();
        }
    }

    private void saveConfiguration(JSONObject body) throws Exception {
        FrameConfig config = FrameConfig.load(context);
        DisplayPrefs display = DisplayPrefs.load(context);
        if (body.has("display")) display.update(body.getJSONObject("display"));
        String endpoint = body.optString("url", "").trim();
        if (endpoint.length() > 255) throw new IllegalArgumentException("URL trop longue");
        config.baseUrl = endpoint;
        config.webSocketUrl();
        String title = body.optString("dashboard_title", config.title).trim();
        if (title.length() > 28) throw new IllegalArgumentException("Titre trop long (28 caractères maximum)");
        config.title = title.isEmpty() ? "HA FRAME" : title;
        String customCss = body.optString("custom_css", config.customCss);
        CanvasTheme.parse(customCss);
        config.customCss = customCss;
        config.setPages(body.getJSONArray("pages"));
        String incomingToken = body.optString("token", "").trim();
        if (!incomingToken.isEmpty()) {
            if (incomingToken.length() > 4096) throw new IllegalArgumentException("Token trop long");
            TokenVault.save(context, incomingToken);
        } else if (!TokenVault.hasToken(context)) {
            throw new IllegalArgumentException("Token Home Assistant manquant");
        }
        config.save(context);
        display.save(context);
        context.sendBroadcast(new Intent(ACTION_CONFIG_UPDATED).setPackage(context.getPackageName()));
    }
}
