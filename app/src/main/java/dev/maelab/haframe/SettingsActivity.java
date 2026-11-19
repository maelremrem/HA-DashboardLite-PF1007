package dev.maelab.haframe;

import android.app.Activity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private final BroadcastReceiver configUpdated = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            // Do not overwrite a newer web configuration with stale form fields.
            Toast.makeText(SettingsActivity.this, "Configuration web appliquée", Toast.LENGTH_SHORT).show();
            finish();
        }
    };
    private EditText url;
    private Button webToggle;
    private TextView webStatus;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable webTicker = new Runnable() {
        @Override public void run() {
            updateWebStatus();
            handler.postDelayed(this, 1000);
        }
    };
    private EditText token;
    private final EditText[] labels = new EditText[FrameConfig.COUNT];
    private final EditText[] entities = new EditText[FrameConfig.COUNT];

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private EditText field(String placeholder, String value) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF78879D);
        e.setHint(placeholder);
        e.setText(value);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        return e;
    }

    private void add(LinearLayout parent, View child, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(bottom);
        parent.addView(child, lp);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameConfig current = FrameConfig.load(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF10151E);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(38), dp(24), dp(38), dp(30));
        scroll.addView(root);

        add(root, text("HA FRAME   /   CONFIGURATION", 26, Color.WHITE), 14);
        add(root, text("Application Android native · aucun WebView", 15, 0xFF91A1B5), 18);

        add(root, text("CONFIGURATION DEPUIS UN NAVIGATEUR", 19, 0xFF6BA8FF), 7);
        add(root, text("Active temporairement le serveur sur le Wi-Fi du cadre. Code d'accès affiché ici. Durée maximum : 15 minutes.",
            14, 0xFF91A1B5), 8);
        webToggle = new Button(this);
        webToggle.setOnClickListener(v -> toggleWeb());
        add(root, webToggle, 3);
        webStatus = text("", 16, Color.WHITE);
        add(root, webStatus, 22);
        updateWebStatus();
        add(root, text("URL Home Assistant", 18, Color.WHITE), 4);
        url = field("http://192.168.1.x:8123", current.baseUrl);
        add(root, url, 12);
        add(root, text("Long-Lived Access Token", 18, Color.WHITE), 4);
        token = field(TokenVault.hasToken(this) ? "Laisser vide pour conserver le token" : "Coller le token ici", "");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(root, token, 9);
        add(root, text("Token chiffré avec Android Keystore. Utiliser HTTPS lorsque possible ; ne jamais exposer le token.",
            13, 0xFF91A1B5), 20);

        add(root, text("3 CAPTEURS", 20, 0xFF6BA8FF), 10);
        for (int i = 0; i < FrameConfig.COUNT; i++) {
            if (i == FrameConfig.METRICS) add(root, text("6 COMMANDES", 20, 0xFF6BA8FF), 12);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            labels[i] = field("Titre", current.labels[i]);
            entities[i] = field("sensor.xxx / light.xxx", current.ids[i]);
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1);
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, -2, 2);
            b.leftMargin = dp(12);
            row.addView(labels[i], a);
            row.addView(entities[i], b);
            add(root, row, 6);
        }

        add(root, text("Commandes prises en charge : light, switch, fan, input_boolean, cover, scene, script, button, input_button.",
            13, 0xFF91A1B5), 18);
        Button save = new Button(this);
        save.setText("ENREGISTRER ET CONNECTER");
        save.setOnClickListener(v -> save());
        add(root, save, 8);
        Button cancel = new Button(this);
        cancel.setText("RETOUR AU DASHBOARD");
        cancel.setOnClickListener(v -> finish());
        add(root, cancel, 0);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(configUpdated, new IntentFilter(ConfigWebServer.ACTION_CONFIG_UPDATED));
        handler.removeCallbacks(webTicker);
        handler.post(webTicker);
    }

    @Override protected void onPause() {
        unregisterReceiver(configUpdated);
        handler.removeCallbacks(webTicker);
        super.onPause();
    }

    private void toggleWeb() {
        if (ConfigWebServer.active() != null) {
            ConfigWebServer.stop();
        } else {
            try {
                ConfigWebServer.start(this);
            } catch (Exception e) {
                Toast.makeText(this, "Serveur indisponible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        updateWebStatus();
    }

    private void updateWebStatus() {
        if (webToggle == null || webStatus == null) return;
        ConfigWebServer current = ConfigWebServer.active();
        if (current == null) {
            webToggle.setText("ACTIVER LE SERVEUR WEB (15 MIN)");
            webStatus.setText("Serveur désactivé · Aucune interface réseau exposée");
        } else {
            webToggle.setText("DÉSACTIVER LE SERVEUR WEB");
            long seconds = current.remainingSeconds();
            webStatus.setText("Adresse : " + current.url() + "\nCode d'accès : " + current.pin() +
                "\nExpiration : " + (seconds / 60) + " min " + (seconds % 60) + " s");
        }
    }

    private void save() {
        FrameConfig next = new FrameConfig();
        next.baseUrl = url.getText().toString().trim();
        try { next.webSocketUrl(); }
        catch (Exception e) {
            url.setError(e.getMessage());
            return;
        }
        String inputToken = token.getText().toString().trim();
        if (inputToken.isEmpty() && !TokenVault.hasToken(this)) {
            token.setError("Token obligatoire");
            return;
        }
        for (int i = 0; i < FrameConfig.COUNT; i++) {
            next.labels[i] = labels[i].getText().toString().trim();
            next.ids[i] = entities[i].getText().toString().trim();
            if (!next.ids[i].isEmpty() && !next.ids[i].matches("[a-z0-9_]+\\.[a-z0-9_]+")) {
                entities[i].setError("Exemple : light.salon");
                return;
            }
        }
        try {
            if (!inputToken.isEmpty()) TokenVault.save(this, inputToken);
            else TokenVault.load(this);
        } catch (Exception e) {
            Toast.makeText(this, "Keystore indisponible : " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            return;
        }
        next.save(this);
        Toast.makeText(this, "Configuration enregistrée", Toast.LENGTH_SHORT).show();
        finish();
    }
}
