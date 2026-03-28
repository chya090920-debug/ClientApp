package com.chat.client.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.client.R;
import com.chat.client.adapter.RoomAdapter;
import com.chat.client.model.ChatRoom;
import com.chat.client.service.WebSocketClientService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity implements 
        WebSocketClientService.ConnectionListener,
        WebSocketClientService.RoomListener,
        RoomAdapter.OnRoomClickListener {

    private TextView tvConnectionStatus;
    private RecyclerView rvRooms;
    private FloatingActionButton fabAdd;
    private View layoutConnecting;

    private WebSocketClientService clientService;
    private boolean isBound = false;
    private RoomAdapter roomAdapter;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WebSocketClientService.ClientBinder binder = (WebSocketClientService.ClientBinder) service;
            clientService = binder.getService();
            clientService.setConnectionListener(MainActivity.this);
            clientService.setRoomListener(MainActivity.this);
            isBound = true;
            updateRoomList();
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
        setContentView(R.layout.activity_main);

        initViews();
        startAndBindService();
    }

    private void initViews() {
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        rvRooms = findViewById(R.id.rv_rooms);
        fabAdd = findViewById(R.id.fab_add);
        layoutConnecting = findViewById(R.id.layout_connecting);

        roomAdapter = new RoomAdapter(this);
        rvRooms.setLayoutManager(new LinearLayoutManager(this));
        rvRooms.setAdapter(roomAdapter);

        fabAdd.setOnClickListener(v -> showAddRoomDialog());
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, WebSocketClientService.class);
        startService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onConnectionStatusChanged(WebSocketClientService.ConnectionStatus status) {
        runOnUiThread(() -> {
            switch (status) {
                case CONNECTING:
                    tvConnectionStatus.setText("连接中...");
                    tvConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
                    layoutConnecting.setVisibility(View.VISIBLE);
                    break;
                case CONNECTED:
                    tvConnectionStatus.setText("已连接");
                    tvConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark));
                    layoutConnecting.setVisibility(View.GONE);
                    break;
                case DISCONNECTED:
                    tvConnectionStatus.setText("已断开");
                    tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                    layoutConnecting.setVisibility(View.GONE);
                    break;
                case RECONNECTING:
                    tvConnectionStatus.setText("重连中...");
                    tvConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
                    layoutConnecting.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }

    @Override
    public void onRoomJoined(ChatRoom room) {
        runOnUiThread(this::updateRoomList);
    }

    @Override
    public void onRoomCreated(String roomId) {
        runOnUiThread(() -> {
            if (clientService != null) {
                clientService.joinRoom(roomId, "");
            }
        });
    }

    @Override
    public void onRoomLeft(String roomId) {
        runOnUiThread(this::updateRoomList);
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_LONG).show());
    }

    private void updateRoomList() {
        if (clientService != null) {
            List<ChatRoom> rooms = clientService.getRooms();
            roomAdapter.setRooms(rooms);
        }
    }

    @Override
    public void onRoomClick(ChatRoom room) {
        Intent intent = new Intent(this, ChatRoomActivity.class);
        intent.putExtra("roomId", room.getRoomId());
        intent.putExtra("roomName", room.getRoomName());
        startActivity(intent);
    }

    private void showAddRoomDialog() {
        String[] options = {"加入聊天室", "创建聊天室"};
        new MaterialAlertDialogBuilder(this)
            .setTitle("选择操作")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showJoinRoomDialog();
                        break;
                    case 1:
                        showCreateRoomDialog();
                        break;
                }
            })
            .show();
    }

    private void showJoinRoomDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_join_room, null);
        android.widget.EditText etRoomId = dialogView.findViewById(R.id.et_room_id);
        android.widget.EditText etPassword = dialogView.findViewById(R.id.et_password);

        new MaterialAlertDialogBuilder(this)
            .setTitle("加入聊天室")
            .setView(dialogView)
            .setPositiveButton("加入", (dialog, which) -> {
                String roomId = etRoomId.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                
                if (roomId.length() != 12 || !roomId.matches("\\d{12}")) {
                    Toast.makeText(this, "请输入12位数字ID", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (clientService != null) {
                    clientService.joinRoom(roomId, password);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showCreateRoomDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);
        android.widget.EditText etPassword = dialogView.findViewById(R.id.et_password);
        android.widget.CheckBox cbUsePassword = dialogView.findViewById(R.id.cb_use_password);

        cbUsePassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etPassword.setEnabled(isChecked);
            if (!isChecked) etPassword.setText("");
        });

        new MaterialAlertDialogBuilder(this)
            .setTitle("创建聊天室")
            .setView(dialogView)
            .setPositiveButton("创建", (dialog, which) -> {
                String password = "";
                if (cbUsePassword.isChecked()) {
                    password = etPassword.getText().toString().trim();
                }
                if (clientService != null) {
                    clientService.createRoom(password);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRoomList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
        }
    }
}
