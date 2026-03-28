package com.chat.client.model;

public class ChatMessage {
    private String id;
    private String roomId;
    private String senderToken;
    private String senderName;
    private String content;
    private long timestamp;
    private int type; // 0=普通消息, 1=系统公告, 2=加入, 3=离开, 4=撤回
    private boolean isRecalled;

    public ChatMessage() {
        this.id = generateId();
        this.timestamp = System.currentTimeMillis();
        this.isRecalled = false;
    }

    public ChatMessage(String roomId, String senderToken, String senderName, String content, int type) {
        this();
        this.roomId = roomId;
        this.senderToken = senderToken;
        this.senderName = senderName;
        this.content = content;
        this.type = type;
    }

    private String generateId() {
        return String.valueOf(System.currentTimeMillis()) + (int)(Math.random() * 10000);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getSenderToken() { return senderToken; }
    public void setSenderToken(String senderToken) { this.senderToken = senderToken; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public boolean isRecalled() { return isRecalled; }
    public void setRecalled(boolean recalled) { isRecalled = recalled; }
}
