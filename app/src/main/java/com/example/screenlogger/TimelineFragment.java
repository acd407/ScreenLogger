package com.example.screenlogger;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TimelineFragment extends Fragment {
    private TextView noDataTextView;
    private TimelineView timelineView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_timeline, container, false);

        // 初始化UI组件
        timelineView = view.findViewById(R.id.timeline_view);
        noDataTextView = view.findViewById(R.id.no_data_text);

        // 更新时间线显示
        updateTimeline();

        return view;
    }

    public void updateTimeline() {
        if (getContext() == null) {
            return;
        }

        // 从数据库获取最近足够的事件以构建10次使用记录
        DatabaseHelper dbHelper = new DatabaseHelper(getContext());
        List<DatabaseHelper.ScreenEvent> eventsList = dbHelper.getLastTenUsagePeriodsEvents();

        // 根据是否有数据显示或隐藏相应的视图
        if (eventsList.isEmpty()) {
            timelineView.setVisibility(View.GONE);
            noDataTextView.setVisibility(TextView.VISIBLE);
        } else {
            timelineView.setVisibility(View.VISIBLE);
            noDataTextView.setVisibility(TextView.GONE);
            
            // 处理事件列表，生成使用时段数据
        List<UsagePeriod> usagePeriods = processEventsForTimeline(eventsList);
        
        // 转换为TimelineView需要的UsagePeriod列表
        List<TimelineView.UsagePeriod> timelinePeriods = new ArrayList<>();
        for (UsagePeriod period : usagePeriods) {
            timelinePeriods.add(new TimelineView.UsagePeriod(period.startTime, period.endTime, period.isUsed));
        }
        
        // 更新时间线视图
        timelineView.setUsagePeriods(timelinePeriods);
            timelineView.invalidate();
        }
    }

    // 处理事件列表，生成使用时段数据
    private List<UsagePeriod> processEventsForTimeline(List<DatabaseHelper.ScreenEvent> eventsList) {
        List<UsagePeriod> usagePeriods = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        // 按时间戳升序排序
        Collections.sort(eventsList, new Comparator<DatabaseHelper.ScreenEvent>() {
            @Override
            public int compare(DatabaseHelper.ScreenEvent event1, DatabaseHelper.ScreenEvent event2) {
                try {
                    Date date1 = sdf.parse(event1.getTimestamp());
                    Date date2 = sdf.parse(event2.getTimestamp());
                    return date1.compareTo(date2);
                } catch (ParseException e) {
                    e.printStackTrace();
                    return 0;
                }
            }
        });

        // 找出第一个事件和最后一个事件的时间
        long startTime = Long.MAX_VALUE;
        long endTime = Long.MIN_VALUE;
        
        // 计算所有事件中的最早和最晚时间
        for (DatabaseHelper.ScreenEvent event : eventsList) {
            try {
                Date eventDate = sdf.parse(event.getTimestamp());
                long eventTime = eventDate.getTime();
                startTime = Math.min(startTime, eventTime);
                endTime = Math.max(endTime, eventTime);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        // 处理事件，构建使用时段
        Date startDate = null;
        for (DatabaseHelper.ScreenEvent event : eventsList) {
            try {
                Date eventDate = sdf.parse(event.getTimestamp());
                long eventTime = eventDate.getTime();

                if (event.getEventType().equals(DatabaseHelper.EVENT_SCREEN_ON)) {
                    // 记录亮屏开始时间
                    startDate = eventDate;
                } else if (event.getEventType().equals(DatabaseHelper.EVENT_SCREEN_OFF) && startDate != null) {
                    // 找到匹配的熄屏事件，创建使用时段
                    usagePeriods.add(new UsagePeriod(startDate.getTime(), eventTime, true));
                    startDate = null;
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        // 如果最后一个事件是亮屏，那么从该时间到现在都是使用时段
        if (startDate != null) {
            usagePeriods.add(new UsagePeriod(startDate.getTime(), endTime, true));
        }

        // 添加未使用时段
        List<UsagePeriod> allPeriods = new ArrayList<>();
        long currentTime = startTime;

        // 对使用时段按时间排序（升序）
        Collections.sort(usagePeriods, new Comparator<UsagePeriod>() {
            @Override
            public int compare(UsagePeriod period1, UsagePeriod period2) {
                return Long.compare(period1.startTime, period2.startTime);
            }
        });
        
        // 限制为最近10次使用记录（如果超过的话）
        if (usagePeriods.size() > 10) {
            usagePeriods = usagePeriods.subList(usagePeriods.size() - 10, usagePeriods.size());
        }
        
        // 重新计算时间范围
        startTime = Long.MAX_VALUE;
        endTime = Long.MIN_VALUE;
        for (UsagePeriod period : usagePeriods) {
            startTime = Math.min(startTime, period.startTime);
            endTime = Math.max(endTime, period.endTime);
        }

        // 插入未使用时段
        for (UsagePeriod period : usagePeriods) {
            if (period.startTime > currentTime) {
                // 添加未使用时段
                allPeriods.add(new UsagePeriod(currentTime, period.startTime, false));
            }
            // 添加使用时段
            allPeriods.add(period);
            currentTime = period.endTime;
        }

        // 如果最后还有时间剩余，添加未使用时段
        if (currentTime < endTime) {
            allPeriods.add(new UsagePeriod(currentTime, endTime, false));
        }

        return allPeriods;
    }

    // 表示一个使用时段
    public static class UsagePeriod {
        long startTime;
        long endTime;
        boolean isUsed;

        public UsagePeriod(long startTime, long endTime, boolean isUsed) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.isUsed = isUsed;
        }
    }
}