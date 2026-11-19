package dev.maelab.haframe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Calendar;

/** Independent display settings: updating these never overwrites layout or HA credentials. */
final class DisplayPrefs {
    boolean screenEnabled = true;
    boolean nightEnabled = false;
    boolean autoBrightness = false;
    boolean timeFallback = false;
    boolean integrationEnabled = false;
    int brightness = 75;
    int autoMin = 10;
    int autoMax = 100;
    int nightLux = 5;
    String nightStart = "22:00";
    String nightEnd = "07:00";
    String luxEntityId = "";

    static DisplayPrefs load(Context context) {
        SharedPreferences p = context.getSharedPreferences("display", Context.MODE_PRIVATE);
        DisplayPrefs v = new DisplayPrefs();
        v.screenEnabled = p.getBoolean("screen_enabled", true);
        v.nightEnabled = p.getBoolean("night_enabled", false);
        v.autoBrightness = p.getBoolean("auto_brightness", false);
        v.timeFallback = p.getBoolean("time_fallback", false);
        v.integrationEnabled = p.getBoolean("integration_enabled", false);
        v.brightness = p.getInt("brightness", 75);
        v.autoMin = p.getInt("auto_min", 10);
        v.autoMax = p.getInt("auto_max", 100);
        v.nightLux = p.getInt("night_lux", 5);
        v.nightStart = p.getString("night_start", "22:00");
        v.nightEnd = p.getString("night_end", "07:00");
        v.luxEntityId = p.getString("lux_entity_id", "");
        return v;
    }

    void update(JSONObject data) {
        if (data.has("screen_enabled")) screenEnabled = data.optBoolean("screen_enabled");
        if (data.has("night_enabled")) nightEnabled = data.optBoolean("night_enabled");
        if (data.has("auto_brightness")) autoBrightness = data.optBoolean("auto_brightness");
        if (data.has("time_fallback")) timeFallback = data.optBoolean("time_fallback");
        if (data.has("integration_enabled")) integrationEnabled = data.optBoolean("integration_enabled");
        if (data.has("brightness")) brightness = data.optInt("brightness", -1);
        if (data.has("auto_min")) autoMin = data.optInt("auto_min", -1);
        if (data.has("auto_max")) autoMax = data.optInt("auto_max", -1);
        if (data.has("night_lux")) nightLux = data.optInt("night_lux", -1);
        if (data.has("night_start")) nightStart = data.optString("night_start");
        if (data.has("night_end")) nightEnd = data.optString("night_end");
        if (data.has("lux_entity_id")) luxEntityId = data.optString("lux_entity_id").trim();
        validate();
    }

    void validate() {
        if (brightness < 1 || brightness > 100 || autoMin < 1 || autoMin > 100 || autoMax < autoMin || autoMax > 100)
            throw new IllegalArgumentException("Brightness must be between 1 and 100 (auto_min <= auto_max)");
        if (nightLux < 0 || nightLux > 10000) throw new IllegalArgumentException("Night lux threshold out of range");
        minutes(nightStart);
        minutes(nightEnd);
        if (!luxEntityId.isEmpty() && !luxEntityId.matches("[a-z0-9_]+\\.[a-z0-9_]+"))
            throw new IllegalArgumentException("Invalid illuminance entity_id");
    }

    private static int minutes(String value) {
        if (value == null || !value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
            throw new IllegalArgumentException("Time must be HH:mm");
        return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3));
    }

    boolean withinNightHours(Calendar now) {
        int start = minutes(nightStart);
        int end = minutes(nightEnd);
        int time = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return start == end ? true : start < end ? time >= start && time < end : time >= start || time < end;
    }

    void save(Context context) {
        validate();
        context.getSharedPreferences("display", Context.MODE_PRIVATE).edit()
            .putBoolean("screen_enabled", screenEnabled)
            .putBoolean("night_enabled", nightEnabled)
            .putBoolean("auto_brightness", autoBrightness)
            .putBoolean("time_fallback", timeFallback)
            .putBoolean("integration_enabled", integrationEnabled)
            .putInt("brightness", brightness)
            .putInt("auto_min", autoMin)
            .putInt("auto_max", autoMax)
            .putInt("night_lux", nightLux)
            .putString("night_start", nightStart)
            .putString("night_end", nightEnd)
            .putString("lux_entity_id", luxEntityId).apply();
    }

    JSONObject toJson() throws Exception {
        return new JSONObject()
            .put("screen_enabled", screenEnabled)
            .put("night_enabled", nightEnabled)
            .put("auto_brightness", autoBrightness)
            .put("time_fallback", timeFallback)
            .put("integration_enabled", integrationEnabled)
            .put("brightness", brightness)
            .put("auto_min", autoMin)
            .put("auto_max", autoMax)
            .put("night_lux", nightLux)
            .put("night_start", nightStart)
            .put("night_end", nightEnd)
            .put("lux_entity_id", luxEntityId);
    }
}
