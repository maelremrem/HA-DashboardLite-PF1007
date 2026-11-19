package dev.maelab.haframe;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class HaClient {
    interface Listener {
        void onStatus(String status, boolean connected);
        void onStatesChanged();
        void onCommandError(String message);
    }

    static final class EntityState {
        final String state;
        final JSONObject attributes;

        EntityState(String state, JSONObject attributes) {
            this.state = state;
            this.attributes = attributes == null ? new JSONObject() : attributes;
        }

        String unit() {
            return attributes.optString("unit_of_measurement", "");
        }
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger sequence = new AtomicInteger(1);
    private final Map<String, EntityState> states = new ConcurrentHashMap<>();
    private final Listener listener;
    private final String endpoint;
    private final String token;
    private final String[] entities;
    private volatile WebSocket socket;
    private volatile boolean stopped = true;
    private volatile boolean authenticated = false;
    private int failures = 0;
    private int subscribeId = -1;
    private int getStatesId = -1;
    private int fallbackSubscriptionId = -1;
    private volatile boolean receivedStateSnapshot = false;
    private final Runnable reconnect = this::connect;
    private final Runnable snapshotFallback = () -> {
        if (!stopped && authenticated && !receivedStateSnapshot && getStatesId == -1 && entities.length > 0) {
            status("Chargement des entités (mode compatible)…", true);
            try { startClassicSubscription(); }
            catch (Exception ignored) {}
        }
    };

    private void startClassicSubscription() throws Exception {
        if (fallbackSubscriptionId != -1) return;
        // Stop the compact subscription to avoid duplicate updates.
        if (subscribeId != -1) {
            send(new JSONObject().put("type", "unsubscribe_events").put("subscription", subscribeId));
            subscribeId = -1;
        }
        fallbackSubscriptionId = send(new JSONObject().put("type", "subscribe_events")
            .put("event_type", "state_changed"));
        getStatesId = send(new JSONObject().put("type", "get_states"));
    }

    HaClient(String endpoint, String token, String[] entities, Listener listener) {
        this.endpoint = endpoint;
        this.token = token;
        this.entities = entities;
        this.listener = listener;
    }

    EntityState get(String id) {
        return states.get(id);
    }

    boolean isConnected() {
        return authenticated;
    }

    void start() {
        if (!stopped) return;
        stopped = false;
        connect();
    }

    void stop() {
        stopped = true;
        authenticated = false;
        main.removeCallbacks(reconnect);
        main.removeCallbacks(snapshotFallback);
        WebSocket previous = socket;
        socket = null;
        if (previous != null) previous.close(1000, "Dashboard paused");
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private void status(String message, boolean connected) {
        main.post(() -> { if (!stopped) listener.onStatus(message, connected); });
    }

    private void changed() {
        main.post(() -> { if (!stopped) listener.onStatesChanged(); });
    }

    private void connect() {
        if (stopped) return;
        authenticated = false;
        receivedStateSnapshot = false;
        subscribeId = -1;
        getStatesId = -1;
        fallbackSubscriptionId = -1;
        main.removeCallbacks(snapshotFallback);
        status("Connexion à Home Assistant…", false);
        Request request = new Request.Builder().url(endpoint).build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onMessage(WebSocket webSocket, String text) {
                if (stopped || socket != webSocket) return;
                try { handle(webSocket, new JSONObject(text)); }
                catch (Exception ignored) { status("Réponse Home Assistant illisible", false); }
            }

            @Override public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                if (stopped || socket != webSocket) return;
                authenticated = false;
                status("Hors ligne : " + shortError(error), false);
                scheduleReconnect();
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (stopped || socket != webSocket) return;
                authenticated = false;
                status("Connexion interrompue", false);
                scheduleReconnect();
            }
        });
    }

    private static String shortError(Throwable cause) {
        String name = cause == null ? "Erreur réseau" : cause.getClass().getSimpleName();
        if (name.contains("SSL") || name.contains("Cert")) return "Certificat HTTPS à vérifier";
        if (name.contains("UnknownHost")) return "Adresse introuvable";
        return name;
    }

    private void scheduleReconnect() {
        main.removeCallbacks(reconnect);
        int delay = Math.min(30, 2 << Math.min(failures++, 4));
        main.postDelayed(reconnect, delay * 1000L);
    }

    private void handle(WebSocket webSocket, JSONObject msg) throws Exception {
        String type = msg.optString("type");
        if ("auth_required".equals(type)) {
            JSONObject auth = new JSONObject();
            auth.put("type", "auth");
            auth.put("access_token", token);
            webSocket.send(auth.toString());
        } else if ("auth_ok".equals(type)) {
            failures = 0;
            authenticated = true;
            states.clear();
            receivedStateSnapshot = false;
            status(entities.length == 0 ? "Aucune entité configurée" : "Synchronisation…", true);
            changed();
            JSONArray ids = new JSONArray();
            for (String entity : entities) ids.put(entity);
            if (ids.length() > 0) {
                JSONObject sub = new JSONObject().put("type", "subscribe_entities");
                sub.put("entity_ids", ids);
                subscribeId = send(sub);
                // If a proxy/older server does not send an initial compact event,
                // obtain a single snapshot without subscribing to all state changes.
                main.postDelayed(snapshotFallback, 4500L);
            }
        } else if ("auth_invalid".equals(type)) {
            stopped = true;
            authenticated = false;
            main.removeCallbacks(reconnect);
            main.post(() -> listener.onStatus("Token refusé — ouvrir Réglages", false));
            webSocket.close(1008, "Unauthorized");
        } else if ("event".equals(type)) {
            JSONObject event = msg.optJSONObject("event");
            if (event == null) return;
            if (msg.optInt("id", -1) == subscribeId) {
                receivedStateSnapshot = true;
                main.removeCallbacks(snapshotFallback);
                handleCompressedEvent(event);
                reportLoadedStates();
            } else if (msg.optInt("id", -1) == fallbackSubscriptionId) {
                handleStateChanged(event);
                reportLoadedStates();
            }
        } else if ("result".equals(type) && !msg.optBoolean("success", false)) {
            int id = msg.optInt("id", -1);
            if (id == subscribeId) {
                status("Compatibilité API : mode classique", true);
                main.removeCallbacks(snapshotFallback);
                subscribeId = -1;
                startClassicSubscription();
            } else {
                JSONObject error = msg.optJSONObject("error");
                String text = error == null ? "Commande refusée" : error.optString("message", "Commande refusée");
                main.post(() -> { if (!stopped) listener.onCommandError(text); });
            }
        } else if ("result".equals(type) && msg.optInt("id", -1) == getStatesId) {
            JSONArray all = msg.optJSONArray("result");
            if (all == null) return;
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.optJSONObject(i);
                if (item == null) continue;
                String entity = item.optString("entity_id", "");
                if (!wanted(entity)) continue;
                states.put(entity, new EntityState(item.optString("state", ""), item.optJSONObject("attributes")));
            }
            receivedStateSnapshot = true;
            main.removeCallbacks(snapshotFallback);
            changed();
            reportLoadedStates();
        }
    }

    private void reportLoadedStates() {
        status("Connecté · " + states.size() + "/" + entities.length + " entités", true);
    }

    private boolean wanted(String id) {
        for (String entity : entities) if (entity.equals(id)) return true;
        return false;
    }

    private void handleStateChanged(JSONObject event) {
        JSONObject data = event.optJSONObject("data");
        if (data == null) return;
        String id = data.optString("entity_id", "");
        if (!wanted(id)) return;
        JSONObject current = data.optJSONObject("new_state");
        if (current == null) states.remove(id);
        else states.put(id, new EntityState(current.optString("state", ""), current.optJSONObject("attributes")));
        changed();
    }

    private void handleCompressedEvent(JSONObject event) {
        boolean modified = false;
        JSONObject added = event.optJSONObject("a");
        if (added != null) {
            JSONArray keys = added.names();
            if (keys != null) for (int i = 0; i < keys.length(); i++) {
                String id = keys.optString(i);
                if (!wanted(id)) continue;
                JSONObject value = added.optJSONObject(id);
                if (value == null) continue;
                states.put(id, new EntityState(value.optString("s", ""), value.optJSONObject("a")));
                modified = true;
            }
        }
        JSONObject changed = event.optJSONObject("c");
        if (changed != null) {
            JSONArray keys = changed.names();
            if (keys != null) for (int i = 0; i < keys.length(); i++) {
                String id = keys.optString(i);
                if (!wanted(id)) continue;
                JSONObject patch = changed.optJSONObject(id);
                if (patch == null) continue;
                JSONObject plus = patch.optJSONObject("+");
                EntityState previous = states.get(id);
                String state = previous == null ? "" : previous.state;
                JSONObject attrs = new JSONObject();
                if (previous != null) {
                    JSONArray oldNames = previous.attributes.names();
                    if (oldNames != null) for (int k = 0; k < oldNames.length(); k++) {
                        String name = oldNames.optString(k);
                        try { attrs.put(name, previous.attributes.opt(name)); } catch (Exception ignored) {}
                    }
                }
                if (plus != null) {
                    if (plus.has("s")) state = plus.optString("s", "");
                    JSONObject changedAttrs = plus.optJSONObject("a");
                    if (changedAttrs != null) {
                        JSONArray names = changedAttrs.names();
                        if (names != null) for (int k = 0; k < names.length(); k++) {
                            String name = names.optString(k);
                            try { attrs.put(name, changedAttrs.opt(name)); } catch (Exception ignored) {}
                        }
                    }
                }
                JSONObject minus = patch.optJSONObject("-");
                if (minus != null && minus.has("a")) {
                    JSONArray removedAttrs = minus.optJSONArray("a");
                    if (removedAttrs != null) for (int k = 0; k < removedAttrs.length(); k++) {
                        attrs.remove(removedAttrs.optString(k));
                    }
                }
                states.put(id, new EntityState(state, attrs));
                modified = true;
            }
        }
        JSONArray removed = event.optJSONArray("r");
        if (removed != null) for (int i = 0; i < removed.length(); i++) {
            if (states.remove(removed.optString(i)) != null) modified = true;
        }
        if (modified) changed();
    }

    private int send(JSONObject payload) {
        int id = sequence.getAndIncrement();
        try {
            payload.put("id", id);
            WebSocket current = socket;
            if (current != null) current.send(payload.toString());
        } catch (Exception ignored) {}
        return id;
    }

    void activate(String entityId) {
        if (!authenticated || entityId == null || entityId.isEmpty()) return;
        String[] parts = entityId.split("\\.", 2);
        if (parts.length != 2) return;
        String domain = parts[0];
        String service;
        switch (domain) {
            case "light":
            case "switch":
            case "fan":
            case "input_boolean": service = "toggle"; break;
            case "scene":
            case "script": service = "turn_on"; break;
            case "button":
            case "input_button": service = "press"; break;
            case "cover":
                EntityState s = states.get(entityId);
                if (s == null || "unavailable".equals(s.state)) return;
                service = "open".equals(s.state) ? "close_cover" : "open_cover";
                break;
            default:
                main.post(() -> listener.onCommandError("Entité non commandable : " + domain));
                return;
        }
        try {
            JSONObject target = new JSONObject().put("entity_id", entityId);
            send(new JSONObject().put("type", "call_service")
                .put("domain", domain).put("service", service).put("target", target));
        } catch (Exception ignored) {}
    }
}
