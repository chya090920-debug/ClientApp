package com.chat.client.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.client.R;
import com.chat.client.model.ChatMessage;
import com.chat.client.model.ChatRoom;
import com.chat.client.service.WebSocketClientService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatRoomActivity extends AppCompatActivity implements WebSocketClientService.MessageListener {

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private TextView tvTitle;
    private TextView tvTyping;

    private MessageAdapter messageAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private String roomId;
    private String roomName;
    private String userToken;
    private SimpleDateFormat timeFormat;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable typingStopRunnable;
    private boolean isTyping = false;

    private WebSocketClientService clientService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WebSocketClientService.ClientBinder binder = (WebSocketClientService.ClientBinder) service;
            clientService = binder.getService();
            clientService.setMessageListener(ChatRoomActivity.this);
            userToken = clientService.getUserToken();
            isBound = true;
            loadMessages();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            clientService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);

        roomId = getIntent().getStringExtra("roomId");
        roomName = getIntent().getStringExtra("roomName");
        timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        initViews();
        setupTypingWatcher();
        bindService();
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        tvTitle = findViewById(R.id.tv_title);
        tvTyping = findViewById(R.id.tv_typing);

        tvTitle.setText(roomName != null ? roomName : "聊天室");

        messageAdapter = new MessageAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(messageAdapter);

        btnSend.setOnClickListener(v -> sendMessage());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void setupTypingWatcher() {
        typingStopRunnable = () -> {
            if (isTyping && clientService != null) {
                isTyping = false;
                clientService.sendTypingStatus(roomId, false);
            }
        };

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isTyping && s.length() > 0 && clientService != null) {
                    isTyping = true;
                    clientService.sendTypingStatus(roomId, true);
                }
                handler.removeCallbacks(typingStopRunnable);
                handler.postDelayed(typingStopRunnable, 2000);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void bindService() {
        Intent intent = new Intent(this, WebSocketClientService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void loadMessages() {
        if (clientService != null) {
            ChatRoom room = clientService.getRoom(roomId);
            if (room != null) {
                messages = new ArrayList<>(room.getMessages());
                messageAdapter.notifyDataSetChanged();
                if (!messages.isEmpty()) {
                    rvMessages.scrollToPosition(messages.size() - 1);
                }
            }
        }
    }

    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) return;

        if (clientService != null) {
            clientService.sendMessage(roomId, content);
            etMessage.setText("");
            
            // 停止输入状态
            if (isTyping) {
                isTyping = false;
                clientService.sendTypingStatus(roomId, false);
            }
        }
    }

    @Override
    public void onNewMessage(String roomId, ChatMessage message) {
        if (!this.roomId.equals(roomId)) return;
        
        runOnUiThread(() -> {
            messages.add(message);
            messageAdapter.notifyItemInserted(messages.size() - 1);
            rvMessages.scrollToPosition(messages.size() - 1);
        });
    }

    @Override
    public void onMessageRecalled(String roomId, String messageId) {
        if (!this.roomId.equals(roomId)) return;
        
        runOnUiThread(() -> {
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).getId().equals(messageId)) {
                    messages.get(i).setRecalled(true);
                    messageAdapter.notifyItemChanged(i);
                    break;
                }
            }
        });
    }

    @Override
    public void onUserTyping(String roomId, String userName, boolean isTyping) {
        if (!this.roomId.equals(roomId)) return;
        
        runOnUiThread(() -> {
            if (isTyping) {
                tvTyping.setText(userName + " 正在输入...");
                tvTyping.setVisibility(View.VISIBLE);
            } else {
                tvTyping.setVisibility(View.GONE);
            }
        });
    }

    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

        private static final int TYPE_MY_MESSAGE = 0;
        private static final int TYPE_OTHER_MESSAGE = 1;
        private static final int TYPE_SYSTEM = 2;

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == TYPE_MY_MESSAGE ? 
                R.layout.item_message_sent : 
                (viewType == TYPE_SYSTEM ? R.layout.item_message_system : R.layout.item_message_received);
            View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            ChatMessage message = messages.get(position);
            
            if (getItemViewType(position) == TYPE_SYSTEM) {
                holder.tvContent.setText(message.getContent());
                holder.tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));
            } else {
                holder.tvContent.setText(message.isRecalled() ? "消息已撤回" : message.getContent());
                holder.tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));
                
                if (getItemViewType(position) == TYPE_OTHER_MESSAGE) {
                    holder.tvName.setText(message.getSenderName());
                }
                
                holder.itemView.setOnLongClickListener(v -> {
                    if (!message.isRecalled()) {
                        if (message.getSenderToken().equals(userToken)) {
                            showMyMessageOptions(message, position);
                        } else {
                            showOtherMessageOptions(message);
                        }
                    }
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessage message = messages.get(position);
            if (message.getType() == 1 || message.getType() == 2 || message.getType() == 3) {
                return TYPE_SYSTEM;
            }
            return message.getSenderToken().equals(userToken) ? TYPE_MY_MESSAGE : TYPE_OTHER_MESSAGE;
        }

        class MessageViewHolder extends RecyclerView.ViewHolder {
            TextView tvContent;
            TextView tvTime;
            TextView tvName;

            MessageViewHolder(View itemView) {
                super(itemView);
                tvContent = itemView.findViewById(R.id.tv_content);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvName = itemView.findViewById(R.id.tv_name);
            }
        }
    }

    private void showMyMessageOptions(ChatMessage message, int position) {
        String[] options = {"复制", "撤回"};
        new MaterialAlertDialogBuilder(this)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        copyToClipboard(message.getContent());
                        break;
                    case 1:
                        if (clientService != null) {
                            clientService.recallMessage(roomId, message.getId());
                        }
                        break;
                }
            })
            .show();
    }

    private void showOtherMessageOptions(ChatMessage message) {
        new MaterialAlertDialogBuilder(this)
            .setItems(new String[]{"复制"}, (dialog, which) -> {
                copyToClipboard(message.getContent());
            })
            .show();
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard = 
            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("message", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
        }
    }
}
