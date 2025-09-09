package com.example.screenlogger;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.Timer;
import java.util.TimerTask;

public class ProcessControlFragment extends Fragment {
    private TextView tvProcessStatus;
    private EditText tvLogOutput;
    private Button btnStartProcess;
    private Button btnStopProcess;
    private NativeProcessManager processManager;
    private Handler mainHandler;
    private Timer logRefreshTimer;
    private static final String LOG_TAG = "ProcessControlFragment";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        // 初始化日志定时器，定期刷新日志显示
        logRefreshTimer = new Timer();
        logRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshLogDisplay();
            }
        }, 1000, 2000); // 1秒后开始，每2秒刷新一次
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_process_control, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI components
        tvProcessStatus = view.findViewById(R.id.tv_process_status);
        tvLogOutput = view.findViewById(R.id.tv_log_output);
        btnStartProcess = view.findViewById(R.id.btn_start_process);
        btnStopProcess = view.findViewById(R.id.btn_stop_process);

        // Get the process manager instance
        if (getActivity() instanceof MainActivity) {
            processManager = ((MainActivity) getActivity()).getProcessManager();
        }

        // Set button click listeners
        btnStartProcess.setOnClickListener(v -> startNativeProcess());
        btnStopProcess.setOnClickListener(v -> stopNativeProcess());

        // Update initial UI state and log display
        updateUIState();
        refreshLogDisplay();
    }

    private void startNativeProcess() {
        if (processManager != null && !processManager.isNativeProcessRunning()) {
            appendLog("启动NDK进程...");
            processManager.writeLog("用户请求启动NDK进程");
            int result = processManager.startNativeProcess();
            updateUIState();
            
            String resultMessage = "未知错误";
            switch (result) {
                case 0: resultMessage = "NDK进程启动成功";
                    break;
                case 1: resultMessage = "NDK进程已在运行";
                    break;
                default: resultMessage = "NDK进程启动失败";
            }
            appendLog(resultMessage);
            processManager.writeLog(resultMessage);
        }
    }

    private void stopNativeProcess() {
        if (processManager != null && processManager.isNativeProcessRunning()) {
            appendLog("停止NDK进程...");
            processManager.writeLog("用户请求停止NDK进程");
            int result = processManager.stopNativeProcess();
            updateUIState();
            
            String resultMessage = result == 0 ? "NDK进程停止成功" : "NDK进程停止失败";
            appendLog(resultMessage);
            processManager.writeLog(resultMessage);
        }
    }

    public void updateUIState() {
        if (processManager != null) {
            boolean isRunning = processManager.isNativeProcessRunning();
            int pid = processManager.getProcessPid();
            mainHandler.post(() -> {
                if (isRunning) {
                    if (pid > 0) {
                        tvProcessStatus.setText("NDK进程：运行中 (PID: " + pid + ")");
                    } else {
                        tvProcessStatus.setText("NDK进程：运行中 (无法获取PID)");
                    }
                } else {
                    tvProcessStatus.setText("NDK进程：已停止");
                }
                btnStartProcess.setEnabled(!isRunning);
                btnStopProcess.setEnabled(isRunning);
            });
        }
    }

    /**
     * 刷新日志显示
     */
    private void refreshLogDisplay() {
        if (processManager != null) {
            String logContent = processManager.readLog();
            final String finalLogContent = logContent.isEmpty() ? "暂无日志信息\n" : logContent;
            mainHandler.post(() -> {
                if (tvLogOutput != null) {
                    // 仅当日志内容发生变化时更新，避免频繁刷新UI
                    String currentText = tvLogOutput.getText().toString();
                    if (!currentText.equals(finalLogContent)) {
                        tvLogOutput.setText(finalLogContent);
                        // Auto-scroll to the bottom
                        tvLogOutput.setSelection(tvLogOutput.getText().length());
                    }
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 取消日志刷新定时器，避免内存泄漏
        if (logRefreshTimer != null) {
            logRefreshTimer.cancel();
            logRefreshTimer = null;
        }
    }

    public void appendLog(String logMessage) {
        final String timestampedMessage = getCurrentTimestamp() + " " + logMessage + "\n";
        mainHandler.post(() -> {
            if (tvLogOutput != null) {
                tvLogOutput.append(timestampedMessage);
                // Auto-scroll to the bottom
                tvLogOutput.post(() -> {
                    tvLogOutput.setSelection(tvLogOutput.getText().length());
                });
            }
        });
    }

    private String getCurrentTimestamp() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
        return "[" + sdf.format(new java.util.Date()) + "]";
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUIState();
    }
}