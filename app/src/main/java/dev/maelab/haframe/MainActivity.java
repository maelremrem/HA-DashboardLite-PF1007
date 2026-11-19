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
            @Override public void command(String entityId) {
                if (ha != null && ha.isConnected()) ha.activate(entityId);
                else Toast.makeText(MainActivity.this, "Home Assistant hors ligne", Toast.LENGTH_SHORT).show();
            }
        });
        setContentView(dashboard);
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
    }

    private void reload() {
        config = FrameConfig.load(this);
        dashboard.setConfig(config);
        if (ha != null) { ha.stop(); ha = null; }
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
            ha = new HaClient(config.webSocketUrl(), TokenVault.load(this), config.subscribedEntityIds(), this);
            dashboard.setClient(ha);
            ha.start();
        } catch (Exception e) {
            dashboard.setStatus("Erreur configuration / Keystore", false);
            Toast.makeText(this, "Ouvrir Réglages : " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onPause() {
        if (ha != null) { ha.stop(); ha = null; }
        super.onPause();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(configUpdated);
        super.onDestroy();
    }

    @Override public void onStatus(String status, boolean connected) {
        dashboard.setStatus(status, connected);
    }

    @Override public void onStatesChanged() {
        dashboard.invalidate();
    }

    @Override public void onCommandError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
