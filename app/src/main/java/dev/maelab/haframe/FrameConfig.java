package dev.maelab.haframe;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

final class FrameConfig {
    static final int METRICS = 3;
    static final int CONTROLS = 6;
    static final int COUNT = METRICS + CONTROLS;

    static final String[] DEFAULT_LABELS = {
        "Température", "Humidité", "Puissance",
        "Salon", "Cuisine", "Bureau", "Garage", "Volets", "Ambiance"
    };

    String baseUrl = "";
    String title = "HA FRAME";
    String customCss = "";
    String[] labels = DEFAULT_LABELS.clone();
    String[] ids = new String[COUNT];

    FrameConfig() {
        for (int i = 0; i < COUNT; i++) ids[i] = "";
    }

    static FrameConfig load(Context context) {
        SharedPreferences p = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        FrameConfig config = new FrameConfig();
        config.baseUrl = p.getString("url", "").trim();
        config.title = p.getString("dashboard_title", "HA FRAME");
        config.customCss = p.getString("custom_css", "");
        for (int i = 0; i < COUNT; i++) {
            config.labels[i] = p.getString("label_" + i, DEFAULT_LABELS[i]).trim();
            config.ids[i] = p.getString("entity_" + i, "").trim();
        }
        return config;
    }

    void save(Context context) {
        SharedPreferences.Editor edit = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("url", baseUrl)
            .putString("dashboard_title", title)
            .putString("custom_css", customCss);
        for (int i = 0; i < COUNT; i++) {
            edit.putString("label_" + i, labels[i]);
            edit.putString("entity_" + i, ids[i]);
        }
        edit.apply();
    }

    String[] subscribedEntityIds() {
        Set<String> unique = new LinkedHashSet<>();
        for (String entity : ids) if (!entity.isEmpty()) unique.add(entity);
        return unique.toArray(new String[0]);
    }

    String restStatesUrl() {
        String ws = webSocketUrl();
        try {
            URI parsed = new URI(ws);
            String scheme = "wss".equals(parsed.getScheme()) ? "https" : "http";
            String path = parsed.getRawPath();
            String prefix = path.substring(0, path.length() - "/api/websocket".length());
            return scheme + "://" + parsed.getRawAuthority() + prefix + "/api/states";
        } catch (Exception e) {
            throw new IllegalArgumentException("URL Home Assistant invalide");
        }
    }

    String webSocketUrl() {
        String input = baseUrl.trim();
        if (input.isEmpty()) throw new IllegalArgumentException("Adresse Home Assistant manquante");
        if (!input.contains("://")) input = "http://" + input;
        URI parsed;
        try {
            parsed = new URI(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("URL Home Assistant invalide");
        }
        String scheme = parsed.getScheme();
        String protocol;
        if ("http".equalsIgnoreCase(scheme)) protocol = "ws";
        else if ("https".equalsIgnoreCase(scheme)) protocol = "wss";
        else if ("ws".equalsIgnoreCase(scheme)) protocol = "ws";
        else if ("wss".equalsIgnoreCase(scheme)) protocol = "wss";
        else throw new IllegalArgumentException("Protocole non pris en charge");
        if (parsed.getHost() == null || parsed.getRawUserInfo() != null || parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
            throw new IllegalArgumentException("Utiliser uniquement l'URL de Home Assistant");
        }
        String path = parsed.getRawPath() == null ? "" : parsed.getRawPath().replaceAll("/+$", "");
        if (!path.endsWith("/api/websocket")) path += "/api/websocket";
        return protocol + "://" + parsed.getRawAuthority() + path;
    }
}
