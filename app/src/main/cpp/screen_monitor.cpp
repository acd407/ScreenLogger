#include <unistd.h>
#include <sys/epoll.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <string.h>
#include <errno.h>
#include <android/log.h>
#include <cstdlib>
#include <cstdio>
#include <ctime>
#include <sys/xattr.h>  // 用于扩展属性操作

// 尝试包含ACL头文件，如果可用
#ifdef HAVE_POSIX_ACL
#include <sys/acl.h>    // 用于ACL操作
#endif

#define LOG_TAG "ScreenLoggerMonitor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 全局变量
extern sig_atomic_t running;

#define MAX_EVENTS 1
#define EPOLL_TIMEOUT 10000 // 10秒

/**
 * 执行shell命令并获取结果
 */
int execute_shell_command(const char* command, char* output, size_t output_size) {
    FILE* pipe = popen(command, "r");
    if (!pipe) {
        LOGE("Failed to execute command: %s", command);
        return -1;
    }
    
    if (output && output_size > 0) {
        memset(output, 0, output_size);
        fgets(output, output_size, pipe);
    }
    
    int result = pclose(pipe);
    return result;
}

/**
 * 获取当前时间字符串
 */
void get_current_time_string(char* buffer, size_t buffer_size) {
    time_t now = time(nullptr);
    struct tm* local_time = localtime(&now);
    if (local_time) {
        strftime(buffer, buffer_size, "%Y-%m-%d %H:%M:%S", local_time);
    } else {
        snprintf(buffer, buffer_size, "%ld", now);
    }
}

/**
 * 将屏幕事件写入数据库
 */
int write_event_to_database(const char* db_path, const char* event_type, const char* timestamp) {
    char command[1024];
    
    // 使用sqlite3命令行工具写入数据库
    // 需要确保设备已安装sqlite3工具
    snprintf(command, sizeof(command), 
             "su -c \"sqlite3 %s 'INSERT INTO screen_events (event_type, timestamp) VALUES ('\\''%s\\'', '\\''%s\\''');'\"",
             db_path, event_type, timestamp);
    
    LOGD("Executing DB command: %s", command);
    
    char output[256];
    int result = execute_shell_command(command, output, sizeof(output));
    
    if (result != 0) {
        LOGE("Failed to write event to database: %s", output);
        return -1;
    }
    
    LOGD("Successfully wrote event to database: %s at %s", event_type, timestamp);
    return 0;
}

/**
 * 读取亮度文件值
 */
int read_brightness_value(const char* brightness_file_path) {
    int brightness_value = -1;
    int fd = open(brightness_file_path, O_RDONLY);
    
    if (fd != -1) {
        char buffer[32];
        memset(buffer, 0, sizeof(buffer));
        
        ssize_t bytes_read = read(fd, buffer, sizeof(buffer) - 1);
        if (bytes_read > 0) {
            brightness_value = atoi(buffer);
            LOGD("Read brightness value: %d", brightness_value);
        } else {
            LOGE("Failed to read brightness file: %s", strerror(errno));
        }
        
        close(fd);
    } else {
        LOGE("Failed to open brightness file: %s", strerror(errno));
    }
    
    return brightness_value;
}

/**
 * 使用xattr设置文件扩展属性
 * 
 * @param file_path 文件路径
 * @return 成功返回0，失败返回-1
 */
int set_file_xattr(const char* file_path) {
    // 设置扩展属性 - 这里我们可以添加自定义属性，例如应用特定的读取权限标识
    const char* attr_name = "user.screenlogger.access";
    const char* attr_value = "read_enabled";
    size_t attr_size = strlen(attr_value);
    
    // 尝试设置扩展属性
    int result = setxattr(file_path, attr_name, attr_value, attr_size, 0);
    
    if (result == 0) {
        LOGD("Successfully set extended attribute for %s", file_path);
        return 0;
    } else {
        LOGE("Failed to set extended attribute: %s", strerror(errno));
        return -1;
    }
}

/**
 * 尝试使用ACL设置文件访问控制列表
 * 
 * @param file_path 文件路径
 * @return 成功返回0，失败返回-1
 */
int set_file_acl(const char* file_path) {
    #ifdef HAVE_POSIX_ACL
    acl_t acl = NULL;
    acl_entry_t entry = NULL;
    
    // 获取现有ACL
    acl = acl_get_file(file_path, ACL_TYPE_ACCESS);
    if (acl == NULL) {
        // 如果不存在ACL，创建新的ACL
        acl = acl_init(1);
        if (acl == NULL) {
            LOGE("Failed to initialize ACL: %s", strerror(errno));
            return -1;
        }
    }
    
    // 添加读取权限条目
    if (acl_create_entry(&acl, &entry) != 0) {
        LOGE("Failed to create ACL entry: %s", strerror(errno));
        acl_free(acl);
        return -1;
    }
    
    // 设置条目为所有用户可读
    const acl_permset_t permset;
    acl_get_permset(entry, &permset);
    acl_add_perm(permset, ACL_READ);
    
    // 设置条目标志
    const acl_qualifier_t qualifier = ACL_UNDEFINED_ID;
    acl_set_qualifier(entry, qualifier);
    
    // 应用ACL到文件
    if (acl_set_file(file_path, ACL_TYPE_ACCESS, acl) != 0) {
        LOGE("Failed to set ACL: %s", strerror(errno));
        acl_free(acl);
        return -1;
    }
    
    LOGD("Successfully set ACL for %s", file_path);
    acl_free(acl);
    return 0;
    #else
    LOGD("ACL support not available");
    return -1;
    #endif
}

/**
 * 设置文件读取权限（支持多种方式）
 * 
 * 尝试顺序：
 * 1. 首先尝试使用xattr设置扩展属性
 * 2. 然后尝试使用ACL设置访问控制列表
 * 3. 最后回退到传统的chmod方式
 * 
 * @param file_path 文件路径
 * @return 成功返回0，所有方法都失败返回-1
 */
int set_file_permissions(const char* file_path) {
    // 1. 首先尝试使用xattr
    if (set_file_xattr(file_path) == 0) {
        return 0;
    }
    
    // 2. 然后尝试使用ACL
    if (set_file_acl(file_path) == 0) {
        return 0;
    }
    
    // 3. 最后回退到传统的chmod方式
    char command[512];
    snprintf(command, sizeof(command), "su -c \"chmod 644 %s\"", file_path);
    
    LOGD("Falling back to chmod: %s", command);
    
    char output[256];
    int result = execute_shell_command(command, output, sizeof(output));
    
    if (result != 0) {
        LOGE("Failed to set file permissions with all methods: %s", output);
        return -1;
    }
    
    LOGD("Successfully set file permissions with chmod for %s", file_path);
    return 0;
}

/**
 * 启动屏幕监控
 */
int start_monitoring_screen(const char* brightness_file_path, const char* db_path) {
    // 首先尝试获取文件读取权限
    if (set_file_permissions(brightness_file_path) != 0) {
        LOGE("Failed to get file permissions, monitoring may not work");
    }
    
    // 初始化epoll
    int epoll_fd = epoll_create1(0);
    if (epoll_fd == -1) {
        LOGE("Failed to create epoll instance: %s", strerror(errno));
        return -1;
    }
    
    // 打开亮度文件
    int brightness_fd = open(brightness_file_path, O_RDONLY);
    if (brightness_fd == -1) {
        LOGE("Failed to open brightness file for monitoring: %s", strerror(errno));
        close(epoll_fd);
        return -1;
    }
    
    // 设置文件描述符为非阻塞
    int flags = fcntl(brightness_fd, F_GETFL, 0);
    if (flags == -1) {
        LOGE("Failed to get file flags: %s", strerror(errno));
        close(brightness_fd);
        close(epoll_fd);
        return -1;
    }
    
    if (fcntl(brightness_fd, F_SETFL, flags | O_NONBLOCK) == -1) {
        LOGE("Failed to set non-blocking flag: %s", strerror(errno));
        close(brightness_fd);
        close(epoll_fd);
        return -1;
    }
    
    // 注册文件描述符到epoll
    struct epoll_event event;
    event.data.fd = brightness_fd;
    event.events = EPOLLIN | EPOLLPRI | EPOLLERR | EPOLLHUP;
    
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, brightness_fd, &event) == -1) {
        LOGE("Failed to add file descriptor to epoll: %s", strerror(errno));
        close(brightness_fd);
        close(epoll_fd);
        return -1;
    }
    
    // 初始化屏幕状态
    int prev_brightness = read_brightness_value(brightness_file_path);
    int is_screen_on = (prev_brightness > 0);
    
    LOGD("Screen monitoring started. Initial brightness: %d, Screen on: %s", 
         prev_brightness, is_screen_on ? "yes" : "no");
    
    // 主监控循环
    struct epoll_event events[MAX_EVENTS];
    while (running) {
        // 等待事件发生
        int num_events = epoll_wait(epoll_fd, events, MAX_EVENTS, EPOLL_TIMEOUT);
        
        if (num_events == -1) {
            if (errno == EINTR) {
                // 被信号中断，继续循环
                continue;
            }
            LOGE("epoll_wait failed: %s", strerror(errno));
            break;
        }
        
        // 检查亮度文件变化
        if (num_events > 0 && events[0].data.fd == brightness_fd) {
            // 重新读取亮度值
            int current_brightness = read_brightness_value(brightness_file_path);
            int current_screen_on = (current_brightness > 0);
            
            // 检查屏幕状态是否变化
            if (current_screen_on != is_screen_on) {
                char timestamp[32];
                get_current_time_string(timestamp, sizeof(timestamp));
                
                const char* event_type = current_screen_on ? "SCREEN_ON" : "SCREEN_OFF";
                LOGD("Screen state changed: %s at %s (Brightness: %d)", 
                     event_type, timestamp, current_brightness);
                
                // 将事件写入数据库
                write_event_to_database(db_path, event_type, timestamp);
                
                // 更新屏幕状态
                is_screen_on = current_screen_on;
            }
            
            // 清除事件，重新读取文件
            lseek(brightness_fd, 0, SEEK_SET);
            char buffer[32];
            read(brightness_fd, buffer, sizeof(buffer));
        }
        
        // 定期检查权限，防止权限被系统重置
        static int check_count = 0;
        if (++check_count >= 60) { // 大约每分钟检查一次
            check_count = 0;
            set_file_permissions(brightness_file_path);
        }
    }
    
    // 清理资源
    close(brightness_fd);
    close(epoll_fd);
    
    LOGD("Screen monitoring stopped");
    return 0;
}