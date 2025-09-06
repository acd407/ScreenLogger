package com.example.screenlogger;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TableFragment extends Fragment {
    private RecyclerView eventsRecyclerView;
    private ScreenEventsAdapter eventsAdapter;
    private TextView noDataTextView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_table, container, false);

        // 初始化UI组件
        eventsRecyclerView = view.findViewById(R.id.events_recycler_view);
        noDataTextView = view.findViewById(R.id.no_data_text);

        // 初始化RecyclerView
        initRecyclerView();

        // 更新UI显示最近的屏幕状态记录
        updateScreenEvents();

        return view;
    }

    private void initRecyclerView() {
        // 设置RecyclerView的布局管理器
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        eventsRecyclerView.setLayoutManager(layoutManager);

        // 隐藏分割线（通过行布局的背景色交替实现表格效果）
        eventsRecyclerView.addItemDecoration(new androidx.recyclerview.widget.DividerItemDecoration(getContext(), 
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL));

        // 初始化适配器
        DatabaseHelper dbHelper = new DatabaseHelper(getContext());
        List<DatabaseHelper.ScreenEvent> eventsList = dbHelper.getRecentScreenEvents();
        eventsAdapter = new ScreenEventsAdapter(getContext(), eventsList);
        eventsRecyclerView.setAdapter(eventsAdapter);
    }

    public void updateScreenEvents() {
        if (getContext() == null) {
            return;
        }

        // 从数据库获取最近12小时内的记录
        DatabaseHelper dbHelper = new DatabaseHelper(getContext());
        List<DatabaseHelper.ScreenEvent> eventsList = dbHelper.getRecentScreenEvents();

        // 更新适配器数据
        eventsAdapter.setEventsList(eventsList);

        // 根据是否有数据显示或隐藏相应的视图
        if (eventsList.isEmpty()) {
            eventsRecyclerView.setVisibility(RecyclerView.GONE);
            noDataTextView.setVisibility(TextView.VISIBLE);
        } else {
            eventsRecyclerView.setVisibility(RecyclerView.VISIBLE);
            noDataTextView.setVisibility(TextView.GONE);
        }
    }
}