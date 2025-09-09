package com.example.screenlogger;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenStateService extends Service {

    private static final String TAG = "ScreenStateService"; // 用于日志记录
    private ScreenStateReceiver screenStateReceiver; // 屏幕状态接收器
    private boolean isServiceRunning = false; // 服务运行状态标记

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "后台服务创建，PID: " + android.os.Process.myPid());
        
        // 使用root权限调整当前进程的oom_adj值
        adjustOomAdjWithRoot();
        
        // 注册屏幕状态接收器
        registerScreenReceiver();
        
        isServiceRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "后台服务启动");
        
        // 尝试通过fork方式脱离AMS管理
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            try {
                forkProcess();
            } catch (Exception e) {
                Log.e(TAG, "fork进程失败: " + e.getMessage());
            }
        }
        
        // 设置服务为前台服务以提高优先级（可选，如果需要更稳定的运行）
        // startForeground(1, new Notification.Builder(this, "channel_id").build());
        
        // 服务在被杀死后会尝试重启
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // 不支持绑定服务
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "后台服务销毁");
        
        // 取消注册接收器
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (Exception e) {
                Log.e(TAG, "取消注册接收器失败: " + e.getMessage());
            }
        }
        
        isServiceRunning = false;
    }

    /**
     * 注册屏幕状态接收器
     */
    private void registerScreenReceiver() {
        screenStateReceiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        
        try {
            registerReceiver(screenStateReceiver, filter);
            Log.d(TAG, "屏幕状态接收器注册成功");
        } catch (Exception e) {
            Log.e(TAG, "屏幕状态接收器注册失败: " + e.getMessage());
        }
    }

    /**
     * 使用root权限调整当前进程的oom_adj值
     */
    private void adjustOomAdjWithRoot() {
        try {
            int pid = android.os.Process.myPid();
            
            // 执行root命令调整oom_adj值
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            
            // 将进程的oom_adj设置为-17，这是系统进程的级别
            os.writeBytes("echo -17 > /proc/" + pid + "/oom_score_adj\n");
            os.writeBytes("exit\n");
            os.flush();
            
            int result = process.waitFor();
            
            if (result == 0) {
                Log.d(TAG, "Root权限获取成功，OOM_ADJ值已调整");
                
                // 验证调整结果
                Process verifyProcess = Runtime.getRuntime().exec("cat /proc/" + pid + "/oom_score_adj");
                BufferedReader reader = new BufferedReader(new InputStreamReader(verifyProcess.getInputStream()));
                String oomAdjValue = reader.readLine();
                reader.close();
                
                Log.d(TAG, "当前进程OOM_ADJ值: " + oomAdjValue);
            } else {
                Log.e(TAG, "Root权限获取失败，无法调整OOM_ADJ值");
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "调整OOM_ADJ值时出错: " + e.getMessage());
        }
    }

    /**
     * 尝试通过fork创建子进程以脱离AMS管理
     */
    private void forkProcess() {
        try {
            // 在Android中，直接使用Java的ProcessBuilder来模拟fork行为
            // 注意：这种方式在Android 8.0以上系统可能受到限制
            ProcessBuilder builder = new ProcessBuilder("/system/bin/app_process", "/system/bin", "com.example.screenlogger.ForkedProcess");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            
            Log.d(TAG, "已创建fork进程");
        } catch (Exception e) {
            Log.e(TAG, "创建fork进程失败: " + e.getMessage());
        }
    }

    /**
     * 内部广播接收器，用于监听屏幕状态变化
     */
    private class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                Log.d(TAG, "收到屏幕状态广播: " + intent.getAction());
                
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    // 屏幕亮起，保存记录
                    saveScreenOnTime(context);
                } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    // 屏幕关闭，保存记录
                    saveScreenOffTime(context);
                }
            }
        }
    }

    /**
     * 保存屏幕亮起时间
     */
    public static void saveScreenOnTime(Context context) {
        String currentTime = getCurrentTime();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        dbHelper.insertScreenEvent(DatabaseHelper.EVENT_SCREEN_ON, currentTime);
        Log.d(TAG, "屏幕亮起时间已保存: " + currentTime);
    }

    /**
     * 保存屏幕熄灭时间
     */
    public static void saveScreenOffTime(Context context) {
        String currentTime = getCurrentTime();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        dbHelper.insertScreenEvent(DatabaseHelper.EVENT_SCREEN_OFF, currentTime);
        Log.d(TAG, "屏幕熄灭时间已保存: " + currentTime);
    }

    /**
     * 获取最后一次屏幕亮起时间
     */
    public static String getLastScreenOnTime(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        return dbHelper.getLastScreenOnTime();
    }

    /**
     * 获取最后一次屏幕熄灭时间
     */
    public static String getLastScreenOffTime(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        return dbHelper.getLastScreenOffTime();
    }

    /**
     * 获取当前时间字符串
     */
    private static String getCurrentTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return dateFormat.format(new Date());
    }
}