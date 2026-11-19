package dev.maelab.haframe;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import java.util.Calendar;

/** Reads a physical light sensor, or an HA illuminance entity if configured. */
final class AmbientController implements SensorEventListener {
    private final Activity activity;
    private final DashboardView dashboard;
    private final SensorManager sensors;
    private final Sensor lightSensor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            apply();
            main.postDelayed(this, 5000L);
        }
    };
    private DisplayPrefs prefs;
    private HaClient client;
    private float physicalLux = Float.NaN;
    private float currentLux = Float.NaN;
    private String source = "none";
    private int currentBrightness = -1;
    private boolean sleeping;
    private long awakeUntil;

    AmbientController(Activity activity, DashboardView dashboard) {
        this.activity = activity;
        this.dashboard = dashboard;
        sensors = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        prefs = DisplayPrefs.load(activity);
    }

    boolean hasPhysicalSensor() { return lightSensor != null; }
    String source() { return source; }
    float lux() { return currentLux; }
    int brightness() { return currentBrightness < 0 ? prefs.brightness : currentBrightness; }
    boolean sleeping() { return sleeping; }

    void resume() {
        prefs = DisplayPrefs.load(activity);
        if (lightSensor != null) sensors.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        main.removeCallbacks(ticker);
        ticker.run();
    }

    void pause() {
        main.removeCallbacks(ticker);
        if (lightSensor != null) sensors.unregisterListener(this);
    }

    void setClient(HaClient client) { this.client = client; apply(); }
    void refresh() { prefs = DisplayPrefs.load(activity); apply(); }

    void temporaryWake(long millis) {
        awakeUntil = SystemClock.elapsedRealtime() + millis;
        apply();
    }

    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }

    private void apply() {
        float lux = Float.NaN;
        String selected = prefs.luxEntityId;
        if (!selected.isEmpty() && client != null && client.isConnected()) {
            HaClient.EntityState state = client.get(selected);
            if (state != null) {
                try { lux = Float.parseFloat(state.state); }
                catch (NumberFormatException ignored) {}
            }
            if (finite(lux) && lux >= 0) source = "home_assistant";
        }
        if (!finite(lux) && finite(physicalLux)) {
            lux = physicalLux;
            source = "device";
        } else if (!finite(lux)) source = "none";
        currentLux = lux;
        boolean inHours = prefs.nightEnabled && prefs.withinNightHours(Calendar.getInstance());
        boolean low = finite(lux) ? (sleeping ? lux < Math.max(1, prefs.nightLux * 1.5f) : lux <= prefs.nightLux)
            : prefs.timeFallback;
        boolean newSleeping = (!prefs.screenEnabled || (inHours && low)) && SystemClock.elapsedRealtime() >= awakeUntil;
        if (newSleeping != sleeping) {
            sleeping = newSleeping;
            dashboard.setSleeping(sleeping);
        }
        int target = prefs.brightness;
        if (prefs.autoBrightness && finite(lux)) {
            // Logarithmic mapping makes changes in dark rooms more noticeable.
            double normalized = Math.log1p(Math.min(lux, 2000)) / Math.log1p(2000);
            target = prefs.autoMin + (int) Math.round((prefs.autoMax - prefs.autoMin) * normalized);
        }
        if (sleeping) target = 1;
        IntegrationApiServer.updateRuntime(this);
        if (target != currentBrightness) {
            currentBrightness = target;
            IntegrationApiServer.updateRuntime(this);
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.screenBrightness = target / 100f;
            activity.getWindow().setAttributes(params);
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT && event.values.length > 0) {
            physicalLux = Math.max(0f, event.values[0]);
            apply();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
