#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>

#define LOG_TAG "ScreenLoggerNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 声明外部函数
extern int start_forked_process(const char* pid_file_path, const char* brightness_file_path, const char* db_path);
extern int stop_forked_process(const char* pid_file_path);
extern bool is_forked_process_running(const char* pid_file_path);

// 定义JNI接口方法

/**
 * 启动NDK进程，使用多重fork脱离AMS监控
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_example_screenlogger_NativeProcessManager_startNativeProcess(
        JNIEnv* env,
        jobject /* this */,
        jstring pid_file_path_jstr,
        jstring brightness_file_path_jstr,
        jstring db_path_jstr) {

    const char* pid_file_path = env->GetStringUTFChars(pid_file_path_jstr, nullptr);
    const char* brightness_file_path = env->GetStringUTFChars(brightness_file_path_jstr, nullptr);
    const char* db_path = env->GetStringUTFChars(db_path_jstr, nullptr);

    LOGD("Starting native process with PID file: %s, Brightness file: %s, DB path: %s",
         pid_file_path, brightness_file_path, db_path);

    int result = start_forked_process(pid_file_path, brightness_file_path, db_path);

    // 添加短暂延迟，给工作进程足够时间创建PID文件
    if (result == 0) {
        LOGD("Waiting for PID file to be created...");
        usleep(200000); // 200毫秒
    }

    env->ReleaseStringUTFChars(pid_file_path_jstr, pid_file_path);
    env->ReleaseStringUTFChars(brightness_file_path_jstr, brightness_file_path);
    env->ReleaseStringUTFChars(db_path_jstr, db_path);

    return result;
}

/**
 * 停止NDK进程
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_example_screenlogger_NativeProcessManager_stopNativeProcess(
        JNIEnv* env,
        jobject /* this */,
        jstring pid_file_path_jstr) {

    const char* pid_file_path = env->GetStringUTFChars(pid_file_path_jstr, nullptr);

    LOGD("Stopping native process with PID file: %s", pid_file_path);

    int result = stop_forked_process(pid_file_path);

    env->ReleaseStringUTFChars(pid_file_path_jstr, pid_file_path);

    return result;
}

/**
 * 检查NDK进程是否运行
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_screenlogger_NativeProcessManager_isNativeProcessRunning(
        JNIEnv* env,
        jobject /* this */,
        jstring pid_file_path_jstr) {

    const char* pid_file_path = env->GetStringUTFChars(pid_file_path_jstr, nullptr);

    LOGD("Checking if native process is running with PID file: %s", pid_file_path);

    int result = is_forked_process_running(pid_file_path);

    env->ReleaseStringUTFChars(pid_file_path_jstr, pid_file_path);

    return result == 1;
}

/**
 * 获取当前屏幕亮度值
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_example_screenlogger_NativeProcessManager_getCurrentBrightness(
        JNIEnv* env,
        jobject /* this */,
        jstring brightness_file_path_jstr) {

    const char* brightness_file_path = env->GetStringUTFChars(brightness_file_path_jstr, nullptr);

    int brightness_value = -1;
    int fd = open(brightness_file_path, O_RDONLY);
    
    if (fd != -1) {
        char buffer[32];
        ssize_t bytes_read = read(fd, buffer, sizeof(buffer) - 1);
        if (bytes_read > 0) {
            buffer[bytes_read] = '\0';
            brightness_value = atoi(buffer);
            LOGD("Current brightness value: %d", brightness_value);
        } else {
            LOGE("Failed to read brightness file: %s", strerror(errno));
        }
        close(fd);
    } else {
        LOGE("Failed to open brightness file: %s", strerror(errno));
    }

    env->ReleaseStringUTFChars(brightness_file_path_jstr, brightness_file_path);

    return brightness_value;
}

/**
 * 获取NDK进程的PID
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_example_screenlogger_NativeProcessManager_getProcessPid(
        JNIEnv* env,
        jobject /* this */,
        jstring pid_file_path_jstr) {

    const char* pid_file_path = env->GetStringUTFChars(pid_file_path_jstr, nullptr);

    LOGD("Getting process PID from file: %s", pid_file_path);
    
    // 读取PID文件获取进程ID
    int fd = open(pid_file_path, O_RDONLY);
    int pid = -1;
    
    if (fd != -1) {
        char pid_str[32];
        ssize_t bytes_read = read(fd, pid_str, sizeof(pid_str) - 1);
        if (bytes_read > 0) {
            pid_str[bytes_read] = '\0';
            pid = atoi(pid_str);
            LOGD("Read PID: %d from file", pid);
        } else {
            LOGE("Failed to read PID file: %s", strerror(errno));
        }
        close(fd);
    } else {
        LOGE("Failed to open PID file: %s", strerror(errno));
    }

    env->ReleaseStringUTFChars(pid_file_path_jstr, pid_file_path);

    return pid;
}