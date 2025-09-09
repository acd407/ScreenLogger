package com.example.screenlogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver"; // 用于日志记录

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            Log.d(TAG, "收到广播: " + intent.getAction());
            
            // 处理开机完成广播
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || 
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
                // 在设备启动完成后启动ScreenStateService（独立进程）
                Log.d(TAG, "设备启动完成，正在启动后台服务");
                
                // 根据Android版本选择启动服务的方式
                Intent serviceIntent = new Intent(context, ScreenStateService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8.0及以上版本使用startForegroundService
                    context.startForegroundService(serviceIntent);
                } else {
                    // Android 8.0以下版本使用startService
                    context.startService(serviceIntent);
                }
                
                Log.d(TAG, "后台服务已启动");
            }
        }
    }
}