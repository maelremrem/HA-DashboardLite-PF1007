package dev.maelab.haframe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public final class MainActivity extends Activity implements HaClient.Listener {
    private DashboardView dashboard;
    private HaClient ha;
    private FrameConfig config;
    private boolean prompted = false;
    private AmbientController ambient;
    private String subscribedLuxEntity = "";
    private final BroadcastReceiver apiUpdates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (IntegrationApiServer.ACTION_DISPLAY.equals(action)) {
                if (ambient != null) ambient.refresh();
                String now = DisplayPrefs.load(MainActivity.this).luxEntityId;
                if (!now.equals(subscribedLuxEntity)) reload();
            } else if (IntegrationApiServer.ACTION_WAKE.equals(action)) {
                if (ambient != null) ambient.temporaryWake(120000L);
            } else if (IntegrationApiServer.ACTION_NOTIFY.equals(action)) {
                if (ambient != null) ambient.temporaryWake(Math.max(20000L, intent.getIntExtra("duration", 12) * 1000L));
                dashboard.showNotification(intent.getStringExtra("title"), intent.getStringExtra("message"),
                    intent.getIntExtra("duration", 12));
            }
        }
    };
    private final BroadcastReceiver configUpdated = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // Web configuration is saved on a server thread. Apply it immediately
            // if the dashboard is visible; otherwise onResume() reloads it.
            if (getWindow().getDecorView().hasWindowFocus()) reload();
        }
    };

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreen();
        dashboard = new DashboardView(this);
        dashboard.setCallbacks(new DashboardView.Callbacks() {
            @Override public void settings() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
            @Override public void retryConnection() {
                if (ha != null) ha.retryNow();
                else startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
            @Override public void level(String entityId, int percent) {
                if (ha != null && ha.isConnected()) ha.setLevel(entityId, percent);
            }
            @Override public void temperature(String entityId, double value) {
                if (ha != null && ha.isConnected()) ha.setTemperature(entityId, value);
            }
            @Override public void wakeDisplay() {
                if (ambient != null) ambient.temporaryWake(120000L);
            }
            @Override public void command(String entityId) {
                if (ha != null && ha.isConnected()) ha.activate(entityId);
                else Toast.makeText(MainActivity.this, "Home Assistant hors ligne", Toast.LENGTH_SHORT).show();
            }
        });
        setContentView(dashboard);
        ambient = new AmbientController(this, dashboard);
        IntentFilter apiFilter = new IntentFilter();
        apiFilter.addAction(IntegrationApiServer.ACTION_DISPLAY);
        apiFilter.addAction(IntegrationApiServer.ACTION_NOTIFY);
        apiFilter.addAction(IntegrationApiServer.ACTION_WAKE);
        registerReceiver(apiUpdates, apiFilter);
        registerReceiver(configUpdated, new IntentFilter(ConfigWebServer.ACTION_CONFIG_UPDATED));
    }

    private void fullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) fullscreen();
    }

    @Override protected void onResume() {
        super.onResume();
        reload();
        ambient.resume();
        if (DisplayPrefs.load(this).integrationEnabled) {
            try { IntegrationApiServer.start(this); }
            catch (Exception e) { Toast.makeText(this, "API HA inactive : " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        }
    }

    private void reload() {
        config = FrameConfig.load(this);
        dashboard.setConfig(config);
        if (ha != null) { ha.stop(); ha = null; }
        if (ambient != null) { ambient.setClient(null); ambient.refresh(); }
        if (config.baseUrl.isEmpty() || !TokenVault.hasToken(this)) {
            dashboard.setClient(null);
            dashboard.setStatus("Configuration nécessaire", false);
            if (!prompted) {
                prompted = true;
                dashboard.post(() -> startActivity(new Intent(this, SettingsActivity.class)));
            }
            return;
        }
        try {
            String[] ids = config.subscribedEntityIds();
            String luxId = DisplayPrefs.load(this).luxEntityId;
            subscribedLuxEntity = luxId;
            if (!luxId.isEmpty()) {
                boolean found = false;
                for (String id : ids) if (id.equals(luxId)) found = true;
                if (!found) {
                    String[] extended = java.util.Arrays.copyOf(ids, ids.length + 1);
                    extended[ids.length] = luxId;
                    ids = extended;
                }
            }
            ha = new HaClient(config.webSocketUrl(), TokenVault.load(this), ids, this);
            dashboard.setClient(ha);
            ambient.setClient(ha);
            ha.start();
        } catch (Exception e) {
            dashboard.setStatus("Erreur configuration / Keystore", false);
            Toast.makeText(this, "Ouvrir Réglages : " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onPause() {
        if (ambient != null) ambient.pause();
        if (ha != null) { ha.stop(); ha = null; }
        super.onPause();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(apiUpdates);
        unregisterReceiver(configUpdated);
        super.onDestroy();
    }

    @Override public void onStatus(String status, boolean connected) {
        dashboard.setStatus(status, connected);
    }

    @Override public void onStatesChanged() {
        if (ambient != null) ambient.refresh();
        dashboard.refreshContext();
    }

    @Override public void onCommandError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
