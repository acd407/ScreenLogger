package com.example.screenlogger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ScreenStateReceiver screenStateReceiver;
    private TableFragment tableFragment;
    private TimelineFragment timelineFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI组件
        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);

        // 初始化Fragment
        tableFragment = new TableFragment();
        timelineFragment = new TimelineFragment();

        // 设置ViewPager2适配器
        viewPager.setAdapter(new ScreenLoggerPagerAdapter(this));

        // 关联TabLayout和ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("事件表格");
            } else {
                tab.setText("使用时间线");
            }
        }).attach();

        // 检查并请求SYSTEM_ALERT_WINDOW权限（Android 10及以上需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, 101);
        } else {
            initScreenLogger();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // 用户授予了权限
                initScreenLogger();
            } else {
                // 用户拒绝了权限
                Toast.makeText(this, "需要悬浮窗权限才能监听屏幕状态", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void initScreenLogger() {
        // 初始化并注册屏幕状态接收器
        screenStateReceiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter);

        // 启动屏幕状态服务
        Intent serviceIntent = new Intent(this, ScreenStateService.class);
        startService(serviceIntent);

        // 更新UI显示最近的屏幕状态记录
        updateScreenEvents();
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
            } else {
                return timelineFragment;
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到这个页面时更新屏幕状态记录
        updateScreenEvents();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在Activity销毁时取消注册接收器，避免内存泄漏
        if (screenStateReceiver != null) {
            unregisterReceiver(screenStateReceiver);
        }
    }
}