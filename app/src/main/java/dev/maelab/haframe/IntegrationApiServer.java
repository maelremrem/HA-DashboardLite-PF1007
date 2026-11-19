package dev.maelab.haframe;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opt-in LAN API for the Home Assistant custom integration, never exposes the HA token. */
final class IntegrationApiServer {
    static final String ACTION_DISPLAY = "dev.maelab.haframe.DISPLAY_UPDATED";
    static final String ACTION_NOTIFY = "dev.maelab.haframe.NOTIFY";
    static final String ACTION_WAKE = "dev.maelab.haframe.WAKE";
    static final int PORT = 8766;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile IntegrationApiServer current;
    private static volatile float lastLux = Float.NaN;
    private static volatile String lastSource = "none";
    private static volatile boolean lastSleeping;
    private static volatile int lastBrightness = 75;
    private static volatile boolean hasSensor;

    private final Context context;
    private final String url;
    private final String token;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final ServerSocket socket;
    private volatile boolean stopped;
    private volatile String pin = "";
    private volatile long pinExpiry;
    private int failures;

    private IntegrationApiServer(Context ctx, Inet4Address address) throws Exception {
        context = ctx.getApplicationContext();
        url = "http://" + address.getHostAddress() + ":" + PORT;
        if (!context.getSharedPreferences("secrets", Context.MODE_PRIVATE).contains("device_api")) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format(Locale.US, "%02x", b & 255));
            TokenVault.saveSecret(context, "device_api", hex.toString());
        }
        token = TokenVault.loadSecret(context, "device_api");
        socket = new ServerSocket(PORT, 8, address);
        socket.setSoTimeout(1000);
    }

    static synchronized IntegrationApiServer start(Context context) throws Exception {
        if (current != null && !current.stopped) return current;
        Inet4Address address = wifiAddress();
        if (address == null) throw new IllegalStateException("Wi-Fi unavailable");
        IntegrationApiServer server = new IntegrationApiServer(context, address);
        current = server;
        Thread thread = new Thread(server::loop, "haframe-ha-api");
        thread.setDaemon(true);
        thread.start();
        return server;
    }

    static synchronized void stop() {
        if (current != null) current.shutdown();
        current = null;
    }

    static IntegrationApiServer active() { return current == null || current.stopped ? null : current; }
    String url() { return url; }

    synchronized String newPin() {
        pin = String.format(Locale.US, "%06d", RANDOM.nextInt(1000000));
        pinExpiry = SystemClock.elapsedRealtime() + 120000L;
        failures = 0;
        return pin;
    }

    static void updateRuntime(AmbientController c) {
        lastLux = c.lux();
        lastSource = c.source();
        lastSleeping = c.sleeping();
        lastBrightness = c.brightness();
        hasSensor = c.hasPhysicalSensor();
    }

    private static Inet4Address wifiAddress() throws Exception {
        NetworkInterface wifi = NetworkInterface.getByName("wlan0");
        if (wifi == null || !wifi.isUp()) return null;
        Enumeration<InetAddress> addresses = wifi.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress candidate = addresses.nextElement();
            if (candidate instanceof Inet4Address && !candidate.isLoopbackAddress())
                return (Inet4Address) candidate;
        }
        return null;
    }

    private void loop() {
        while (!stopped) {
            try {
                Socket incoming = socket.accept();
                incoming.setSoTimeout(5000);
                workers.execute(() -> handle(incoming));
            } catch (SocketTimeoutException ignored) {
            } catch (Exception e) {
                if (!stopped) Log.w("HAFrameApi", "Connection failed", e);
            }
        }
    }

    private void shutdown() {
        stopped = true;
        try { socket.close(); } catch (Exception ignored) {}
        workers.shutdownNow();
    }

    private static class Request {
        String method, path, body = "";
        Map<String, String> headers = new HashMap<>();
    }

    private static String line(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previous = -1;
        while (out.size() < 8192) {
            int b = in.read();
            if (b < 0) throw new IllegalArgumentException("Incomplete request");
            if (previous == '\r' && b == '\n') {
                byte[] raw = out.toByteArray();
                return new String(raw, 0, raw.length - 1, StandardCharsets.ISO_8859_1);
            }
            out.write(b);
            previous = b;
        }
        throw new IllegalArgumentException("Header too long");
    }

    private static Request parse(InputStream in) throws Exception {
        Request r = new Request();
        String[] first = line(in).split(" ");
        if (first.length != 3) throw new IllegalArgumentException("Invalid request");
        r.method = first[0];
        r.path = first[1];
        boolean ended = false;
        for (int i = 0; i < 32; i++) {
            String item = line(in);
            if (item.isEmpty()) { ended = true; break; }
            int colon = item.indexOf(':');
            if (colon < 1) throw new IllegalArgumentException("Invalid header");
            r.headers.put(item.substring(0, colon).trim().toLowerCase(Locale.US), item.substring(colon + 1).trim());
        }
        if (!ended) throw new IllegalArgumentException("Too many headers");
        if ("POST".equals(r.method)) {
            String contentType = r.headers.get("content-type");
            if (contentType == null || !contentType.toLowerCase(Locale.US).startsWith("application/json"))
                throw new IllegalArgumentException("JSON required");
            int size = Integer.parseInt(r.headers.containsKey("content-length") ? r.headers.get("content-length") : "-1");
            if (size < 0 || size > 32768) throw new IllegalArgumentException("Invalid request length");
            byte[] raw = new byte[size];
            int pos = 0;
            while (pos < size) {
                int n = in.read(raw, pos, size - pos);
                if (n <= 0) throw new IllegalArgumentException("Incomplete request body");
                pos += n;
            }
            r.body = new String(raw, StandardCharsets.UTF_8);
        }
        return r;
    }

    private static void send(OutputStream out, int code, JSONObject body) throws Exception {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        String reason = code == 200 ? "OK" : code == 401 ? "Unauthorized" : code == 403 ? "Forbidden" : "Bad Request";
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: " + bytes.length + "\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private synchronized boolean pair(String code) {
        if (pin.isEmpty() || SystemClock.elapsedRealtime() >= pinExpiry || failures >= 5) return false;
        if (!MessageDigest.isEqual(pin.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) {
            failures++;
            return false;
        }
        pin = "";
        pinExpiry = 0;
        return true;
    }

    private boolean authenticated(Request r) {
        String header = r.headers.get("authorization");
        return header != null && header.startsWith("Bearer ") && MessageDigest.isEqual(
            header.substring(7).getBytes(StandardCharsets.US_ASCII), token.getBytes(StandardCharsets.US_ASCII));
    }

    private void handle(Socket client) {
        try (Socket connection = client) {
            OutputStream out = connection.getOutputStream();
            try {
                Request req = parse(connection.getInputStream());
                if (!DisplayPrefs.load(context).integrationEnabled) {
                    send(out, 403, new JSONObject().put("error", "Integration disabled"));
                    return;
                }
                if ("POST".equals(req.method) && "/api/v1/pair".equals(req.path)) {
                    JSONObject request = new JSONObject(req.body);
                    if (!pair(request.optString("code"))) {
                        send(out, 401, new JSONObject().put("error", "Invalid or expired pairing code"));
                        return;
                    }
                    send(out, 200, new JSONObject().put("token", token).put("device_id", deviceId()));
                    return;
                }
                if (!authenticated(req)) {
                    send(out, 401, new JSONObject().put("error", "Authentication required"));
                    return;
                }
                if ("GET".equals(req.method) && "/api/v1/status".equals(req.path)) {
                    JSONObject data = DisplayPrefs.load(context).toJson();
                    data.put("device_id", deviceId()).put("name", "HA Frame PF1007")
                        .put("version", "0.7.0").put("lux", Float.isNaN(lastLux) ? JSONObject.NULL : lastLux)
                        .put("lux_source", lastSource).put("has_light_sensor", hasSensor)
                        .put("sleeping", lastSleeping).put("effective_brightness", lastBrightness);
                    send(out, 200, data);
                } else if ("GET".equals(req.method) && "/api/v1/config".equals(req.path)) {
                    FrameConfig config = FrameConfig.load(context);
                    send(out, 200, new JSONObject().put("dashboard_title", config.title)
                        .put("custom_css", config.customCss).put("pages", config.pagesJson())
                        .put("display", DisplayPrefs.load(context).toJson()));
                } else if ("POST".equals(req.method) && "/api/v1/config".equals(req.path)) {
                    JSONObject body = new JSONObject(req.body);
                    DisplayPrefs display = DisplayPrefs.load(context);
                    if (body.has("display")) display.update(body.getJSONObject("display"));
                    FrameConfig frame = FrameConfig.load(context);
                    boolean layout = body.has("pages") || body.has("dashboard_title") || body.has("custom_css");
                    if (body.has("pages")) frame.setPages(body.getJSONArray("pages"));
                    if (body.has("custom_css")) {
                        frame.customCss = body.getString("custom_css");
                        CanvasTheme.parse(frame.customCss);
                    }
                    if (body.has("dashboard_title")) {
                        frame.title = body.getString("dashboard_title").trim();
                        if (frame.title.isEmpty() || frame.title.length() > 28)
                            throw new IllegalArgumentException("Invalid dashboard title");
                    }
                    if (layout) frame.save(context);
                    display.save(context);
                    Intent event = new Intent(layout ? ConfigWebServer.ACTION_CONFIG_UPDATED : ACTION_DISPLAY);
                    context.sendBroadcast(event.setPackage(context.getPackageName()));
                    send(out, 200, new JSONObject().put("ok", true));
                } else if ("POST".equals(req.method) && "/api/v1/notify".equals(req.path)) {
                    JSONObject data = new JSONObject(req.body);
                    String title = data.optString("title", "Home Assistant");
                    String message = data.optString("message", "");
                    int seconds = data.optInt("duration", 12);
                    if (message.isEmpty() || message.length() > 500 || title.length() > 80 || seconds < 3 || seconds > 60)
                        throw new IllegalArgumentException("Invalid notification");
                    context.sendBroadcast(new Intent(ACTION_NOTIFY).setPackage(context.getPackageName())
                        .putExtra("title", title).putExtra("message", message).putExtra("duration", seconds));
                    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (manager != null) manager.notify(1001, new Notification.Builder(context)
                        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title)
                        .setContentText(message).setAutoCancel(true).build());
                    send(out, 200, new JSONObject().put("ok", true));
                } else if ("POST".equals(req.method) && "/api/v1/wake".equals(req.path)) {
                    context.sendBroadcast(new Intent(ACTION_WAKE).setPackage(context.getPackageName()));
                    send(out, 200, new JSONObject().put("ok", true));
                } else send(out, 400, new JSONObject().put("error", "Unknown API route"));
            } catch (Exception e) {
                Log.w("HAFrameApi", "API request failed: " + e.getClass().getSimpleName());
                try { send(out, 400, new JSONObject().put("error", "Invalid request")); }
                catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private String deviceId() {
        SharedPreferences p = context.getSharedPreferences("device", Context.MODE_PRIVATE);
        String result = p.getString("id", "");
        if (result.isEmpty()) {
            result = UUID.randomUUID().toString();
            p.edit().putString("id", result).commit();
        }
        return result;
    }
}
