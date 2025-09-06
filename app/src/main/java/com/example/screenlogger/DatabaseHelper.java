package com.example.screenlogger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "screen_logger.db";
    private static final int DATABASE_VERSION = 1;

    // 表名和列名
    public static final String TABLE_NAME = "screen_events";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_EVENT_TYPE = "event_type";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    // 事件类型
    public static final String EVENT_SCREEN_ON = "SCREEN_ON";
    public static final String EVENT_SCREEN_OFF = "SCREEN_OFF";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_EVENT_TYPE + " TEXT NOT NULL, " +
            COLUMN_TIMESTAMP + " TEXT NOT NULL" +
            ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
        Log.d(TAG, "Database table created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 如果数据库版本更新，删除旧表并创建新表
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    // 插入一条屏幕事件记录
    public void insertScreenEvent(String eventType, String timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_EVENT_TYPE, eventType);
        values.put(COLUMN_TIMESTAMP, timestamp);
        
        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        Log.d(TAG, "Inserted screen event: " + eventType + " at " + timestamp + " with ID: " + id);
    }

    // 获取最近12小时内的所有屏幕事件记录
    public List<ScreenEvent> getRecentScreenEvents() {
        List<ScreenEvent> events = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        // 计算12小时前的时间戳
        long twelveHoursAgo = System.currentTimeMillis() - (12 * 60 * 60 * 1000);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String twelveHoursAgoStr = sdf.format(new Date(twelveHoursAgo));
        
        // 查询12小时内的记录，按时间戳降序排列
        String selectQuery = "SELECT * FROM " + TABLE_NAME + 
                             " WHERE " + COLUMN_TIMESTAMP + " >= ? " +
                             " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        
        Cursor cursor = db.rawQuery(selectQuery, new String[]{twelveHoursAgoStr});
        
        if (cursor.moveToFirst()) {
            do {
                ScreenEvent event = new ScreenEvent();
                
                // 安全地获取列索引
                int idColumnIndex = cursor.getColumnIndex(COLUMN_ID);
                int typeColumnIndex = cursor.getColumnIndex(COLUMN_EVENT_TYPE);
                int timeColumnIndex = cursor.getColumnIndex(COLUMN_TIMESTAMP);
                
                if (idColumnIndex != -1) {
                    event.setId(cursor.getInt(idColumnIndex));
                }
                if (typeColumnIndex != -1) {
                    event.setEventType(cursor.getString(typeColumnIndex));
                }
                if (timeColumnIndex != -1) {
                    event.setTimestamp(cursor.getString(timeColumnIndex));
                }
                
                events.add(event);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        
        return events;
    }

    // 获取最后一次屏幕亮屏时间
    public String getLastScreenOnTime() {
        return getLastEventTime(EVENT_SCREEN_ON);
    }

    // 获取最后一次屏幕熄屏时间
    public String getLastScreenOffTime() {
        return getLastEventTime(EVENT_SCREEN_OFF);
    }

    // 获取指定事件类型的最后一次发生时间
    private String getLastEventTime(String eventType) {
        SQLiteDatabase db = this.getReadableDatabase();
        String lastTime = null;
        
        String selectQuery = "SELECT " + COLUMN_TIMESTAMP + " FROM " + TABLE_NAME +
                             " WHERE " + COLUMN_EVENT_TYPE + " = ? " +
                             " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT 1";
        
        Cursor cursor = db.rawQuery(selectQuery, new String[]{eventType});
        
        if (cursor.moveToFirst()) {
            lastTime = cursor.getString(0);
        }
        
        cursor.close();
        db.close();
        
        return lastTime;
    }

    // 删除所有记录（用于测试）
    public void deleteAllEvents() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        db.close();
        Log.d(TAG, "All events deleted");
    }
    
    // 获取最近足够的事件以构建10次使用记录
    public List<ScreenEvent> getLastTenUsagePeriodsEvents() {
        List<ScreenEvent> events = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        // 获取最近30个事件（足够构建10次使用记录），按时间戳降序排列
        String selectQuery = "SELECT * FROM " + TABLE_NAME + 
                             " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT 30";
        
        Cursor cursor = db.rawQuery(selectQuery, null);
        
        if (cursor.moveToFirst()) {
            do {
                ScreenEvent event = new ScreenEvent();
                
                // 安全地获取列索引
                int idColumnIndex = cursor.getColumnIndex(COLUMN_ID);
                int typeColumnIndex = cursor.getColumnIndex(COLUMN_EVENT_TYPE);
                int timeColumnIndex = cursor.getColumnIndex(COLUMN_TIMESTAMP);
                
                if (idColumnIndex != -1) {
                    event.setId(cursor.getInt(idColumnIndex));
                }
                if (typeColumnIndex != -1) {
                    event.setEventType(cursor.getString(typeColumnIndex));
                }
                if (timeColumnIndex != -1) {
                    event.setTimestamp(cursor.getString(timeColumnIndex));
                }
                
                events.add(event);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        
        // 由于我们是按降序获取的，现在需要反转列表以按时间顺序处理
        Collections.reverse(events);
        
        return events;
    }

    // 屏幕事件实体类
    public static class ScreenEvent {
        private int id;
        private String eventType;
        private String timestamp;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}