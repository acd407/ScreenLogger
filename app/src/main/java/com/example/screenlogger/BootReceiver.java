package com.example.screenlogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed, starting service");
            // 设备启动完成后启动服务
            Intent serviceIntent = new Intent(context, ScreenStateService.class);
            context.startService(serviceIntent);
        }
    }
}