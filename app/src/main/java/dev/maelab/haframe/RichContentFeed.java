package dev.maelab.haframe;

import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only HA calendar and to-do content, fetched only for visible widgets. */
final class RichContentFeed {
    private static final int MAX_RESPONSE = 128 * 1024;
    private final OkHttpClient http;
    private final String base;
    private final String token;
    private final Runnable changed;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile boolean stopped;

    private static final class Entry {
        volatile JSONArray items = new JSONArray();
        volatile long nextFetch;
        volatile boolean fetching;
    }

    RichContentFeed(OkHttpClient client, String websocket, String token, Runnable changed) {
        this.http = client;
        this.token = token;
        this.changed = changed;
        String result = "";
        try {
            URI uri = new URI(websocket);
            String scheme = "wss".equalsIgnoreCase(uri.getScheme()) ? "https" : "http";
            String path = uri.getRawPath();
            if (path != null && path.endsWith("/api/websocket")) {
                result = new URI(scheme, null, uri.getHost(), uri.getPort(),
                    path.substring(0,path.length() - "/api/websocket".length()), null, null).toASCIIString();
            }
        } catch (Exception ignored) {}
        base = result;
    }

    JSONArray items(final String entity) {
        if (stopped || base.isEmpty() || entity == null ||
            !entity.matches("(?:calendar|todo)\\.[a-z0-9_]+")) return new JSONArray();
        Entry current = entries.get(entity);
        if (current == null) {
            Entry created = new Entry();
            Entry existing = entries.putIfAbsent(entity, created);
            current = existing == null ? created : existing;
        }
        final Entry entry = current;
        long now = System.currentTimeMillis();
        synchronized (entry) {
            if (now >= entry.nextFetch && !entry.fetching) {
                entry.fetching = true;
                entry.nextFetch = now + 300000L;
                try {
                    http.dispatcher().executorService().execute(new Runnable() {
                        @Override public void run() {
                            try { entry.items = fetch(entity); changed.run(); }
                            catch (Exception ignored) {}
                            finally { entry.fetching = false; }
                        }
                    });
                } catch (RuntimeException ignored) { entry.fetching = false; }
            }
        }
        return entry.items;
    }

    private JSONArray fetch(String entity) throws Exception {
        String url;
        RequestBody body = null;
        if (entity.startsWith("calendar.")) {
            SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            long now=System.currentTimeMillis();
            url=base+"/api/calendars/"+entity+"?start="+URLEncoder.encode(iso.format(new Date(now)),"UTF-8")+
                "&end="+URLEncoder.encode(iso.format(new Date(now+7L*86400000L)),"UTF-8");
        } else {
            url=base+"/api/services/todo/get_items?return_response";
            body=RequestBody.create(MediaType.parse("application/json"),
                new JSONObject().put("entity_id",entity).put("status","needs_action").toString());
        }
        Request.Builder request=new Request.Builder().url(url)
            .header("Authorization","Bearer "+token).header("Accept","application/json");
        if (body != null) request.post(body);
        try (Response response=http.newCall(request.build()).execute()) {
            if (!response.isSuccessful() || response.body()==null) return new JSONArray();
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            InputStream input=response.body().byteStream();
            byte[] chunk=new byte[4096];
            int n;
            while ((n=input.read(chunk))!=-1) {
                if (output.size()+n>MAX_RESPONSE) return new JSONArray();
                output.write(chunk,0,n);
            }
            String json=new String(output.toByteArray(),"UTF-8");
            JSONArray rows;
            if (entity.startsWith("calendar.")) rows=new JSONArray(json);
            else {
                JSONObject responseObject=new JSONObject(json);
                JSONObject content=responseObject.optJSONObject("service_response");
                JSONObject target=content==null?null:content.optJSONObject(entity);
                rows=target==null?new JSONArray():target.optJSONArray("items");
            }
            JSONArray result=new JSONArray();
            if (rows==null) return result;
            for(int i=0;i<rows.length() && result.length()<5;i++) {
                JSONObject row=rows.optJSONObject(i);
                if(row==null) continue;
                String title=entity.startsWith("calendar.")?row.optString("summary",""):
                    row.optString("summary","");
                if(title.isEmpty()) continue;
                String when="";
                if(entity.startsWith("calendar.")) {
                    JSONObject start=row.optJSONObject("start");
                    if(start!=null) when=start.optString("dateTime",start.optString("date",""));
                } else when=row.optString("due","");
                result.put(new JSONObject().put("title",
                    title.length()>80?title.substring(0,80):title).put("when",
                    when.length()>30?when.substring(0,30):when));
            }
            return result;
        }
    }

    void stop() { stopped=true; entries.clear(); }
}
