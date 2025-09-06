package com.example.screenlogger;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ScreenEventsAdapter extends RecyclerView.Adapter<ScreenEventsAdapter.EventViewHolder> {

    private Context context;
    private List<DatabaseHelper.ScreenEvent> eventsList;

    public ScreenEventsAdapter(Context context, List<DatabaseHelper.ScreenEvent> eventsList) {
        this.context = context;
        this.eventsList = eventsList;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.event_item_row, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        DatabaseHelper.ScreenEvent event = eventsList.get(position);
        
        // 设置事件类型文本
        if (DatabaseHelper.EVENT_SCREEN_ON.equals(event.getEventType())) {
            holder.eventTypeTextView.setText(context.getString(R.string.screen_on_text));
            holder.eventTypeTextView.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
        } else if (DatabaseHelper.EVENT_SCREEN_OFF.equals(event.getEventType())) {
            holder.eventTypeTextView.setText(context.getString(R.string.screen_off_text));
            holder.eventTypeTextView.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
        }
        
        // 设置时间戳文本
        holder.timestampTextView.setText(event.getTimestamp());
        
        // 交替行背景颜色
        if (position % 2 == 0) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(android.R.color.background_light));
        } else {
            holder.itemView.setBackgroundColor(context.getResources().getColor(android.R.color.darker_gray));
        }
    }

    @Override
    public int getItemCount() {
        return eventsList != null ? eventsList.size() : 0;
    }

    // 更新数据列表
    public void setEventsList(List<DatabaseHelper.ScreenEvent> eventsList) {
        this.eventsList = eventsList;
        notifyDataSetChanged();
    }

    // ViewHolder类
    public static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView eventTypeTextView;
        TextView timestampTextView;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            eventTypeTextView = itemView.findViewById(R.id.event_type_text);
            timestampTextView = itemView.findViewById(R.id.timestamp_text);
        }
    }
}