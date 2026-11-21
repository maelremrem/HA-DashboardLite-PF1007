package dev.maelab.haframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reads one downsampled photo at a time from the app's external Pictures directory. */
final class PhotoSlideshow {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File folder;
    private final Runnable changed;
    private volatile Bitmap current;
    private volatile Bitmap previous;
    private volatile long transitionAt;
    private volatile boolean stopped;
    private int index;

    PhotoSlideshow(Context context, Runnable changed) {
        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        folder = pictures == null ? null : new File(pictures, "HAFrame");
        this.changed = changed;
        if (folder != null) folder.mkdirs();
    }

    File directory() { return folder; }
    Bitmap image() { return current; }
    Bitmap previous() { return previous; }
    long transitionAt() { return transitionAt; }

    void advance() {
        if (stopped || folder == null || !busy.compareAndSet(false, true)) return;
        loader.execute(new Runnable() {
            @Override public void run() {
                try {
                    File[] files = folder.listFiles(new java.io.FileFilter() {
                        @Override public boolean accept(File file) {
                            return file.isFile() && file.length() <= 6 * 1024 * 1024 &&
                                file.getName().toLowerCase(java.util.Locale.US)
                                    .matches(".*\\.(?:jpe?g|png)");
                        }
                    });
                    if (files == null || files.length == 0) {
                        current = null;
        previous = null;
                        return;
                    }
                    Arrays.sort(files, new Comparator<File>() {
                        @Override public int compare(File a, File b) {
                            return a.getName().compareToIgnoreCase(b.getName());
                        }
                    });
                    File selected = files[index++ % Math.min(100, files.length)];
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(selected.getAbsolutePath(), options);
                    if (options.outWidth <= 0 || options.outHeight <= 0) return;
                    int sample = 1;
                    while (options.outWidth / sample > 1280 || options.outHeight / sample > 800) {
                        sample *= 2;
                    }
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = sample;
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    Bitmap picture = BitmapFactory.decodeFile(selected.getAbsolutePath(), options);
                    if (!stopped && picture != null) {
                        previous = current;
                        current = picture;
                        transitionAt = SystemClock.elapsedRealtime();
                    }
                } catch (RuntimeException ignored) {
                    // A corrupt image must not terminate the dashboard.
                } finally {
                    busy.set(false);
                    if (!stopped) main.post(changed);
                }
            }
        });
    }

    void stop() {
        stopped = true;
        loader.shutdownNow();
        current = null;
    }
}
