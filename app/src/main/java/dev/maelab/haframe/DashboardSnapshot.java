package dev.maelab.haframe;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Read-only, authenticated thumbnail capture; never accesses device secrets. */
final class DashboardSnapshot {
    private static volatile WeakReference<DashboardView> current = new WeakReference<>(null);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    static void attach(DashboardView view) { current = new WeakReference<>(view); }
    static void detach() { current = new WeakReference<>(null); }

    static String capture() throws Exception {
        final DashboardView view = current.get();
        if (view == null || view.getWidth() < 1 || view.getHeight() < 1)
            throw new IllegalStateException("Dashboard not visible");
        final CountDownLatch complete = new CountDownLatch(1);
        final String[] result = new String[1];
        Runnable task = new Runnable() {
            @Override public void run() {
                Bitmap bitmap = null;
                try {
                    bitmap = Bitmap.createBitmap(640, 400, Bitmap.Config.RGB_565);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.scale(640f / view.getWidth(), 400f / view.getHeight());
                    view.draw(canvas);
                    ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 58, jpeg);
                    if (jpeg.size() <= 220000) {
                        result[0] = Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP);
                    }
                } catch (RuntimeException ignored) {
                } finally {
                    if (bitmap != null) bitmap.recycle();
                    complete.countDown();
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else {
            UI.post(task);
            if (!complete.await(2500, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Screenshot timed out");
            }
        }
        if (result[0] == null) throw new IllegalStateException("Screenshot unavailable");
        return result[0];
    }

    private DashboardSnapshot() {}
}
