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
import android.widget.Switch;
import android.widget.SeekBar;

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
    private Switch nightToggle, autoToggle, fallbackToggle;
    private EditText nightStartField, nightEndField, luxField, luxEntityField;
    private SeekBar manualBrightness;
    private Button haToggle, pairButton;
    private TextView haStatus;

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

        add(root, text("INTÉGRATION HOME ASSISTANT", 19, 0xFF6BA8FF), 7);
        add(root, text("API locale 8766 pour les entités et notifications. Active uniquement sur un Wi-Fi privé.",
            14, 0xFF91A1B5), 5);
        haToggle = new Button(this);
        haToggle.setOnClickListener(v -> toggleIntegration());
        add(root, haToggle, 4);
        pairButton = new Button(this);
        pairButton.setText("GÉNÉRER UN CODE DE JUMELAGE (2 MIN)");
        pairButton.setOnClickListener(v -> issuePin());
        add(root, pairButton, 3);
        haStatus = text("", 15, Color.WHITE);
        add(root, haStatus, 18);
        updateIntegrationStatus();

        DisplayPrefs display = DisplayPrefs.load(this);
        add(root, text("LUMINOSITÉ ET MODE NUIT", 19, 0xFF6BA8FF), 8);
        autoToggle = new Switch(this);
        autoToggle.setText("Luminosité automatique");
        autoToggle.setChecked(display.autoBrightness);
        add(root, autoToggle, 5);
        nightToggle = new Switch(this);
        nightToggle.setText("Mode nuit (horaire + faible lumière)");
        nightToggle.setChecked(display.nightEnabled);
        add(root, nightToggle, 5);
        fallbackToggle = new Switch(this);
        fallbackToggle.setText("Secours horaire si aucun capteur n'est disponible");
        fallbackToggle.setChecked(display.timeFallback);
        add(root, fallbackToggle, 9);
        add(root, text("Luminosité manuelle (1 à 100 %)", 15, Color.WHITE), 4);
        manualBrightness = new SeekBar(this);
        manualBrightness.setMax(99);
        manualBrightness.setProgress(display.brightness - 1);
        add(root, manualBrightness, 9);
        nightStartField = field("Début HH:mm", display.nightStart);
        nightEndField = field("Fin HH:mm", display.nightEnd);
        luxField = field("Seuil de luminosité (lux)", String.valueOf(display.nightLux));
        luxEntityField = field("sensor.illuminance (facultatif)", display.luxEntityId);
        add(root, text("Début / fin du mode nuit", 15, Color.WHITE), 3);
        add(root, nightStartField, 5);
        add(root, nightEndField, 7);
        add(root, text("Seuil lux et entité HA optionnelle", 15, Color.WHITE), 3);
        add(root, luxField, 5);
        add(root, luxEntityField, 8);
        SensorManagerStatus(root);

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

        add(root, text("MISE À JOUR APK SÉCURISÉE", 19, 0xFF6BA8FF), 9);
        add(root, text("Copie candidate.apk dans Android/data/dev.maelab.haframe/files/updates/ ; " +
            "l’APK doit utiliser la même signature et un numéro de version supérieur. " +
            "Aucune installation automatique : confirmation sur le cadre obligatoire.",
            13, 0xFF91A1B5), 6);
        Button updateApk = new Button(this);
        updateApk.setText("VÉRIFIER UN APK ET OUVRIR L’INSTALLATEUR");
        updateApk.setOnClickListener(v -> verifyUpdate());
        add(root, updateApk, 18);

        add(root, text("PAGES ET CARTES", 20, 0xFF6BA8FF), 10);
        add(root, text("Gère les pages, les capteurs, les commandes et la disposition depuis le serveur web. " +
            "Le dashboard reste une application Android native sans WebView.", 14, 0xFF91A1B5), 18);
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

    private void verifyUpdate() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final UpdateVerifier.Candidate candidate=UpdateVerifier.verify(SettingsActivity.this);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            new android.app.AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("APK signé et vérifié : " + candidate.version)
                                .setMessage("Empreinte SHA-256 :\\n" + candidate.sha256 +
                                    "\\n\\nL’installation demandera une confirmation Android.")
                                .setPositiveButton("Installer", new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(android.content.DialogInterface dialog,int which) {
                                        try { UpdateVerifier.launchInstaller(SettingsActivity.this,candidate); }
                                        catch (Exception exc) {
                                            Toast.makeText(SettingsActivity.this,
                                                "Installation indisponible : "+exc.getClass().getSimpleName(),
                                                Toast.LENGTH_LONG).show();
                                        }
                                    }
                                })
                                .setNegativeButton("Annuler",null)
                                .show();
                        }
                    });
                } catch (Exception error) {
                    final String message=error.getClass().getSimpleName()+": "+error.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(SettingsActivity.this,
                                "APK refusé : "+message,Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, "haframe-verify-update").start();
    }

    private void SensorManagerStatus(LinearLayout root) {
        android.hardware.SensorManager sm = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
        boolean available = sm != null && sm.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) != null;
        add(root, text(available ? "Capteur ambiant Android détecté" : "Pas de capteur Android : choisir un capteur HA ou le secours horaire",
            13, 0xFF91A1B5), 14);
    }

    private void toggleIntegration() {
        DisplayPrefs display = DisplayPrefs.load(this);
        display.integrationEnabled = !display.integrationEnabled;
        display.save(this);
        if (display.integrationEnabled) {
            try { IntegrationApiServer.start(this); }
            catch (Exception e) {
                display.integrationEnabled = false;
                display.save(this);
                Toast.makeText(this, "API indisponible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else IntegrationApiServer.stop();
        updateIntegrationStatus();
    }

    private void issuePin() {
        IntegrationApiServer api = IntegrationApiServer.active();
        if (api == null) { Toast.makeText(this, "Activer l'intégration d'abord", Toast.LENGTH_SHORT).show(); return; }
        haStatus.setText("Adresse : " + api.url() + "\nCode de jumelage : " + api.newPin() + "\nValide pendant 2 minutes et 5 essais maximum");
    }

    private void updateIntegrationStatus() {
        if (haToggle == null || haStatus == null) return;
        IntegrationApiServer api = IntegrationApiServer.active();
        haToggle.setText(api == null ? "ACTIVER L'INTÉGRATION HA" : "DÉSACTIVER L'INTÉGRATION HA");
        pairButton.setEnabled(api != null);
        haStatus.setText(api == null ? "API locale désactivée" : "API active : " + api.url() + " (jumelage requis)");
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
        FrameConfig next = FrameConfig.load(this);
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
        try {
            if (!inputToken.isEmpty()) TokenVault.save(this, inputToken);
            else TokenVault.load(this);
        } catch (Exception e) {
            Toast.makeText(this, "Keystore indisponible : " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            next.save(this);
        } catch (Exception e) {
            Toast.makeText(this, "Configuration invalide : " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            DisplayPrefs display = DisplayPrefs.load(this);
            display.autoBrightness = autoToggle.isChecked();
            display.nightEnabled = nightToggle.isChecked();
            display.timeFallback = fallbackToggle.isChecked();
            display.brightness = manualBrightness.getProgress() + 1;
            display.nightStart = nightStartField.getText().toString().trim();
            display.nightEnd = nightEndField.getText().toString().trim();
            display.nightLux = Integer.parseInt(luxField.getText().toString());
            display.luxEntityId = luxEntityField.getText().toString().trim();
            display.save(this);
        } catch (Exception e) {
            Toast.makeText(this, "Affichage invalide : " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Configuration enregistrée", Toast.LENGTH_SHORT).show();
        finish();
    }
}
