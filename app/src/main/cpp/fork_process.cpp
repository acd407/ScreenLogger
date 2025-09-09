#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <signal.h>
#include <string.h>
#include <errno.h>
#include <android/log.h>
#include <sys/prctl.h>
#include <cstdlib>
#include <cstdio>
#include "fork_process.h"

#define LOG_TAG "NativeProcess"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 声明外部函数
extern int start_monitoring_screen(const char* brightness_file_path, const char* db_path);

sig_atomic_t running = 1;

/**
 * 信号处理函数
 */
void signal_handler(int sig) {
    if (sig == SIGTERM || sig == SIGINT) {
        running = 0;
        LOGD("Received termination signal, shutting down...");
    }
}

/**
 * 创建pid文件
 */
int create_pid_file(const char* pid_file_path, pid_t pid) {
    int fd = open(pid_file_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd == -1) {
        LOGE("Failed to create pid file: %s", strerror(errno));
        return -1;
    }
    
    char pid_str[32];
    snprintf(pid_str, sizeof(pid_str), "%d\n", pid);
    
    if (write(fd, pid_str, strlen(pid_str)) != (ssize_t)strlen(pid_str)) {
        LOGE("Failed to write to pid file: %s", strerror(errno));
        close(fd);
        return -1;
    }
    
    close(fd);
    return 0;
}

/**
 * 从pid文件读取pid
 */
pid_t read_pid_file(const char* pid_file_path) {
    int fd = open(pid_file_path, O_RDONLY);
    if (fd == -1) {
        LOGE("Failed to open pid file: %s", strerror(errno));
        return -1;
    }
    
    char pid_str[32];
    ssize_t bytes_read = read(fd, pid_str, sizeof(pid_str) - 1);
    if (bytes_read <= 0) {
        LOGE("Failed to read pid file: %s", strerror(errno));
        close(fd);
        return -1;
    }
    
    pid_str[bytes_read] = '\0';
    pid_t pid = (pid_t)atoi(pid_str);
    
    close(fd);
    return pid;
}

/**
 * 删除pid文件
 */
int delete_pid_file(const char* pid_file_path) {
    if (unlink(pid_file_path) == -1) {
        LOGE("Failed to delete pid file: %s", strerror(errno));
        return -1;
    }
    return 0;
}

/**
 * 主工作函数，在子进程中执行
 */
void worker_process(const char* pid_file_path, const char* brightness_file_path, const char* db_path) {
    // 写入pid文件
    if (create_pid_file(pid_file_path, getpid()) != 0) {
        LOGE("Failed to create pid file, exiting...");
        exit(1);
    }
    
    // 设置信号处理
    signal(SIGTERM, signal_handler);
    signal(SIGINT, signal_handler);
    
    // 调整进程优先级（如果有权限）
    setpriority(PRIO_PROCESS, 0, -20);
    
    // 设置进程名，隐藏真实身份
    prctl(PR_SET_NAME, "screenlogger_worker");
    
    LOGD("Worker process started, PID: %d, monitoring brightness file: %s", 
         getpid(), brightness_file_path);
    
    // 启动屏幕监控
    start_monitoring_screen(brightness_file_path, db_path);
    
    // 清理pid文件
    delete_pid_file(pid_file_path);
    
    LOGD("Worker process exiting");
    exit(0);
}

/**
 * 启动多重fork的进程
 */
int start_forked_process(const char* pid_file_path, const char* brightness_file_path, const char* db_path) {
    // 检查进程是否已经在运行
    if (is_forked_process_running(pid_file_path)) {
        LOGD("Process is already running");
        return 1; // 进程已经在运行
    }
    
    // 第一次fork - 创建子进程
    pid_t pid1 = fork();
    
    if (pid1 < 0) {
        LOGE("First fork failed: %s", strerror(errno));
        return -1;
    }
    
    if (pid1 > 0) {
        // 父进程等待子进程退出
        waitpid(pid1, NULL, 0);
        LOGD("First parent process exiting");
        return 0; // 父进程成功退出
    }
    
    // 第一子进程 - 创建新会话
    if (setsid() < 0) {
        LOGE("setsid failed: %s", strerror(errno));
        exit(1);
    }
    
    // 第二次fork - 确保进程不会成为会话首进程
    pid_t pid2 = fork();
    
    if (pid2 < 0) {
        LOGE("Second fork failed: %s", strerror(errno));
        exit(1);
    }
    
    if (pid2 > 0) {
        // 第一子进程退出
        LOGD("First child process exiting");
        exit(0);
    }
    
    // 第二子进程 - 真正的工作进程
    // 切换到根目录，避免占用挂载点
    chdir("/");
    
    // 关闭所有文件描述符
    for (int i = 0; i < 1024; i++) {
        close(i);
    }
    
    // 重定向标准输入输出到/dev/null
    open("/dev/null", O_RDONLY);
    open("/dev/null", O_WRONLY);
    open("/dev/null", O_WRONLY);
    
    // 执行工作函数
    worker_process(pid_file_path, brightness_file_path, db_path);
    
    // 不应该执行到这里
    return -1;
}

/**
 * 停止forked进程
 */
int stop_forked_process(const char* pid_file_path) {
    pid_t pid = read_pid_file(pid_file_path);
    
    if (pid <= 0) {
        LOGE("Invalid PID or process not running");
        return -1;
    }
    
    // 发送终止信号
    if (kill(pid, SIGTERM) != 0) {
        LOGE("Failed to send SIGTERM: %s", strerror(errno));
        // 尝试强制终止
        if (kill(pid, SIGKILL) != 0) {
            LOGE("Failed to send SIGKILL: %s", strerror(errno));
            return -1;
        }
    }
    
    // 等待进程退出
    int status;
    waitpid(pid, &status, WNOHANG);
    
    // 删除pid文件
    delete_pid_file(pid_file_path);
    
    LOGD("Process %d stopped successfully", pid);
    return 0;
}

/**
 * 检查forked进程是否运行
 */
bool is_forked_process_running(const char* pid_file_path) {
    pid_t pid = read_pid_file(pid_file_path);
    
    if (pid <= 0) {
        return false; // 进程未运行
    }
    
    // 发送0信号检查进程是否存在
    if (kill(pid, 0) == 0) {
        return true; // 进程正在运行
    } else {
        // 进程不存在，删除pid文件
        delete_pid_file(pid_file_path);
        return false; // 进程未运行
    }
}