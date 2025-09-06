package com.example.screenlogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ScreenStateReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            Log.d(TAG, "Received action: " + intent.getAction());
            
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                // 屏幕亮起
                ScreenStateService.saveScreenOnTime(context);
                // 确保服务正在运行
                Intent serviceIntent = new Intent(context, ScreenStateService.class);
                context.startService(serviceIntent);
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // 屏幕关闭
                ScreenStateService.saveScreenOffTime(context);
            }
        }
    }
}