package dev.maelab.haframe;

import org.json.JSONArray;
import java.util.ArrayDeque;

/** Privacy-safe rolling application events (no passwords, URLs, entity states or tokens). */
final class DiagnosticLog {
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();
    private static final int MAX = 60;

    static synchronized void add(String event) {
        if (event == null || !event.matches("[A-Za-z0-9 _:/.-]{1,80}")) return;
        EVENTS.addLast(android.os.SystemClock.elapsedRealtime()/1000L + "s  " + event);
        while (EVENTS.size() > MAX) EVENTS.removeFirst();
    }

    static synchronized JSONArray snapshot() {
        JSONArray result = new JSONArray();
        for (String line : EVENTS) result.put(line);
        return result;
    }

    private DiagnosticLog() {}
}
