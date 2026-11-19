package dev.maelab.haframe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

final class FrameConfig {
    static final int MAX_PAGES = 4;
    static final int MAX_CARDS_PER_PAGE = 12;
    private static final String LAYOUT_KEY = "dashboard_layout_v2";
    private static final String[] LEGACY_LABELS = {
        "Température", "Humidité", "Puissance", "Salon", "Cuisine", "Bureau", "Garage", "Volets", "Ambiance"
    };

    static final class Card {
        String type;
        String label;
        String entityId;
        float min = 0;
        float max = 100;

        Card(String type, String label, String entityId) {
            this.type = type;
            this.label = label;
            this.entityId = entityId;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("type", type).put("label", label).put("entity_id", entityId)
                .put("min", min).put("max", max);
        }
    }

    static final class Page {
        String title;
        int columns;
        final ArrayList<Card> cards = new ArrayList<>();

        Page(String title, int columns) {
            this.title = title;
            this.columns = columns;
        }

        JSONObject toJson() throws Exception {
            JSONArray items = new JSONArray();
            for (Card card : cards) items.put(card.toJson());
            return new JSONObject().put("title", title).put("columns", columns).put("cards", items);
        }
    }

    String baseUrl = "";
    String title = "HA FRAME";
    String customCss = "";
    final ArrayList<Page> pages = new ArrayList<>();

    static FrameConfig load(Context context) {
        SharedPreferences p = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        FrameConfig config = new FrameConfig();
        config.baseUrl = p.getString("url", "").trim();
        config.title = p.getString("dashboard_title", "HA FRAME");
        config.customCss = p.getString("custom_css", "");
        String savedLayout = p.getString(LAYOUT_KEY, "");
        if (!savedLayout.isEmpty()) {
            try {
                config.setPages(new JSONArray(savedLayout));
                return config;
            } catch (Exception ignored) {
                // Recover the legacy layout if stored JSON is corrupt.
                config.pages.clear();
            }
        }
        Page legacy = new Page("Maison", 3);
        for (int i = 0; i < LEGACY_LABELS.length; i++) {
            legacy.cards.add(new Card(i < 3 ? "metric" : "control",
                p.getString("label_" + i, LEGACY_LABELS[i]).trim(),
                p.getString("entity_" + i, "").trim()));
        }
        config.pages.add(legacy);
        return config;
    }

    void setPages(JSONArray raw) throws Exception {
        if (raw.length() < 1 || raw.length() > MAX_PAGES) {
            throw new IllegalArgumentException("Nombre de pages invalide (1 à 4)");
        }
        ArrayList<Page> parsed = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject source = raw.getJSONObject(i);
            String name = source.getString("title").trim();
            int columns = source.getInt("columns");
            if (name.isEmpty() || name.length() > 32 || columns < 2 || columns > 4) {
                throw new IllegalArgumentException("Page invalide : titre ou colonnes");
            }
            Page page = new Page(name, columns);
            JSONArray cards = source.getJSONArray("cards");
            if (cards.length() > Math.min(MAX_CARDS_PER_PAGE, columns * 3)) {
                throw new IllegalArgumentException("Maximum 3 rangées par page (colonnes × 3 cartes)");
            }
            for (int k = 0; k < cards.length(); k++) {
                JSONObject item = cards.getJSONObject(k);
                String type = item.getString("type");
                String label = item.getString("label").trim();
                String entity = item.getString("entity_id").trim();
                if ((!"metric".equals(type) && !"control".equals(type) && !"gauge".equals(type) &&
                     !"thermostat".equals(type) && !"light".equals(type) && !"cover".equals(type)) ||
                    label.length() > 48 ||
                    (!entity.isEmpty() && !entity.matches("[a-z0-9_]+\\.[a-z0-9_]+"))) {
                    throw new IllegalArgumentException("Carte invalide : " + (k + 1));
                }
                Card card = new Card(type, label, entity);
                card.min = (float) item.optDouble("min", 0);
                card.max = (float) item.optDouble("max", 100);
                if ((Float.isNaN(card.min) || Float.isInfinite(card.min)) ||
                    (Float.isNaN(card.max) || Float.isInfinite(card.max)) || card.max <= card.min ||
                    card.max - card.min > 1000000f) throw new IllegalArgumentException("Invalid gauge range");
                page.cards.add(card);
            }
            parsed.add(page);
        }
        pages.clear();
        pages.addAll(parsed);
    }

    JSONArray pagesJson() throws Exception {
        JSONArray result = new JSONArray();
        for (Page page : pages) result.put(page.toJson());
        return result;
    }

    void save(Context context) throws Exception {
        JSONArray saved = pagesJson();
        // Validate before committing any settings.
        FrameConfig validation = new FrameConfig();
        validation.setPages(saved);
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("url", baseUrl)
            .putString("dashboard_title", title)
            .putString("custom_css", customCss)
            .putString(LAYOUT_KEY, saved.toString())
            .apply();
    }

    String[] subscribedEntityIds() {
        Set<String> unique = new LinkedHashSet<>();
        for (Page page : pages) for (Card card : page.cards) {
            if (!card.entityId.isEmpty()) unique.add(card.entityId);
        }
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
