package dev.maelab.haframe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DashboardView extends View {
    interface Callbacks {
        void settings();
        void command(String id);
        void retryConnection();
    }

    private static final int BG = 0xFF10151E;
    private static final int PANEL = 0xFF1B2432;
    private static final int PANEL_ON = 0xFF213D3C;
    private static final int PANEL_PRESSED = 0xFF33584E;
    private static final int TEXT = 0xFFF5F7FB;
    private static final int MUTED = 0xFF94A3B8;
    private static final int ACCENT = 0xFF67DBBA;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.FRANCE);
    private final SimpleDateFormat date = new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE);
    private final RectF bounds = new RectF();
    private FrameConfig config = new FrameConfig();
    private HaClient client;
    private Callbacks callbacks;
    private String status = "Configuration nécessaire";
    private boolean connected = false;
    private float scale = 1f;
    private float dx = 0;
    private float dy = 0;
    private int touchedControl = -1;
    private int feedbackControl = -1;
    private String lastCommandId = "";
    private long lastCommandAt = 0L;
    private final Runnable endFeedback = () -> { feedbackControl = -1; invalidate(); };
    private final Runnable refreshClock = new Runnable() {
        @Override public void run() {
            // The clock sits below y=135 in design coordinates. Redraw the view so
            // partial invalidation and display scaling cannot leave it frozen.
            invalidate();
            long untilNextMinute = 60000L - (System.currentTimeMillis() % 60000L);
            postDelayed(this, Math.max(250L, untilNextMinute + 100L));
        }
    };

    DashboardView(Context context) {
        super(context);
    }

    void setCallbacks(Callbacks callbacks) { this.callbacks = callbacks; }
    void setConfig(FrameConfig config) { this.config = config; invalidate(); }
    void setClient(HaClient client) { this.client = client; invalidate(); }
    void setStatus(String status, boolean connected) {
        this.status = status;
        this.connected = connected;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshClock.run();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(refreshClock);
        super.onDetachedFromWindow();
    }

    private void rect(Canvas c, float x, float y, float w, float h, float r, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        bounds.set(x, y, x + w, y + h);
        c.drawRoundRect(bounds, r, r, paint);
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size);
        paint.setTypeface(bold ? MEDIUM : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(s, x, y, paint);
    }

    private String shortText(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BG);
        scale = Math.min(getWidth() / 1280f, getHeight() / 800f);
        dx = (getWidth() - 1280f * scale) / 2f;
        dy = (getHeight() - 800f * scale) / 2f;
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        text(canvas, "HA FRAME", 50, 66, 36, TEXT, true);
        text(canvas, "Tableau de bord", 52, 101, 19, MUTED, false);
        rect(canvas, 865, 35, 12, 12, 6, connected ? ACCENT : 0xFFFFB56A);
        text(canvas, shortText((connected ? "" : "↻ ") + status, 23), 890, 47, 17, MUTED, false);
        rect(canvas, 1130, 23, 105, 58, 18, PANEL);
        text(canvas, "RÉGLAGES", 1144, 60, 17, TEXT, true);
        text(canvas, clock.format(new Date()), 52, 171, 40, TEXT, true);
        text(canvas, date.format(new Date()), 245, 165, 19, MUTED, false);
        text(canvas, "CAPTEURS", 52, 219, 18, MUTED, true);
        for (int i = 0; i < FrameConfig.METRICS; i++) drawMetric(canvas, i);
        text(canvas, "COMMANDES", 52, 426, 18, MUTED, true);
        for (int i = 0; i < FrameConfig.CONTROLS; i++) drawControl(canvas, i);
        canvas.restore();
    }

    private void drawMetric(Canvas c, int index) {
        float x = 50 + index * 394;
        float y = 237;
        rect(c, x, y, 376, 145, 20, PANEL);
        text(c, shortText(config.labels[index], 21), x + 22, y + 40, 21, MUTED, false);
        String value = "À configurer";
        int color = 0xFF78879D;
        String entity = config.ids[index];
        if (!entity.isEmpty()) {
            HaClient.EntityState state = client == null ? null : client.get(entity);
            if (!connected) value = "Hors ligne";
            else if (state == null) value = "En attente…";
            else if (state.state.equals("unknown") || state.state.equals("unavailable")) value = "Indisponible";
            else {
                value = state.state;
                if (!state.unit().isEmpty()) value += " " + state.unit();
                color = TEXT;
            }
        }
        text(c, shortText(value, 23), x + 22, y + 109, 36, color, true);
    }

    private void drawControl(Canvas c, int index) {
        int i = FrameConfig.METRICS + index;
        int col = index % 3;
        int row = index / 3;
        float x = 50 + col * 394;
        float y = 446 + row * 154;
        String entity = config.ids[i];
        HaClient.EntityState state = entity.isEmpty() || client == null ? null : client.get(entity);
        String actual = state == null ? "" : state.state;
        boolean on = connected && ("on".equals(actual) || "open".equals(actual) || "playing".equals(actual));
        rect(c, x, y, 376, 133, 20, (touchedControl == index || feedbackControl == index)
            ? PANEL_PRESSED : (on ? PANEL_ON : PANEL));
        rect(c, x + 22, y + 24, 12, 12, 6, on ? ACCENT : 0xFF66768D);
        text(c, shortText(config.labels[i], 24), x + 50, y + 39, 25, TEXT, true);
        String secondary = entity.isEmpty() ? "À configurer" : (!connected ? "Hors ligne" : (state == null ? "En attente…" : actual));
        if ("on".equals(secondary)) secondary = "Allumé";
        if ("off".equals(secondary)) secondary = "Éteint";
        text(c, shortText(secondary, 28), x + 23, y + 94, 19, MUTED, false);
    }

    private int controlAt(float x, float y) {
        for (int index = 0; index < FrameConfig.CONTROLS; index++) {
            float left = 50 + (index % 3) * 394;
            float top = 446 + (index / 3) * 154;
            if (x >= left && x <= left + 376 && y >= top && y <= top + 133) return index;
        }
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (scale <= 0f) return true;
        float x = (event.getX() - dx) / scale;
        float y = (event.getY() - dy) / scale;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchedControl = controlAt(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                touchedControl = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                // Prevent swiping across multiple tiles from sending commands.
                if (touchedControl != -1 && controlAt(x, y) != touchedControl) {
                    touchedControl = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                int tappedControl = touchedControl;
                touchedControl = -1;
                invalidate();
                if (callbacks == null) return true;
                if (x >= 1122 && y >= 18 && y <= 95) {
                    callbacks.settings();
                    return true;
                }
                if (x >= 855 && x < 1122 && y >= 16 && y <= 100) {
                    callbacks.retryConnection();
                    return true;
                }
                if (tappedControl < 0 || tappedControl != controlAt(x, y)) return true;
                String entity = config.ids[FrameConfig.METRICS + tappedControl];
                if (TextUtils.isEmpty(entity)) {
                    callbacks.settings();
                    return true;
                }
                long now = SystemClock.elapsedRealtime();
                if (entity.equals(lastCommandId) && now - lastCommandAt < 700L) return true;
                lastCommandId = entity;
                lastCommandAt = now;
                feedbackControl = tappedControl;
                removeCallbacks(endFeedback);
                postDelayed(endFeedback, 180L);
                invalidate();
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                callbacks.command(entity);
                return true;
            default:
                return true;
        }
    }
}
