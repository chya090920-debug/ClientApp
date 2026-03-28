package com.chat.client.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.client.R;
import com.chat.client.model.ChatRoom;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    private List<ChatRoom> rooms = new ArrayList<>();
    private final OnRoomClickListener listener;
    private SimpleDateFormat timeFormat;

    public interface OnRoomClickListener {
        void onRoomClick(ChatRoom room);
    }

    public RoomAdapter(OnRoomClickListener listener) {
        this.listener = listener;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }

    public void setRooms(List<ChatRoom> rooms) {
        this.rooms = rooms != null ? rooms : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_room, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        ChatRoom room = rooms.get(position);
        holder.tvRoomName.setText(room.getRoomName());
        holder.tvLastMessage.setText(room.getLastMessage());
        holder.tvTime.setText(timeFormat.format(new Date(room.getLastMessageTime())));
        
        if (room.getUnreadCount() > 0) {
            holder.tvUnread.setVisibility(View.VISIBLE);
            holder.tvUnread.setText(String.valueOf(room.getUnreadCount()));
        } else {
            holder.tvUnread.setVisibility(View.GONE);
        }
        
        // 公共聊天室置顶
        if (room.getRoomId().equals("000000000000")) {
            holder.itemView.setBackgroundColor(0xFFF0F8FF);
        } else {
            holder.itemView.setBackgroundColor(0xFFFFFFFF);
        }
        
        holder.itemView.setOnClickListener(v -> listener.onRoomClick(room));
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView tvRoomName;
        TextView tvLastMessage;
        TextView tvTime;
        TextView tvUnread;

        RoomViewHolder(View itemView) {
            super(itemView);
            tvRoomName = itemView.findViewById(R.id.tv_room_name);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvUnread = itemView.findViewById(R.id.tv_unread);
        }
    }
}
