package dev.maelab.haframe;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import androidx.core.graphics.PathParser;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small vector icon library; no font or network assets required on Android 6. */
final class TileIcon {
    static final String[] NAMES = {
        "none", "home", "light", "thermometer", "energy", "water", "sun", "cloud",
        "lock", "camera", "chart", "fan", "battery", "wifi", "person", "settings",
        "bell", "door", "plug", "moon", "heart"
    };

    private static final Map<String, Path> MDI_CACHE = new LinkedHashMap<String, Path>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Path> eldest) {
            return size() > 64;
        }
    };

    static boolean supported(String name) {
        if (name == null || name.isEmpty()) return true;
        if (name.matches("mdi:[a-z0-9]+(?:-[a-z0-9]+)*")) return true;
        for (String known : NAMES) if (known.equals(name)) return true;
        return false;
    }

    static boolean validPath(String path) {
        return path != null && (path.isEmpty() || (path.length() <= 8192 &&
            path.matches("[MmLlHhVvCcSsQqTtAaZz0-9eE+,.\\s-]+") &&
            (path.startsWith("M") || path.startsWith("m"))));
    }

    private static void drawMdi(Canvas canvas, Paint paint, String data,
                                float x, float y, float size, int color) {
        if (!validPath(data) || data.isEmpty()) return;
        Path vector = MDI_CACHE.get(data);
        if (vector == null) {
            try { vector = PathParser.createPathFromPathData(data); }
            catch (RuntimeException ignored) { return; }
            if (vector == null) return;
            MDI_CACHE.put(data, vector);
        }
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(size / 24f, size / 24f);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(vector, paint);
        canvas.restore();
    }

    static String effective(String name, String type) {
        if (name == null) name = "";
        if ("none".equals(name)) return "";
        if (!name.isEmpty()) return name;
        switch (type) {
            case "light": return "light";
            case "thermostat": return "thermometer";
            case "weather": return "sun";
            case "camera": return "camera";
            case "graph": return "chart";
            case "cover": return "door";
            case "gauge": return "chart";
            default: return "";
        }
    }

    private static void line(Canvas c, Paint p, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, p);
    }

    static void draw(Canvas canvas, Paint paint, String requested, String pathData,
                     String type, float x, float y, float size, int color) {
        if (pathData != null && !pathData.isEmpty()) {
            drawMdi(canvas, paint, pathData, x, y, size, color);
            return;
        }
        String name = effective(requested, type);
        if (name.startsWith("mdi:")) return;
        if (name.isEmpty() || !supported(name)) return;
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(size / 24f, size / 24f);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        Path path = new Path();
        switch (name) {
            case "home":
                path.moveTo(3, 11); path.lineTo(12, 3); path.lineTo(21, 11);
                path.moveTo(5, 10); path.lineTo(5, 21); path.lineTo(19, 21); path.lineTo(19, 10);
                path.moveTo(10, 21); path.lineTo(10, 15); path.lineTo(14, 15); path.lineTo(14, 21);
                canvas.drawPath(path, paint); break;
            case "light":
                canvas.drawCircle(12, 9, 6, paint);
                line(canvas,paint,9,15,9,19); line(canvas,paint,15,15,15,19);
                line(canvas,paint,9,19,15,19); line(canvas,paint,10,22,14,22); break;
            case "thermometer":
                canvas.drawRoundRect(new RectF(10,3,14,17),2,2,paint);
                canvas.drawCircle(12,18,4,paint); line(canvas,paint,12,8,12,19); break;
            case "energy":
                path.moveTo(13,2); path.lineTo(5,13); path.lineTo(12,13);
                path.lineTo(10,22); path.lineTo(20,10); path.lineTo(13,10);
                path.close(); canvas.drawPath(path,paint); break;
            case "water":
                path.moveTo(12,2); path.cubicTo(12,2,5,11,5,15);
                path.cubicTo(5,24,19,24,19,15); path.cubicTo(19,11,12,2,12,2);
                canvas.drawPath(path,paint); break;
            case "sun":
                canvas.drawCircle(12,12,4,paint);
                for (int i=0;i<8;i++){
                    double a=i*Math.PI/4;
                    line(canvas,paint,(float)(12+7*Math.cos(a)),(float)(12+7*Math.sin(a)),
                        (float)(12+10*Math.cos(a)),(float)(12+10*Math.sin(a)));
                } break;
            case "cloud":
                path.moveTo(5,18); path.cubicTo(0,18,1,10,6,10);
                path.cubicTo(7,2,17,2,19,11); path.cubicTo(25,12,23,19,18,19);
                path.lineTo(6,19); canvas.drawPath(path,paint); break;
            case "lock":
                canvas.drawRoundRect(new RectF(5,11,19,21),2,2,paint);
                path.moveTo(8,11);path.lineTo(8,8);path.cubicTo(8,1,16,1,16,8);
                path.lineTo(16,11);canvas.drawPath(path,paint);break;
            case "camera":
                canvas.drawRoundRect(new RectF(2,6,22,20),3,3,paint);
                canvas.drawCircle(12,13,4,paint);
                line(canvas,paint,7,6,9,3);line(canvas,paint,9,3,15,3);
                line(canvas,paint,15,3,17,6);break;
            case "chart":
                line(canvas,paint,3,3,3,21);line(canvas,paint,3,21,22,21);
                path.moveTo(5,17);path.lineTo(10,12);path.lineTo(14,15);
                path.lineTo(21,5);canvas.drawPath(path,paint);break;
            case "fan":
                canvas.drawCircle(12,12,2,paint);
                for (int i=0;i<3;i++){
                    canvas.save(); canvas.rotate(i*120,12,12);
                    path.reset();path.moveTo(12,10);path.cubicTo(6,4,10,0,14,3);
                    path.cubicTo(17,5,15,10,12,10);canvas.drawPath(path,paint);
                    canvas.restore();
                } break;
            case "battery":
                canvas.drawRoundRect(new RectF(2,7,20,17),2,2,paint);
                line(canvas,paint,22,10,22,14);line(canvas,paint,6,12,15,12);break;
            case "wifi":
                canvas.drawArc(new RectF(2,3,22,23),225,90,false,paint);
                canvas.drawArc(new RectF(6,8,18,20),225,90,false,paint);
                canvas.drawCircle(12,19,1,paint);break;
            case "person":
                canvas.drawCircle(12,7,4,paint);
                canvas.drawArc(new RectF(3,11,21,24),180,180,false,paint);break;
            case "settings":
                canvas.drawCircle(12,12,8,paint);canvas.drawCircle(12,12,3,paint);
                for(int i=0;i<8;i++){
                    double a=i*Math.PI/4;
                    line(canvas,paint,(float)(12+8*Math.cos(a)),(float)(12+8*Math.sin(a)),
                        (float)(12+11*Math.cos(a)),(float)(12+11*Math.sin(a)));
                }break;
            case "bell":
                path.moveTo(5,17);path.lineTo(7,14);path.lineTo(7,10);
                path.cubicTo(7,2,17,2,17,10);path.lineTo(17,14);
                path.lineTo(19,17);path.close();canvas.drawPath(path,paint);
                canvas.drawArc(new RectF(10,16,14,22),0,180,false,paint);break;
            case "door":
                canvas.drawRect(5,3,19,22,paint);canvas.drawCircle(15,13,1,paint);break;
            case "plug":
                line(canvas,paint,8,3,8,10);line(canvas,paint,16,3,16,10);
                canvas.drawArc(new RectF(5,6,19,18),0,180,false,paint);
                line(canvas,paint,12,18,12,22);break;
            case "moon":
                path.moveTo(18,3);path.cubicTo(8,2,5,14,13,18);
                path.cubicTo(16,20,20,18,21,16);path.cubicTo(17,23,7,23,4,16);
                path.cubicTo(1,8,10,0,18,3);canvas.drawPath(path,paint);break;
            case "heart":
                path.moveTo(12,21);path.cubicTo(9,18,2,13,2,8);
                path.cubicTo(2,1,9,2,12,6);path.cubicTo(15,2,22,1,22,8);
                path.cubicTo(22,13,15,19,12,21);path.close();
                canvas.drawPath(path,paint);break;
            default: break;
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private TileIcon() {}
}
