package dev.maelab.haframe;

import org.json.JSONObject;

/** Restricted, declarative Home Assistant state predicates (never executes code). */
final class DashboardRules {
    static boolean validTap(String value) {
        return "default".equals(value) || "none".equals(value) ||
            "page_next".equals(value) || "page_prev".equals(value) ||
            "settings".equals(value) || "camera_fullscreen".equals(value);
    }

    static boolean validLongPress(String value) {
        return "none".equals(value) || "toggle".equals(value) ||
            "page_next".equals(value) || "settings".equals(value);
    }

    static void validate(JSONObject rule, boolean visual) throws Exception {
        if (rule == null) return;
        String entity = rule.optString("entity_id", "");
        String op = rule.optString("operator", "");
        String value = rule.optString("value", "");
        if ((!"time_between".equals(op) && !entity.matches("[a-z0-9_]+\\.[a-z0-9_]+")) ||
            ("time_between".equals(op) && !entity.isEmpty() &&
                !entity.matches("[a-z0-9_]+\\.[a-z0-9_]+")) ||
            value.length() > 64 ||
            !("equals".equals(op) || "not_equals".equals(op) || "above".equals(op) ||
                "below".equals(op) || "on".equals(op) || "off".equals(op) ||
                "time_between".equals(op))) {
            throw new IllegalArgumentException("Invalid rule");
        }
        java.util.Iterator<String> keys = rule.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("entity_id".equals(key) || "operator".equals(key) || "value".equals(key)) continue;
            if (visual && "icon".equals(key) && TileIcon.supported(rule.optString("icon", ""))) continue;
            if (visual && "icon_path".equals(key) &&
                TileIcon.validPath(rule.optString("icon_path", ""))) continue;
            if (visual && "hide".equals(key) && rule.opt("hide") instanceof Boolean) continue;
            if (visual && "background".equals(key) &&
                rule.optString("background").matches("#[0-9a-fA-F]{6}")) continue;
            throw new IllegalArgumentException("Unsupported rule key");
        }
        if (visual && rule.has("icon")) {
            String icon = rule.optString("icon","");
            String path = rule.optString("icon_path","");
            if (icon.startsWith("mdi:") && path.isEmpty())
                throw new IllegalArgumentException("Rule MDI icon path required");
            if (!path.isEmpty() && !icon.startsWith("mdi:"))
                throw new IllegalArgumentException("Rule icon and path mismatch");
        }
        if (("equals".equals(op) || "not_equals".equals(op)) && value.isEmpty()) {
            throw new IllegalArgumentException("Empty comparison");
        }
        if ("time_between".equals(op) &&
            !value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d-(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException("Invalid time window");
        }
        if ("above".equals(op) || "below".equals(op)) {
            if (!value.matches("-?\\d{1,6}(?:\\.\\d{1,3})?"))
                throw new IllegalArgumentException("Invalid numeric rule");
            double threshold = Double.parseDouble(value);
            if (Double.isNaN(threshold) || Double.isInfinite(threshold))
                throw new IllegalArgumentException("Nonfinite threshold");
        }
    }

    static boolean matches(JSONObject rule, String actual) {
        if (rule == null) return false;
        String op = rule.optString("operator", "");
        if ("time_between".equals(op)) {
            String range = rule.optString("value", "");
            if (!range.matches("(?:[01]\\d|2[0-3]):[0-5]\\d-(?:[01]\\d|2[0-3]):[0-5]\\d"))
                return false;
            String[] parts = range.split("-");
            int start = Integer.parseInt(parts[0].substring(0,2))*60 +
                Integer.parseInt(parts[0].substring(3,5));
            int end = Integer.parseInt(parts[1].substring(0,2))*60 +
                Integer.parseInt(parts[1].substring(3,5));
            java.util.Calendar now = java.util.Calendar.getInstance();
            int minute = now.get(java.util.Calendar.HOUR_OF_DAY)*60 +
                now.get(java.util.Calendar.MINUTE);
            return start <= end ? minute >= start && minute < end :
                minute >= start || minute < end;
        }
        if (actual == null || "unknown".equals(actual) || "unavailable".equals(actual)) return false;
        String wanted = rule.optString("value", "");
        if ("equals".equals(op)) return wanted.equals(actual);
        if ("not_equals".equals(op)) return !wanted.equals(actual);
        if ("on".equals(op)) return "on".equals(actual) || "home".equals(actual);
        if ("off".equals(op)) return "off".equals(actual) || "not_home".equals(actual);
        if ("above".equals(op) || "below".equals(op)) {
            try {
                double n = Double.parseDouble(actual);
                double compare = Double.parseDouble(wanted);
                return !Double.isNaN(n) && !Double.isInfinite(n) &&
                    ("above".equals(op) ? n > compare : n < compare);
            } catch (NumberFormatException ignored) { return false; }
        }
        return false;
    }

    private DashboardRules() {}
}
