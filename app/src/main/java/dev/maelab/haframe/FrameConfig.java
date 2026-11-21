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
        String progressEntity = "";
        String remainingEntity = "";
        float min = 0;
        float max = 100;
        int colSpan = 1;
        int rowSpan = 1;
        String background = "";
        String foreground = "";
        String accent = "";
        String icon = "";
        String iconPath = "";
        int radius = -1;
        int historyHours = 24;
        JSONObject rule = null;
        String tapAction = "default";
        String longPress = "none";

        Card(String type, String label, String entityId) {
            this.type = type;
            this.label = label;
            this.entityId = entityId;
        }

        JSONObject toJson() throws Exception {
            JSONObject item = new JSONObject().put("type", type).put("label", label).put("entity_id", entityId)
                .put("min", min).put("max", max);
            if (colSpan != 1) item.put("col_span", colSpan);
            if (rowSpan != 1) item.put("row_span", rowSpan);
            JSONObject style = new JSONObject();
            if (!background.isEmpty()) style.put("background", background);
            if (!foreground.isEmpty()) style.put("foreground", foreground);
            if (!accent.isEmpty()) style.put("accent", accent);
            if (radius >= 0) style.put("radius", radius);
            if (style.length() > 0) item.put("style", style);
            if (!icon.isEmpty()) item.put("icon", icon);
            if (!iconPath.isEmpty()) item.put("icon_path", iconPath);
            if (historyHours != 24) item.put("history_hours", historyHours);
            if (!progressEntity.isEmpty()) item.put("progress_entity", progressEntity);
            if (!remainingEntity.isEmpty()) item.put("remaining_entity", remainingEntity);
            if (rule != null) item.put("rule", rule);
            if (!"default".equals(tapAction)) item.put("tap_action", tapAction);
            if (!"none".equals(longPress)) item.put("long_press", longPress);
            return item;
        }
    }

    static final class Page {
        String title;
        int columns;
        final ArrayList<Card> cards = new ArrayList<>();
        JSONObject activateWhen = null;

        Page(String title, int columns) {
            this.title = title;
            this.columns = columns;
        }

        int[] columnSpans() {
            int[] result = new int[cards.size()];
            for (int i = 0; i < cards.size(); i++) result[i] = cards.get(i).colSpan;
            return result;
        }

        int[] rowSpans() {
            int[] result = new int[cards.size()];
            for (int i = 0; i < cards.size(); i++) result[i] = cards.get(i).rowSpan;
            return result;
        }

        TileGrid.Layout layout() {
            return TileGrid.place(columns, columnSpans(), rowSpans());
        }

        JSONObject toJson() throws Exception {
            JSONArray items = new JSONArray();
            for (Card card : cards) items.put(card.toJson());
            JSONObject item = new JSONObject().put("title", title).put("columns", columns).put("cards", items);
            if (activateWhen != null) item.put("activate_when", activateWhen);
            return item;
        }
    }

    String baseUrl = "";
    String title = "HA FRAME";
    String customCss = "";
    JSONObject slideshow = defaultSlideshow();
    final ArrayList<Page> pages = new ArrayList<>();

    static JSONObject defaultSlideshow() {
        try {
            return new JSONObject().put("enabled", false).put("idle_seconds", 300)
                .put("interval_seconds", 30).put("show_clock", true)
                .put("source", "local").put("camera_entity", "")
                .put("transition_ms", 800).put("quiet_start", "").put("quiet_end", "");
        } catch (Exception ignored) { return new JSONObject(); }
    }

    void setSlideshow(JSONObject slideshow) throws Exception {
        if (slideshow == null || slideshow.length() > 9)
            throw new IllegalArgumentException("Invalid slideshow");
        java.util.Iterator<String> keys = slideshow.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!("enabled".equals(key) || "idle_seconds".equals(key) ||
                "interval_seconds".equals(key) || "show_clock".equals(key) ||
                "source".equals(key) || "camera_entity".equals(key) ||
                "transition_ms".equals(key) || "quiet_start".equals(key) || "quiet_end".equals(key))) {
                throw new IllegalArgumentException("Unknown slideshow setting");
            }
        }
        if (!(slideshow.opt("enabled") instanceof Boolean) ||
            !(slideshow.opt("show_clock") instanceof Boolean) ||
            !(slideshow.opt("idle_seconds") instanceof Number) ||
            !(slideshow.opt("interval_seconds") instanceof Number)) {
            throw new IllegalArgumentException("Invalid slideshow value");
        }
        int idle = slideshow.getInt("idle_seconds");
        int interval = slideshow.getInt("interval_seconds");
        String source = slideshow.optString("source","local");
        String entity = slideshow.optString("camera_entity","");
        if (idle < 30 || idle > 3600 || interval < 5 || interval > 300 ||
            !("local".equals(source) || "camera".equals(source) || "image".equals(source)) ||
            (!entity.isEmpty() && !entity.matches("(?:camera|image)\\.[a-z0-9_]+")) ||
            (!"local".equals(source) && entity.isEmpty())) {
            throw new IllegalArgumentException("Invalid slideshow source or timing");
        }
        int transition=slideshow.optInt("transition_ms",800);
        String quietStart=slideshow.optString("quiet_start","");
        String quietEnd=slideshow.optString("quiet_end","");
        if(transition<0 || transition>2500 ||
            !quietStart.matches("(?:[01]\\d|2[0-3]):[0-5]\\d|") ||
            !quietEnd.matches("(?:[01]\\d|2[0-3]):[0-5]\\d|") ||
            quietStart.isEmpty()!=quietEnd.isEmpty())
            throw new IllegalArgumentException("Invalid slideshow transition or quiet hours");
        this.slideshow = new JSONObject(slideshow.toString());
    }

    static FrameConfig load(Context context) {
        SharedPreferences p = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        FrameConfig config = new FrameConfig();
        config.baseUrl = p.getString("url", "").trim();
        config.title = p.getString("dashboard_title", "HA FRAME");
        config.customCss = p.getString("custom_css", "");
        try { config.setSlideshow(new JSONObject(p.getString("slideshow", config.slideshow.toString()))); }
        catch (Exception ignored) { config.slideshow = defaultSlideshow(); }
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

    private static String styleColor(JSONObject style, String key) throws Exception {
        if (style == null || !style.has(key)) return "";
        String value = style.getString(key);
        if (!value.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Invalid tile style color: " + key);
        }
        return value;
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
            page.activateWhen = source.optJSONObject("activate_when");
            if (source.has("activate_when") && page.activateWhen == null)
                throw new IllegalArgumentException("Invalid page automation");
            DashboardRules.validate(page.activateWhen, false);
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
                     !"thermostat".equals(type) && !"light".equals(type) && !"cover".equals(type) &&
                     !"weather".equals(type) && !"graph".equals(type) && !"camera".equals(type) &&
                     !"calendar".equals(type) && !"todo".equals(type) && !"bambu".equals(type) &&
                     !"energy".equals(type) && !"air_quality".equals(type) && !"presence".equals(type) &&
                     !"circular_gauge".equals(type) && !"history".equals(type)) ||
                    label.length() > 48 ||
                    (!entity.isEmpty() && !entity.matches("[a-z0-9_]+\\.[a-z0-9_]+"))) {
                    throw new IllegalArgumentException("Carte invalide : " + (k + 1));
                }
                Card card = new Card(type, label, entity);
                card.min = (float) item.optDouble("min", 0);
                card.max = (float) item.optDouble("max", 100);
                card.progressEntity = item.optString("progress_entity","");
                card.remainingEntity = item.optString("remaining_entity","");
                if (!card.progressEntity.isEmpty() &&
                    !card.progressEntity.matches("[a-z0-9_]+\\.[a-z0-9_]+") ||
                    !card.remainingEntity.isEmpty() &&
                    !card.remainingEntity.matches("[a-z0-9_]+\\.[a-z0-9_]+"))
                    throw new IllegalArgumentException("Invalid Bambu entity link");
                if (!"bambu".equals(type) &&
                    (!card.progressEntity.isEmpty() || !card.remainingEntity.isEmpty()))
                    throw new IllegalArgumentException("Entity links are only for Bambu widgets");
                card.historyHours = item.optInt("history_hours", 24);
                if (card.historyHours != 1 && card.historyHours != 24 && card.historyHours != 168)
                    throw new IllegalArgumentException("Invalid history period");
                card.tapAction = item.optString("tap_action", "default");
                card.longPress = item.optString("long_press", "none");
                if (!DashboardRules.validTap(card.tapAction) || !DashboardRules.validLongPress(card.longPress))
                    throw new IllegalArgumentException("Invalid tile action");
                card.rule = item.optJSONObject("rule");
                if (item.has("rule") && card.rule == null)
                    throw new IllegalArgumentException("Invalid tile rule");
                DashboardRules.validate(card.rule, true);
                if ((Float.isNaN(card.min) || Float.isInfinite(card.min)) ||
                    (Float.isNaN(card.max) || Float.isInfinite(card.max)) || card.max <= card.min ||
                    card.max - card.min > 1000000f) throw new IllegalArgumentException("Invalid gauge range");
                card.icon = item.optString("icon", "");
                card.iconPath = item.optString("icon_path", "");
                if (!TileIcon.supported(card.icon)) {
                    throw new IllegalArgumentException("Invalid MDI icon");
                }
                if (!TileIcon.validPath(card.iconPath)) {
                    throw new IllegalArgumentException("Invalid MDI path");
                }
                if (!card.iconPath.isEmpty() && !card.icon.startsWith("mdi:") &&
                    !card.icon.isEmpty() && !"none".equals(card.icon)) {
                    throw new IllegalArgumentException("MDI path with legacy icon");
                }
                if (card.icon.startsWith("mdi:") && card.iconPath.isEmpty()) {
                    throw new IllegalArgumentException("MDI icon is missing path data");
                }
                if ("camera".equals(type) && !entity.isEmpty() && !entity.startsWith("camera.")) {
                    throw new IllegalArgumentException("Camera cards require a camera entity");
                }
                card.colSpan = item.optInt("col_span", 1);
                card.rowSpan = item.optInt("row_span", 1);
                if (card.colSpan < 1 || card.colSpan > columns || card.rowSpan < 1 || card.rowSpan > 2) {
                    throw new IllegalArgumentException("Invalid tile size");
                }
                JSONObject style = item.optJSONObject("style");
                if (item.has("style") && style == null) {
                    throw new IllegalArgumentException("Invalid tile style");
                }
                card.background = styleColor(style, "background");
                card.foreground = styleColor(style, "foreground");
                card.accent = styleColor(style, "accent");
                if (style != null && style.has("radius")) {
                    card.radius = style.getInt("radius");
                    if (card.radius < 0 || card.radius > 72) {
                        throw new IllegalArgumentException("Invalid tile corner radius");
                    }
                }
                page.cards.add(card);
            }
            int cameraCount = 0;
            for (Card card : page.cards) if ("camera".equals(card.type)) cameraCount++;
            if (cameraCount > 1) throw new IllegalArgumentException("One camera per page maximum");
            TileGrid.place(columns, page.columnSpans(), page.rowSpans());
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
            .putString("slideshow", slideshow.toString())
            .putString(LAYOUT_KEY, saved.toString())
            .apply();
    }

    String[] subscribedEntityIds() {
        Set<String> unique = new LinkedHashSet<>();
        for (Page page : pages) {
            if (page.activateWhen != null) unique.add(page.activateWhen.optString("entity_id"));
            for (Card card : page.cards) {
                if (!card.entityId.isEmpty()) unique.add(card.entityId);
                if (!card.progressEntity.isEmpty()) unique.add(card.progressEntity);
                if (!card.remainingEntity.isEmpty()) unique.add(card.remainingEntity);
                if (card.rule != null) unique.add(card.rule.optString("entity_id"));
            }
        }
        if ("camera".equals(slideshow.optString("source")) &&
            slideshow.optBoolean("enabled", false)) {
            unique.add(slideshow.optString("camera_entity", ""));
        }
        unique.remove("");
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
