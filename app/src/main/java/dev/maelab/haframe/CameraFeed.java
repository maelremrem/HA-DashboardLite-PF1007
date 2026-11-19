package dev.maelab.haframe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Single low-bandwidth camera feed for the visible page.
 * Uses authenticated Home Assistant MJPEG still-stream proxy (~1 fps),
 * falling back to JPEG snapshots if streaming is unavailable.
 */
final class CameraFeed {
    private static final int MAX_FRAME_BYTES = 750_000;
    private final OkHttpClient client;
    private final String endpoint;
    private final String token;
    private final Runnable redraw;
    private volatile String active = "";
    private volatile Bitmap bitmap;
    private volatile Call call;
    private volatile int generation;

    CameraFeed(OkHttpClient client, String endpoint, String token, Runnable redraw) {
        this.client = client;
        this.endpoint = endpoint;
        this.token = token;
        this.redraw = redraw;
    }

    synchronized void select(String entityId) {
        String next = entityId == null ? "" : entityId;
        if (!next.isEmpty() && !next.matches("camera\\.[a-z0-9_]+")) next = "";
        if (next.equals(active)) return;
        active = next;
        bitmap = null;
        int id = ++generation;
        Call previous = call;
        call = null;
        if (previous != null) previous.cancel();
        if (!next.isEmpty()) {
            Thread worker = new Thread(() -> run(next, id), "haframe-camera");
            worker.setDaemon(true);
            worker.start();
        }
    }

    synchronized void stop() {
        active = "";
        generation++;
        bitmap = null;
        Call previous = call;
        call = null;
        if (previous != null) previous.cancel();
    }

    Bitmap frame(String entityId) {
        return active.equals(entityId) ? bitmap : null;
    }

    private boolean running(String entityId, int id) {
        return generation == id && entityId.equals(active);
    }

    private String endpoint(String entityId, boolean stream) throws Exception {
        URI ws = new URI(endpoint);
        String scheme = "wss".equalsIgnoreCase(ws.getScheme()) ? "https" : "http";
        String basePath = ws.getPath().replaceFirst("/api/websocket/?$", "");
        String path = basePath + (stream ? "/api/camera_proxy_stream/" : "/api/camera_proxy/") + entityId;
        return new URI(scheme, null, ws.getHost(), ws.getPort(), path,
            stream ? "interval=1.0" : null, null).toASCIIString();
    }

    private Response open(String url) throws Exception {
        Request request = new Request.Builder().url(url)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "multipart/x-mixed-replace,image/jpeg")
            .build();
        Call pending = client.newCall(request);
        call = pending;
        return pending.execute();
    }

    private void publish(byte[] jpeg, String entityId, int id) {
        if (!running(entityId, id)) return;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        Bitmap decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
        if (decoded != null && running(entityId, id)) {
            bitmap = decoded;
            redraw.run();
        }
    }

    private void stream(String entityId, int id) throws Exception {
        try (Response response = open(endpoint(entityId, true))) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new java.io.IOException("Camera MJPEG proxy unavailable");
            }
            InputStream data = response.body().byteStream();
            byte[] chunk = new byte[4096];
            ByteArrayOutputStream jpeg = null;
            int previous = -1;
            long lastPublished = 0L;
            int count;
            while (running(entityId, id) && (count = data.read(chunk)) != -1) {
                for (int i = 0; i < count && running(entityId, id); i++) {
                    int next = chunk[i] & 0xff;
                    if (jpeg == null) {
                        if (previous == 0xff && next == 0xd8) {
                            jpeg = new ByteArrayOutputStream(32_768);
                            jpeg.write(0xff);
                            jpeg.write(0xd8);
                        }
                    } else {
                        jpeg.write(next);
                        if (jpeg.size() > MAX_FRAME_BYTES) {
                            jpeg = null;
                        } else if (previous == 0xff && next == 0xd9) {
                            long now = SystemClock.elapsedRealtime();
                            if (now - lastPublished >= 900L) {
                                publish(jpeg.toByteArray(), entityId, id);
                                lastPublished = now;
                            }
                            jpeg = null;
                        }
                    }
                    previous = next;
                }
            }
        } finally {
            call = null;
        }
    }

    private void snapshot(String entityId, int id) throws Exception {
        try (Response response = open(endpoint(entityId, false))) {
            if (!response.isSuccessful() || response.body() == null) return;
            InputStream stream = response.body().byteStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64_000);
            byte[] buffer = new byte[8192];
            int count;
            while (running(entityId, id) && (count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_FRAME_BYTES) return;
                bytes.write(buffer, 0, count);
            }
            if (running(entityId, id)) publish(bytes.toByteArray(), entityId, id);
        } finally {
            call = null;
        }
    }

    private void run(String entityId, int id) {
        while (running(entityId, id)) {
            try {
                stream(entityId, id);
            } catch (Exception ignored) {
                // Some cameras cannot provide MJPEG; try snapshots instead.
            }
            if (!running(entityId, id)) return;
            try {
                while (running(entityId, id)) {
                    snapshot(entityId, id);
                    Thread.sleep(1400L);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
                try { Thread.sleep(2500L); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
}
