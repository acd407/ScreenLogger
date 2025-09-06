package com.example.screenlogger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenStateService extends Service {

    private static final String TAG = "ScreenStateService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        // 服务在被杀死后会尝试重启
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void saveScreenOnTime(android.content.Context context) {
        String currentTime = getCurrentTime();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        dbHelper.insertScreenEvent(DatabaseHelper.EVENT_SCREEN_ON, currentTime);
        Log.d(TAG, "Screen on time saved: " + currentTime);
    }

    public static void saveScreenOffTime(android.content.Context context) {
        String currentTime = getCurrentTime();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        dbHelper.insertScreenEvent(DatabaseHelper.EVENT_SCREEN_OFF, currentTime);
        Log.d(TAG, "Screen off time saved: " + currentTime);
    }

    public static String getLastScreenOnTime(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        return dbHelper.getLastScreenOnTime();
    }

    public static String getLastScreenOffTime(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        return dbHelper.getLastScreenOffTime();
    }

    private static String getCurrentTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return dateFormat.format(new Date());
    }
}