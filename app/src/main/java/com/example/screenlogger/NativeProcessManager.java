package com.example.screenlogger;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * NativeProcessManager类 - 用于管理NDK进程的启动、停止和状态检查
 */
public class NativeProcessManager {
    private static final String TAG = "NativeProcessManager";
    private static final String NATIVE_LIB_NAME = "screenlogger-native";
    
    // 静态加载native库
    static {
        try {
            System.loadLibrary(NATIVE_LIB_NAME);
            Log.d(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
        }
    }
    
    private final Context context;
    private final String pidFilePath;
    private final String brightnessFilePath;
    private final String dbFilePath;
    private final String logFilePath;
    
    /**
     * 构造函数
     * @param context 上下文对象
     */
    public NativeProcessManager(Context context) {
        this.context = context;
        
        // 设置pid文件路径
        File filesDir = context.getFilesDir();
        this.pidFilePath = new File(filesDir, "screenlogger.pid").getAbsolutePath();
        
        // 设置亮度文件路径
        this.brightnessFilePath = "/sys/class/backlight/panel0-backlight/brightness";
        
        // 设置数据库文件路径
        // 直接使用硬编码的数据库名称，因为DatabaseHelper.DATABASE_NAME是private的
        this.dbFilePath = new File(context.getDatabasePath("screen_logger.db").getPath()).getAbsolutePath();
        
        // 设置日志文件路径
        this.logFilePath = new File(filesDir, "screenlogger.log").getAbsolutePath();
        
        Log.d(TAG, "PID file path: " + pidFilePath);
        Log.d(TAG, "Brightness file path: " + brightnessFilePath);
        Log.d(TAG, "Database file path: " + dbFilePath);
        Log.d(TAG, "Log file path: " + logFilePath);
    }
    
    /**
     * 启动NDK进程
     * @return 0表示成功，-1表示失败，1表示进程已在运行
     */
    public int startNativeProcess() {
        Log.d(TAG, "Starting native process");
        writeLog("正在启动NDK进程...");
        writeLog("PID文件路径: " + pidFilePath);
        writeLog("亮度文件路径: " + brightnessFilePath);
        writeLog("数据库文件路径: " + dbFilePath);
        
        int result = startNativeProcess(pidFilePath, brightnessFilePath, dbFilePath);
        
        if (result == 0) {
            int pid = getProcessPid(pidFilePath);
            writeLog("NDK进程启动成功，PID: " + pid);
        } else if (result == 1) {
            int pid = getProcessPid(pidFilePath);
            writeLog("NDK进程已在运行，PID: " + pid);
        } else {
            writeLog("NDK进程启动失败，错误码: " + result);
        }
        
        return result;
    }
    
    /**
     * 停止NDK进程
     * @return 0表示成功，-1表示失败
     */
    public int stopNativeProcess() {
        Log.d(TAG, "Stopping native process");
        writeLog("正在停止NDK进程...");
        
        int pid = getProcessPid(pidFilePath);
        if (pid > 0) {
            writeLog("目标进程PID: " + pid);
        }
        
        int result = stopNativeProcess(pidFilePath);
        
        if (result == 0) {
            writeLog("NDK进程停止成功");
        } else {
            writeLog("NDK进程停止失败，错误码: " + result);
        }
        
        return result;
    }
    
    /**
     * 检查NDK进程是否运行
     * @return true表示进程正在运行，false表示进程未运行
     */
    public boolean isNativeProcessRunning() {
        boolean isRunning = isNativeProcessRunning(pidFilePath);
        Log.d(TAG, "Native process running: " + isRunning);
        return isRunning;
    }
    
    /**
     * 获取NDK进程的PID
     * @return 进程PID，如果进程未运行则返回-1
     */
    public int getProcessPid() {
        return getProcessPid(pidFilePath);
    }
    
    /**
     * 获取当前屏幕亮度值
     * @return 亮度值，-1表示获取失败
     */
    public int getCurrentBrightness() {
        return getCurrentBrightness(brightnessFilePath);
    }

    /**
     * 写入日志信息到日志文件
     * @param logMessage 要写入的日志信息
     */
    public void writeLog(String logMessage) {
        try {
            // 格式化时间戳
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());
            String formattedLog = "[" + timestamp + "] " + logMessage + "\n";
            
            // 写入日志文件（追加模式）
            try (FileOutputStream fos = new FileOutputStream(logFilePath, true)) {
                fos.write(formattedLog.getBytes());
            }
            
            // 同时记录到Android日志系统
            Log.d(TAG, logMessage);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log: " + e.getMessage());
        }
    }

    /**
     * 读取日志文件内容
     * @return 日志内容字符串
     */
    public String readLog() {
        StringBuilder logContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logContent.append(line).append("\n");
            }
        } catch (IOException e) {
            // 如果文件不存在，返回空字符串而不是抛出异常
            if (!new File(logFilePath).exists()) {
                return "";
            }
            Log.e(TAG, "Failed to read log: " + e.getMessage());
            return "读取日志失败: " + e.getMessage();
        }
        return logContent.toString();
    }

    /**
     * 清空日志文件
     * @return true表示成功，false表示失败
     */
    public boolean clearLog() {
        try {
            // 创建空文件覆盖现有日志文件
            new FileOutputStream(logFilePath, false).close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to clear log: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取日志文件路径
     * @return 日志文件的绝对路径
     */
    public String getLogFilePath() {
        return logFilePath;
    }
    
    // JNI本地方法声明
    private native int startNativeProcess(String pidFilePath, String brightnessFilePath, String dbFilePath);
    private native int stopNativeProcess(String pidFilePath);
    private native boolean isNativeProcessRunning(String pidFilePath);
    private native int getCurrentBrightness(String brightnessFilePath);
    private native int getProcessPid(String pidFilePath);
}