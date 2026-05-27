package com.buscorp.employee.core.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.buscorp.employee.BuildConfig;
import com.buscorp.employee.core.auth.SessionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import timber.log.Timber;

@Singleton
public class SupabaseRealtimeClient {

    public interface RealtimeListener {
        void onEvent(String topic, JSONObject payload);
        void onConnectionChanged(boolean connected);
    }

    private static final long HEARTBEAT_SECONDS = 30L;

    private final OkHttpClient client;
    private final SessionManager sessionManager;
    private final Map<String, RealtimeListener> listeners = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Nullable
    private WebSocket webSocket;
    private int refCounter = 1;

    @Inject
    public SupabaseRealtimeClient(OkHttpClient client, SessionManager sessionManager) {
        this.client = client;
        this.sessionManager = sessionManager;
    }

    public void connect() {
        if (webSocket != null) {
            return;
        }

        String realtimeUrl = BuildConfig.SUPABASE_URL
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                + "/realtime/v1/websocket?apikey=" + BuildConfig.SUPABASE_ANON_KEY + "&vsn=1.0.0";

        Request request = new Request.Builder()
                .url(realtimeUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket socket, @NonNull Response response) {
                notifyConnection(true);
                startHeartbeat();
                for (String topic : listeners.keySet()) {
                    joinTopic(topic);
                }
            }

            @Override
            public void onMessage(@NonNull WebSocket socket, @NonNull String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(@NonNull WebSocket socket, @NonNull Throwable t, @Nullable Response response) {
                Timber.w(t, "Realtime websocket failed.");
                webSocket = null;
                notifyConnection(false);
                scheduler.schedule(SupabaseRealtimeClient.this::connect, 3, TimeUnit.SECONDS);
            }

            @Override
            public void onClosed(@NonNull WebSocket socket, int code, @NonNull String reason) {
                webSocket = null;
                notifyConnection(false);
            }
        });
    }

    public void subscribePostgresChanges(String topic, String table, String filter, RealtimeListener listener) {
        listeners.put(topic, listener);
        connect();
        joinPostgresTopic(topic, table, filter);
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Bus Corp. app closed realtime connection");
            webSocket = null;
        }
        listeners.clear();
    }

    private void joinTopic(String topic) {
        send(topic, "phx_join", new JSONObject());
    }

    private void joinPostgresTopic(String topic, String table, String filter) {
        try {
            JSONObject binding = new JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", table);
            if (filter != null && !filter.isEmpty()) {
                binding.put("filter", filter);
            }

            JSONObject payload = new JSONObject()
                    .put("config", new JSONObject()
                            .put("postgres_changes", new JSONArray().put(binding))
                            .put("broadcast", new JSONObject().put("self", false))
                            .put("presence", new JSONObject().put("key", sessionManager.getUserId())))
                    .put("access_token", sessionManager.getAccessToken());

            send(topic, "phx_join", payload);
        } catch (JSONException e) {
            Timber.e(e, "Unable to join realtime topic.");
        }
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> send("phoenix", "heartbeat", new JSONObject()),
                HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS,
                TimeUnit.SECONDS);
    }

    private void send(String topic, String event, JSONObject payload) {
        WebSocket socket = webSocket;
        if (socket == null) {
            return;
        }

        try {
            JSONObject message = new JSONObject()
                    .put("topic", topic)
                    .put("event", event)
                    .put("payload", payload)
                    .put("ref", String.valueOf(refCounter++));
            socket.send(message.toString());
        } catch (JSONException e) {
            Timber.e(e, "Unable to send realtime message.");
        }
    }

    private void handleMessage(String text) {
        try {
            JSONObject message = new JSONObject(text);
            String topic = message.optString("topic");
            RealtimeListener listener = listeners.get(topic);
            if (listener != null) {
                listener.onEvent(topic, message.optJSONObject("payload"));
            }
        } catch (JSONException e) {
            Timber.w(e, "Invalid realtime payload.");
        }
    }

    private void notifyConnection(boolean connected) {
        for (RealtimeListener listener : listeners.values()) {
            listener.onConnectionChanged(connected);
        }
    }
}
