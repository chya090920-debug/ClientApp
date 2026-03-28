package com.chat.client.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.chat.client.R;
import com.chat.client.model.ChatMessage;
import com.chat.client.model.ChatRoom;
import com.chat.client.ui.MainActivity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketClientService extends Service {
    private static final String TAG = "WebSocketClientService";
    private static final String CHANNEL_ID = "chat_client_channel";
    private static final int NOTIFICATION_ID = 1002;
    private static final String SERVER_URL = "ws://frp-use.com:45733";
    private static final String PREF_NAME = "chat_client_prefs";
    private static final String KEY_TOKEN = "user_token";

    private WebSocketClient webSocketClient;
    private final Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();
    
    private String userToken;
    private String displayName;
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
    private ConnectionListener connectionListener;
    private MessageListener messageListener;
    private RoomListener roomListener;
    
    private static final int RECONNECT_DELAY = 3000;
    private static final int HEARTBEAT_INTERVAL = 25000; // 25秒心跳
    private boolean shouldReconnect = true;
    private Runnable heartbeatRunnable;

    public enum ConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        RECONNECTING
    }

    public interface ConnectionListener {
        void onConnectionStatusChanged(ConnectionStatus status);
    }

    public interface MessageListener {
        void onNewMessage(String roomId, ChatMessage message);
        void onMessageRecalled(String roomId, String messageId);
        void onUserTyping(String roomId, String userName, boolean isTyping);
    }

    public interface RoomListener {
        void onRoomJoined(ChatRoom room);
        void onRoomCreated(String roomId);
        void onRoomLeft(String roomId);
        void onError(String error);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        loadOrGenerateToken();
        connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ClientBinder();
    }

    public class ClientBinder extends Binder {
        public WebSocketClientService getService() {
            return WebSocketClientService.this;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "聊天客户端",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void loadOrGenerateToken() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        userToken = prefs.getString(KEY_TOKEN, null);
        if (userToken == null || userToken.isEmpty()) {
            userToken = UUID.randomUUID().toString().replace("-", "");
            prefs.edit().putString(KEY_TOKEN, userToken).apply();
        }
    }

    public String getUserToken() {
        return userToken;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String name) {
        this.displayName = name;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
        if (listener != null) {
            listener.onConnectionStatusChanged(connectionStatus);
        }
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void setRoomListener(RoomListener listener) {
        this.roomListener = listener;
    }

    private void connect() {
        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing existing connection", e);
            }
        }

        updateStatus(ConnectionStatus.CONNECTING);

        try {
            URI uri = new URI(SERVER_URL);
            Map<String, String> headers = new HashMap<>();
            headers.put("Origin", "http://frp-use.com");
            
            webSocketClient = new WebSocketClient(uri, headers) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.d(TAG, "Connected to server, status: " + handshakedata.getHttpStatus());
                    // 发送认证
                    JsonObject auth = new JsonObject();
                    auth.addProperty("action", "auth");
                    auth.addProperty("token", userToken);
                    send(gson.toJson(auth));
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "Connection closed: " + code + " - " + reason + ", remote: " + remote);
                    updateStatus(ConnectionStatus.DISCONNECTED);
                    if (shouldReconnect) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage(), ex);
                    updateStatus(ConnectionStatus.DISCONNECTED);
                    if (shouldReconnect) {
                        scheduleReconnect();
                    }
                }
            };
            
            // 设置连接超时
            webSocketClient.setConnectionLostTimeout(30);
            webSocketClient.connect();
            
            // 启动心跳
            startHeartbeat();
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect: " + e.getMessage(), e);
            updateStatus(ConnectionStatus.DISCONNECTED);
            scheduleReconnect();
        }
    }
    
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (webSocketClient != null && webSocketClient.isOpen()) {
                    JsonObject heartbeat = new JsonObject();
                    heartbeat.addProperty("action", "heartbeat");
                    webSocketClient.send(gson.toJson(heartbeat));
                    Log.d(TAG, "Heartbeat sent");
                }
                handler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
    }
    
    private void stopHeartbeat() {
        if (heartbeatRunnable != null) {
            handler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
    }

    private void scheduleReconnect() {
        updateStatus(ConnectionStatus.RECONNECTING);
        handler.postDelayed(this::connect, RECONNECT_DELAY);
    }

    private void updateStatus(ConnectionStatus status) {
        connectionStatus = status;
        handler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onConnectionStatusChanged(status);
            }
        });
    }

    private void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String action = json.get("action").getAsString();
            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : null;

            switch (action) {
                case "auth_success":
                    if (data != null && data.has("displayName")) {
                        displayName = data.get("displayName").getAsString();
                    }
                    updateStatus(ConnectionStatus.CONNECTED);
                    // 自动加入公共聊天室
                    joinRoom("000000000000", "");
                    break;

                case "auth_failed":
                    if (roomListener != null && data != null) {
                        roomListener.onError(data.get("error").getAsString());
                    }
                    break;

                case "join_success":
                    if (data != null) {
                        String roomId = data.get("roomId").getAsString();
                        ChatRoom room = new ChatRoom(roomId);
                        if (roomId.equals("000000000000")) {
                            room.setRoomName("公共聊天室");
                        }
                        rooms.put(roomId, room);
                        if (roomListener != null) {
                            roomListener.onRoomJoined(room);
                        }
                    }
                    break;

                case "room_history":
                    if (data != null) {
                        String roomId = data.get("roomId").getAsString();
                        ChatRoom room = rooms.get(roomId);
                        if (room != null) {
                            ChatMessage[] historyMessages = gson.fromJson(
                                data.get("messages"), ChatMessage[].class);
                            for (ChatMessage msg : historyMessages) {
                                room.addMessage(msg);
                            }
                        }
                    }
                    break;

                case "new_message":
                    if (data != null) {
                        ChatMessage msg = gson.fromJson(data.get("message"), ChatMessage.class);
                        ChatRoom room = rooms.get(msg.getRoomId());
                        if (room != null) {
                            room.addMessage(msg);
                        }
                        if (messageListener != null) {
                            messageListener.onNewMessage(msg.getRoomId(), msg);
                        }
                    }
                    break;

                case "message_recalled":
                    if (data != null && messageListener != null) {
                        messageListener.onMessageRecalled(
                            data.get("roomId").getAsString(),
                            data.get("messageId").getAsString()
                        );
                    }
                    break;

                case "user_typing":
                    if (data != null && messageListener != null) {
                        messageListener.onUserTyping(
                            data.get("roomId").getAsString(),
                            data.get("userName").getAsString(),
                            data.get("isTyping").getAsBoolean()
                        );
                    }
                    break;

                case "room_created":
                    if (data != null && roomListener != null) {
                        roomListener.onRoomCreated(data.get("roomId").getAsString());
                    }
                    break;

                case "muted":
                    if (roomListener != null && data != null) {
                        roomListener.onError("您已被禁言");
                    }
                    break;

                case "banned":
                    if (roomListener != null && data != null) {
                        roomListener.onError("您已被封禁");
                        shouldReconnect = false;
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling message", e);
        }
    }

    // 公共方法
    public void joinRoom(String roomId, String password) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("action", "join_room");
            msg.addProperty("roomId", roomId);
            msg.addProperty("password", password);
            webSocketClient.send(gson.toJson(msg));
        }
    }

    public void leaveRoom(String roomId) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("action", "leave_room");
            msg.addProperty("roomId", roomId);
            webSocketClient.send(gson.toJson(msg));
        }
        rooms.remove(roomId);
        if (roomListener != null) {
            roomListener.onRoomLeft(roomId);
        }
    }

    public void createRoom(String password) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("action", "create_room");
            if (password != null && !password.isEmpty()) {
                msg.addProperty("password", password);
            }
            webSocketClient.send(gson.toJson(msg));
        }
    }

    public void sendMessage(String roomId, String content) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("action", "send_message");
            msg.addProperty("roomId", roomId);
            msg.addProperty("content", content);
            msg.addProperty("type", 0);
            webSocketClient.send(gson.toJson(msg));
        }
    }

    public void sendTypingStatus(String roomId, boolean isTyping) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("action", "typing");
            msg.addProperty("roomId", roomId);
            msg.addProperty("isTyping", isTyping);
            webSocketClient.send(gson.toJson(msg));
        }
    }

    public void recallMessage(String roomId, String messageId) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("action", "recall_message");
            msg.addProperty("roomId", roomId);
            msg.addProperty("messageId", messageId);
            webSocketClient.send(gson.toJson(msg));
        }
    }

    public ChatRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public List<ChatRoom> getRooms() {
        return new ArrayList<>(rooms.values());
    }

    public void reconnect() {
        shouldReconnect = true;
        connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shouldReconnect = false;
        stopHeartbeat();
        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing connection", e);
            }
        }
    }
}
