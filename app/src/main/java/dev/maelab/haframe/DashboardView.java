package dev.maelab.haframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Software-drawn dashboard. Only the visible page is rendered. */
final class DashboardView extends View {
    interface Callbacks {
        void settings();
        void command(String id);
        void retryConnection();
        void level(String entityId, int percent);
        void temperature(String entityId, double value);
        void wakeDisplay();
    }

    private static final int BG = 0xFF10151E;
    private static final int PANEL = 0xFF1B2432;
    private static final int PANEL_ON = 0xFF213D3C;
    private static final int PANEL_PRESSED = 0xFF33584E;
    private static final int TEXT = 0xFFF5F7FB;
    private static final int MUTED = 0xFF94A3B8;
    private static final int ACCENT = 0xFF67DBBA;
    private static final float GRID_X = 50f;
    private static final float GRID_Y = 276f;
    private static final float GRID_WIDTH = 1180f;
    private static final float GRID_HEIGHT = 472f;
    private static final float CELL_GAP = 15f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.FRANCE);
    private final SimpleDateFormat date = new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE);
    private final RectF bounds = new RectF();
    private FrameConfig config = new FrameConfig();
    private CanvasTheme theme = CanvasTheme.parse("");
    private HaClient client;
    private Callbacks callbacks;
    private String status = "Configuration nécessaire";
    private boolean connected;
    private boolean sleeping;
    private String notificationTitle = "";
    private String notificationMessage = "";
    private long notificationUntil;
    private final Runnable endNotification = () -> { notificationUntil = 0; invalidate(); };
    private float scale = 1f;
    private float dx;
    private float dy;
    private int pageIndex;
    private int touchedCard = -1;
    private int feedbackCard = -1;
    private float startX;
    private float startY;
    private String lastCommandId = "";
    private long lastCommandAt;
    private final Runnable endFeedback = () -> { feedbackCard = -1; invalidate(); };
    private final Runnable refreshClock = new Runnable() {
        @Override public void run() {
            invalidate();
            long next = 60000L - (System.currentTimeMillis() % 60000L);
            postDelayed(this, Math.max(250L, next + 100L));
        }
    };

    DashboardView(Context context) { super(context); }
    void setCallbacks(Callbacks callbacks) { this.callbacks = callbacks; }
    void setConfig(FrameConfig config) {
        this.config = config;
        pageIndex = Math.max(0, Math.min(pageIndex, config.pages.size() - 1));
        touchedCard = -1;
        feedbackCard = -1;
        try { theme = CanvasTheme.parse(config.customCss); }
        catch (IllegalArgumentException ignored) { theme = CanvasTheme.parse(""); }
        refreshCamera();
        invalidate();
    }
    void setClient(HaClient client) { this.client = client; refreshCamera(); invalidate(); }
    void setSleeping(boolean sleep) { if (sleeping != sleep) { sleeping = sleep; refreshCamera(); invalidate(); } }
    void showNotification(String title, String message, int seconds) {
        notificationTitle = title;
        notificationMessage = message;
        notificationUntil = SystemClock.elapsedRealtime() + seconds * 1000L;
        removeCallbacks(endNotification);
        postDelayed(endNotification, seconds * 1000L);
        invalidate();
    }
    void setStatus(String status, boolean connected) {
        this.status = status;
        this.connected = connected;
        invalidate();
    }

    private void refreshCamera() {
        if (client == null) return;
        String selected = "";
        if (!sleeping && !config.pages.isEmpty()) {
            FrameConfig.Page page = config.pages.get(pageIndex);
            for (FrameConfig.Card card : page.cards) {
                if ("camera".equals(card.type) && card.entityId.startsWith("camera.")) {
                    selected = card.entityId;
                    break; // One stream per page avoids overloading Android 6 hardware.
                }
            }
        }
        client.selectCamera(selected);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshClock.run();
        refreshCamera();
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(refreshClock);
        if (client != null) client.selectCamera("");
        removeCallbacks(endFeedback);
        removeCallbacks(endNotification);
        super.onDetachedFromWindow();
    }

    private void rect(Canvas c, float x, float y, float w, float h, float radius, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        bounds.set(x, y, x + w, y + h);
        c.drawRoundRect(bounds, radius, radius, paint);
    }

    private void text(Canvas c, String content, float x, float y, float size,
                      int color, boolean bold, float maxWidth) {
        if (content == null) content = "";
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size);
        paint.setTypeface(bold ? MEDIUM : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        if (maxWidth > 0 && paint.measureText(content) > maxWidth) {
            final String ellipsis = "…";
            while (!content.isEmpty() && paint.measureText(content + ellipsis) > maxWidth) {
                content = content.substring(0, content.length() - 1);
            }
            content += ellipsis;
        }
        c.drawText(content, x, y, paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sleeping) { canvas.drawColor(0xFF000000); return; }
        canvas.drawColor(theme.color(BG, "background-color", "body"));
        scale = Math.min(getWidth() / 1280f, getHeight() / 800f);
        if (scale <= 0f) return;
        dx = (getWidth() - 1280f * scale) / 2f;
        dy = (getHeight() - 800f * scale) / 2f;
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        text(canvas, config.title, 50, 66, theme.size(36, "font-size", ".header"),
            theme.color(TEXT, "color", ".header"), theme.bold(true, ".header"), 580);
        text(canvas, "Tableau de bord", 52, 101, theme.size(19, "font-size", ".subtitle"),
            theme.color(MUTED, "color", ".subtitle"), false, 600);
        rect(canvas, 865, 35, 12, 12, 6, connected ? theme.color(ACCENT, "color", ".accent") : 0xFFFFB56A);
        text(canvas, (connected ? "" : "↻ ") + status, 890, 47,
            theme.size(17, "font-size", ".status"), theme.color(MUTED, "color", ".status"), false, 226);
        rect(canvas, 1130, 23, 105, 58, theme.size(18, "border-radius", ".settings"),
            theme.color(PANEL, "background-color", ".settings"));
        text(canvas, "RÉGLAGES", 1144, 60, 17, theme.color(TEXT, "color", ".settings"), true, 90);
        text(canvas, clock.format(new Date()), 52, 171,
            theme.size(40, "font-size", ".clock"), theme.color(TEXT, "color", ".clock"),
            theme.bold(true, ".clock"), 190);
        text(canvas, date.format(new Date()), 245, 165,
            theme.size(19, "font-size", ".date"), theme.color(MUTED, "color", ".date"), false, 470);

        if (config.pages.isEmpty()) {
            text(canvas, "Configurer une page depuis le serveur web", 54, 355, 26, MUTED, true, 1050);
        } else {
            drawTabs(canvas);
            drawCards(canvas, config.pages.get(pageIndex));
        }
        if (notificationUntil > SystemClock.elapsedRealtime()) drawNotification(canvas);
        canvas.restore();
    }

    private void drawTabs(Canvas canvas) {
        int count = config.pages.size();
        float width = (GRID_WIDTH - (count - 1) * 10f) / count;
        for (int i = 0; i < count; i++) {
            boolean active = i == pageIndex;
            float x = GRID_X + i * (width + 10);
            float radius = theme.size(14, "border-radius", ".page-tab", active ? ".page-tab.active" : ".page-tab");
            int background = theme.color(active ? PANEL_ON : PANEL, "background-color",
                ".page-tab", active ? ".page-tab.active" : ".page-tab");
            rect(canvas, x, 211, width, 46, radius, background);
            text(canvas, config.pages.get(i).title, x + 17, 240,
                theme.size(18, "font-size", ".page-tab", active ? ".page-tab.active" : ".page-tab"),
                theme.color(active ? ACCENT : MUTED, "color", ".page-tab", active ? ".page-tab.active" : ".page-tab"),
                active, width - 34);
        }
    }

    private int columns(FrameConfig.Page page) { return Math.max(2, Math.min(4, page.columns)); }
    private float cardWidth(FrameConfig.Page page) {
        return (GRID_WIDTH - (columns(page) - 1) * CELL_GAP) / columns(page);
    }
    private float cardHeight(FrameConfig.Page page) {
        int rows = page.layout().usedRows;
        return Math.min(183f, (GRID_HEIGHT - (rows - 1) * CELL_GAP) / rows);
    }
    private TileGrid.Cell tileCell(FrameConfig.Page page, int index) {
        return page.layout().cells[index];
    }
    private float cardLeft(FrameConfig.Page page, int index) {
        return GRID_X + tileCell(page, index).column * (cardWidth(page) + CELL_GAP);
    }
    private float cardTop(FrameConfig.Page page, int index) {
        return GRID_Y + tileCell(page, index).row * (cardHeight(page) + CELL_GAP);
    }
    private float tileWidth(FrameConfig.Page page, int index) {
        return tileCell(page, index).columns * cardWidth(page) +
            (tileCell(page, index).columns - 1) * CELL_GAP;
    }
    private float tileHeight(FrameConfig.Page page, int index) {
        return tileCell(page, index).rows * cardHeight(page) +
            (tileCell(page, index).rows - 1) * CELL_GAP;
    }

    private void drawNotification(Canvas canvas) {
        rect(canvas, 170, 235, 940, 330, 30, 0xFF0E2631);
        rect(canvas, 188, 250, 9, 290, 4, ACCENT);
        text(canvas, notificationTitle.isEmpty() ? "Notification Home Assistant" : notificationTitle,
            230, 322, 34, ACCENT, true, 820);
        String message = notificationMessage;
        for (int i = 0; i < 4 && !message.isEmpty(); i++) {
            int limit = Math.min(message.length(), 51);
            int cut = message.length() <= limit ? message.length() : message.lastIndexOf(' ', limit);
            if (cut <= 0) cut = limit;
            text(canvas, message.substring(0, cut).trim(), 230, 385 + i * 43, 27, TEXT, false, 830);
            message = message.substring(cut).trim();
        }
    }

    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }

    private static float parseNumber(String value, float fallback) {
        try { return Float.parseFloat(value); }
        catch (Exception e) { return fallback; }
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    private void bar(Canvas canvas, float x, float y, float width, float percent, int color) {
        rect(canvas, x, y, width, 12, 6, 0xFF394757);
        rect(canvas, x, y, Math.max(8, width * clamp(percent, 0, 100) / 100f), 12, 6, color);
    }

    private static int customColor(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        return Color.parseColor(hex);
    }

    private void drawTrend(Canvas canvas, float[] samples, float x, float y,
                           float width, float height, int color) {
        if (samples.length < 2 || width <= 0 || height <= 0) return;
        float low = samples[0], high = samples[0];
        for (float sample : samples) {
            low = Math.min(low, sample);
            high = Math.max(high, sample);
        }
        if (high - low < 0.001f) { high += 1f; low -= 1f; }
        Path path = new Path();
        for (int i = 0; i < samples.length; i++) {
            float px = x + width * i / (samples.length - 1);
            float py = y + height * (1f - (samples[i] - low) / (high - low));
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static String weatherLabel(String condition) {
        switch (condition) {
            case "sunny": return "Ensoleillé";
            case "clear-night": return "Ciel dégagé";
            case "partlycloudy": return "Éclaircies";
            case "cloudy": return "Nuageux";
            case "rainy": return "Pluie";
            case "pouring": return "Fortes pluies";
            case "snowy": return "Neige";
            case "snowy-rainy": return "Neige et pluie";
            case "lightning": return "Orage";
            case "fog": return "Brouillard";
            case "windy": return "Vent";
            default: return condition;
        }
    }

    private void drawCards(Canvas canvas, FrameConfig.Page page) {
        if (page.cards.isEmpty()) {
            text(canvas, "Cette page est vide", GRID_X + 18, GRID_Y + 75, 27, MUTED, false, 700);
            return;
        }
        int metricNumber = 0, controlNumber = 0;
        for (int i = 0; i < page.cards.size(); i++) {
            FrameConfig.Card card = page.cards.get(i);
            boolean metric = "metric".equals(card.type);
            boolean actionable = !metric && !"gauge".equals(card.type) &&
                !"graph".equals(card.type) && !"weather".equals(card.type) && !"camera".equals(card.type);
            String slot = metric ? ".metric-" + (++metricNumber) : ".control-" + (++controlNumber);
            float x = cardLeft(page, i), y = cardTop(page, i);
            float w = tileWidth(page, i), h = tileHeight(page, i);
            HaClient.EntityState state = card.entityId.isEmpty() || client == null ? null : client.get(card.entityId);
            boolean valid = connected && state != null && !"unavailable".equals(state.state) && !"unknown".equals(state.state);
            boolean on = valid && ("on".equals(state.state) || "open".equals(state.state) || "playing".equals(state.state));
            boolean pressed = actionable && (touchedCard == i || feedbackCard == i);
            String category = "." + card.type;
            String modifier = pressed ? ".control.pressed" : on ? ".control.on" : category;
            int background = theme.color(pressed ? PANEL_PRESSED : on ? PANEL_ON : PANEL,
                "background-color", ".card", category, slot, modifier);
            int foreground = customColor(card.foreground, theme.color(TEXT, "color", ".metric-value", slot));
            int secondary = customColor(card.foreground, theme.color(MUTED, "color", ".metric-label", category, slot));
            int accent = customColor(card.accent, theme.color(ACCENT, "color", ".accent"));
            float radius = card.radius >= 0 ? card.radius :
                theme.size(20, "border-radius", ".card", category, slot);
            rect(canvas, x, y, w, h, radius, customColor(card.background, background));
            canvas.save();
            canvas.clipRect(x + 1, y + 1, x + w - 1, y + h - 1);
            String icon = TileIcon.effective(card.icon, card.type);
            TileIcon.draw(canvas, paint, icon, card.type, x + w - 52, y + 15, 28, accent);
            text(canvas, card.label.isEmpty() ? card.entityId : card.label, x + 20, y + 39,
                theme.size(20, "font-size", ".metric-label", category, slot),
                secondary, false, w - (icon.isEmpty() ? 40 : 83));
            String unavailable = card.entityId.isEmpty() ? "À configurer" : !connected ? "Hors ligne" : "En attente…";
            if (!valid) {
                text(canvas, unavailable, x + 20, y + h - 27, 21, secondary, true, w - 40);
                canvas.restore();
                continue;
            }
            if (metric) {
                String value = state.state + (state.unit().isEmpty() ? "" : " " + state.unit());
                text(canvas, value, x + 20, y + h - 26,
                    theme.size(34, "font-size", ".metric-value", slot),
                    foreground, true, w - 40);
            } else if ("gauge".equals(card.type)) {
                float value = parseNumber(state.state, Float.NaN);
                if (!finite(value)) { text(canvas, "Non numérique", x + 20, y + h - 26, 20, secondary, false, w - 40); canvas.restore(); continue; }
                text(canvas, state.state + (state.unit().isEmpty() ? "" : " " + state.unit()),
                    x + 20, y + 94, 35, TEXT, true, w - 40);
                float pct = 100f * (value - card.min) / (card.max - card.min);
                bar(canvas, x + 20, y + h - 32, w - 40, pct, accent);
            } else if ("camera".equals(card.type)) {
                Bitmap frame = client == null ? null : client.cameraFrame(card.entityId);
                if (frame != null && !frame.isRecycled()) {
                    float left = x + 12, top = y + 47, boxW = w - 24, boxH = h - 60;
                    float factor = Math.min(boxW / frame.getWidth(), boxH / frame.getHeight());
                    float videoW = frame.getWidth() * factor;
                    float videoH = frame.getHeight() * factor;
                    paint.setColor(0xFFFFFFFF);
                    paint.setFilterBitmap(true);
                    canvas.drawBitmap(frame, null, new RectF(left + (boxW-videoW)/2f,
                        top + (boxH-videoH)/2f,
                        left + (boxW+videoW)/2f,
                        top + (boxH+videoH)/2f), paint);
                } else {
                    text(canvas, "Connexion caméra…", x + 20, y + h/2f,
                        21, secondary, false, w - 40);
                }
            } else if ("weather".equals(card.type)) {
                double temperature = state.attributes.optDouble("temperature", Double.NaN);
                String summary = weatherLabel(state.state);
                text(canvas, summary, x + 20, y + Math.min(85, h - 55), 23, secondary, false, w - 40);
                if (!Double.isNaN(temperature) && !Double.isInfinite(temperature)) {
                    text(canvas, Math.round(temperature) + "°C", x + 20, y + h - 26,
                        Math.min(48, h * 0.27f), foreground, true, w - 40);
                }
                int humidity = state.attributes.optInt("humidity", -1);
                if (humidity >= 0 && w > 320) {
                    text(canvas, "Humidité " + humidity + "%", x + w - 198, y + h - 29,
                        19, secondary, false, 175);
                }
            } else if ("graph".equals(card.type)) {
                text(canvas, state.state + (state.unit().isEmpty() ? "" : " " + state.unit()),
                    x + 20, y + 78, 28, foreground, true, w - 40);
                float[] history = client == null ? new float[0] : client.recentSamples(card.entityId);
                if (history.length < 2) {
                    text(canvas, "Collecte des valeurs…", x + 20, y + h - 23, 18,
                        secondary, false, w - 40);
                } else {
                    drawTrend(canvas, history, x + 20, y + 96,
                        w - 40, Math.max(8, h - 118), accent);
                }
            } else if ("thermostat".equals(card.type)) {
                float temp = (float) state.attributes.optDouble("current_temperature", Float.NaN);
                float target = (float) state.attributes.optDouble("temperature", Float.NaN);
                if (!finite(target)) {
                    text(canvas, "Aucune consigne", x + 20, y + h - 27, 21, MUTED, false, w - 40);
                } else {
                    text(canvas, "Cible " + target + "°C", x + 20, y + h - 25, 23, TEXT, true, w - 145);
                    if (finite(temp)) text(canvas, "Actuel " + temp + "°C", x + 20, y + 93, 22, MUTED, false, w - 150);
                    rect(canvas, x + w - 112, y + h - 76, 44, 52, 10, PANEL_ON);
                    rect(canvas, x + w - 62, y + h - 76, 44, 52, 10, PANEL_ON);
                    text(canvas, "−", x + w - 98, y + h - 39, 33, TEXT, true, 30);
                    text(canvas, "+", x + w - 50, y + h - 39, 29, TEXT, true, 30);
                }
            } else if ("light".equals(card.type) || "cover".equals(card.type)) {
                float percent = "light".equals(card.type)
                    ? (float) state.attributes.optDouble("brightness", 0) * 100f / 255f
                    : (float) state.attributes.optDouble("current_position", 0);
                text(canvas, "light".equals(card.type) ? (on ? "Allumée" : "Éteinte") : state.state,
                    x + 20, y + 90, 23, TEXT, true, w - 40);
                bar(canvas, x + 20, y + h - 34, w - 40, percent, accent);
            } else {
                rect(canvas, x + 20, y + h - 36, 12, 12, 6, on ? accent : 0xFF66768D);
                text(canvas, "on".equals(state.state) ? "Allumé" : "off".equals(state.state) ? "Éteint" : state.state,
                    x + 46, y + h - 26, 21, MUTED, false, w - 60);
            }
            canvas.restore();
        }
    }

    private int cardAt(float x, float y) {
        if (config.pages.isEmpty()) return -1;
        FrameConfig.Page page = config.pages.get(pageIndex);
        for (int i = 0; i < page.cards.size(); i++) {
            float left = cardLeft(page, i);
            float top = cardTop(page, i);
            if (x >= left && x <= left + tileWidth(page, i) && y >= top && y <= top + tileHeight(page, i)) return i;
        }
        return -1;
    }

    private void selectPage(int index) {
        if (index < 0 || index >= config.pages.size() || index == pageIndex) return;
        pageIndex = index;
        touchedCard = -1;
        feedbackCard = -1;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        refreshCamera();
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (sleeping) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && callbacks != null) callbacks.wakeDisplay();
            return true;
        }
        float x = (event.getX() - dx) / scale;
        float y = (event.getY() - dy) / scale;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = x;
                startY = y;
                touchedCard = cardAt(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(x - startX) > 26 || Math.abs(y - startY) > 26) {
                    touchedCard = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                touchedCard = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (notificationUntil > SystemClock.elapsedRealtime() &&
                    x >= 170 && x <= 1110 && y >= 235 && y <= 565) {
                    notificationUntil = 0;
                    touchedCard = -1;
                    removeCallbacks(endNotification);
                    invalidate();
                    return true;
                }
                int selected = touchedCard;
                touchedCard = -1;
                invalidate();
                if (config.pages.size() > 1 && Math.abs(x - startX) > 95 &&
                    Math.abs(x - startX) > Math.abs(y - startY) * 1.3f && startY > 205) {
                    selectPage(pageIndex + (x < startX ? 1 : -1));
                    return true;
                }
                if (callbacks == null) return true;
                if (x >= 1122 && y >= 18 && y <= 95) { callbacks.settings(); return true; }
                if (x >= 855 && x < 1122 && y >= 16 && y <= 100) { callbacks.retryConnection(); return true; }
                if (y >= 211 && y <= 257 && x >= GRID_X && x <= GRID_X + GRID_WIDTH &&
                    !config.pages.isEmpty()) {
                    int count = config.pages.size();
                    float width = (GRID_WIDTH - (count - 1) * 10f) / count;
                    int index = (int) ((x - GRID_X) / (width + 10f));
                    if (index < count && x <= GRID_X + index * (width + 10f) + width) selectPage(index);
                    return true;
                }
                if (selected < 0 || selected != cardAt(x, y)) return true;
                FrameConfig.Card card = config.pages.get(pageIndex).cards.get(selected);
                if ("metric".equals(card.type) || "gauge".equals(card.type) ||
                    "weather".equals(card.type) || "graph".equals(card.type) ||
                    "camera".equals(card.type)) return true;
                if (card.entityId.isEmpty()) { callbacks.settings(); return true; }
                long now = SystemClock.elapsedRealtime();
                if (card.entityId.equals(lastCommandId) && now - lastCommandAt < 700L) return true;
                lastCommandId = card.entityId;
                lastCommandAt = now;
                feedbackCard = selected;
                removeCallbacks(endFeedback);
                postDelayed(endFeedback, 180L);
                invalidate();
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                float cardX = cardLeft(config.pages.get(pageIndex), selected);
                float cardY = cardTop(config.pages.get(pageIndex), selected);
                float cw = tileWidth(config.pages.get(pageIndex), selected);
                float ch = tileHeight(config.pages.get(pageIndex), selected);
                if (("light".equals(card.type) || "cover".equals(card.type)) && y >= cardY + ch - 58) {
                    int percent = Math.round(100f * clamp((x - cardX - 20) / (cw - 40), 0, 1));
                    callbacks.level(card.entityId, percent);
                    return true;
                }
                if ("thermostat".equals(card.type)) {
                    HaClient.EntityState e = client == null ? null : client.get(card.entityId);
                    if (e == null) return true;
                    double target = e.attributes.optDouble("temperature", Double.NaN);
                    if (Double.isNaN(target)) return true;
                    if (y >= cardY + ch - 90 && x >= cardX + cw - 122) {
                        double step = e.attributes.optDouble("target_temp_step", 0.5);
                        if (!(!Double.isNaN(step) && !Double.isInfinite(step)) || step <= 0 || step > 5) step = 0.5;
                        double min = e.attributes.optDouble("min_temp", 5);
                        double max = e.attributes.optDouble("max_temp", 35);
                        double next = target + (x >= cardX + cw - 64 ? step : -step);
                        callbacks.temperature(card.entityId, Math.max(min, Math.min(max, next)));
                    }
                    return true;
                }
                callbacks.command(card.entityId);
                return true;
            default:
                return true;
        }
    }
}
