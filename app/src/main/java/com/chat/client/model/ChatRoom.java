package com.chat.client.model;

import java.util.ArrayList;
import java.util.List;

public class ChatRoom {
    private String roomId;
    private String roomName;
    private List<ChatMessage> messages;
    private int unreadCount;
    private long lastMessageTime;
    private String lastMessage;
    private boolean hasPassword;

    public ChatRoom(String roomId) {
        this.roomId = roomId;
        this.roomName = "聊天室 " + roomId;
        this.messages = new ArrayList<>();
        this.unreadCount = 0;
        this.lastMessageTime = System.currentTimeMillis();
        this.lastMessage = "";
        this.hasPassword = false;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        lastMessage = message.getContent();
        lastMessageTime = message.getTimestamp();
    }

    public void clearUnread() {
        unreadCount = 0;
    }

    public void incrementUnread() {
        unreadCount++;
    }

    // Getters and Setters
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public boolean hasPassword() { return hasPassword; }
    public void setHasPassword(boolean hasPassword) { this.hasPassword = hasPassword; }
}
