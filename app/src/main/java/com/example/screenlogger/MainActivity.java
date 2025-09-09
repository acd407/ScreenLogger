package com.example.screenlogger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private TableFragment tableFragment;
    private TimelineFragment timelineFragment;
    private ProcessControlFragment processControlFragment;
    private NativeProcessManager nativeProcessManager;
    private ScheduledExecutorService statusChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI组件
        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);
        
        // 初始化NativeProcessManager
        nativeProcessManager = new NativeProcessManager(this);
        
        // 初始化Fragment
        tableFragment = new TableFragment();
        timelineFragment = new TimelineFragment();
        processControlFragment = new ProcessControlFragment();

        // 设置ViewPager2适配器
        viewPager.setAdapter(new ScreenLoggerPagerAdapter(this));

        // 关联TabLayout和ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("事件表格");
            } else if (position == 1) {
                tab.setText("使用时间线");
            } else {
                tab.setText("进程控制");
            }
        }).attach();

        // 仅显示数据，不进行任何保活操作
        Toast.makeText(this, "正在加载屏幕使用记录...", Toast.LENGTH_SHORT).show();
        updateScreenEvents();
        
        // 初始化状态检查器
        statusChecker = Executors.newSingleThreadScheduledExecutor();
        statusChecker.scheduleAtFixedRate(this::updateProcessStatus, 0, 1, TimeUnit.SECONDS);
    }

    private void updateScreenEvents() {
        // 更新两个Fragment的数据
        if (tableFragment != null) {
            tableFragment.updateScreenEvents();
        }
        if (timelineFragment != null) {
            timelineFragment.updateTimeline();
        }
    }

    // ViewPager2适配器
    private class ScreenLoggerPagerAdapter extends FragmentStateAdapter {
        public ScreenLoggerPagerAdapter(FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return tableFragment;
            } else if (position == 1) {
                return timelineFragment;
            } else {
                return processControlFragment;
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到这个页面时更新屏幕状态记录
        updateScreenEvents();
        updateProcessStatus();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (statusChecker != null) {
            statusChecker.shutdown();
        }
    }
    

    
    /**
     * 更新进程状态显示和刷新事件数据
     */
    private void updateProcessStatus() {
        if (processControlFragment != null) {
            processControlFragment.updateUIState();
        }
        
        // 定期刷新事件数据，确保事件表格能够反映最新状态
        // 每5次状态检查刷新一次事件数据，避免过于频繁的数据库操作
        refreshCounter = (refreshCounter + 1) % 5;
        if (refreshCounter == 0) {
            updateScreenEvents();
        }
    }
    
    // 计数器，用于控制事件数据刷新频率
    private int refreshCounter = 0;
    
    /**
     * 获取NativeProcessManager实例
     * 供ProcessControlFragment使用
     */
    public NativeProcessManager getProcessManager() {
        return nativeProcessManager;
    }
    

}