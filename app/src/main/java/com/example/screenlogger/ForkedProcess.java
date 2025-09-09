package com.example.screenlogger;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 这个类用于创建一个脱离AMS管理的独立进程
 * 通过app_process启动，而不是通过常规的Android组件方式
 */
public class ForkedProcess {

    private static final String TAG = "ForkedProcess"; // 用于日志记录
    private static boolean isRunning = false; // 进程运行状态标记
    private static Context appContext; // 应用上下文
    
    /**
     * 主入口方法，由app_process调用
     */
    public static void main(String[] args) {
        Log.d(TAG, "ForkedProcess启动，PID: " + android.os.Process.myPid());
        
        isRunning = true;
        
        try {
            // 调整当前进程的oom_adj值
            adjustOomAdjWithRoot();
            
            // 启动一个无限循环保持进程运行
            while (isRunning) {
                try {
                    // 每秒检查一次是否需要继续运行
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "线程中断: " + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "ForkedProcess运行出错: " + e.getMessage());
        }
        
        Log.d(TAG, "ForkedProcess退出");
    }
    
    /**
     * 使用root权限调整当前进程的oom_adj值
     */
    private static void adjustOomAdjWithRoot() {
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
                
                Log.d(TAG, "ForkedProcess OOM_ADJ值: " + oomAdjValue);
            } else {
                Log.e(TAG, "Root权限获取失败，无法调整OOM_ADJ值");
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "调整OOM_ADJ值时出错: " + e.getMessage());
        }
    }
    
    /**
     * 设置应用上下文
     */
    public static void setApplicationContext(Context context) {
        appContext = context;
    }
    
    /**
     * 停止进程
     */
    public static void stop() {
        isRunning = false;
    }
}