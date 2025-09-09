#ifndef FORK_PROCESS_H
#define FORK_PROCESS_H

/**
 * 启动一个脱离AMS监控的fork进程
 * @param pid_file_path PID文件路径，用于保存和读取进程ID
 * @param brightness_file_path 亮度文件路径
 * @param db_path 数据库文件路径
 * @return 0表示成功，-1表示失败，1表示进程已在运行
 */
int start_forked_process(const char* pid_file_path, const char* brightness_file_path, const char* db_path);

/**
 * 停止forked进程
 * @param pid_file_path PID文件路径
 * @return 0表示成功，-1表示失败
 */
int stop_forked_process(const char* pid_file_path);

/**
 * 检查forked进程是否正在运行
 * @param pid_file_path PID文件路径
 * @return true表示进程正在运行，false表示进程未运行
 */
bool is_forked_process_running(const char* pid_file_path);

#endif // FORK_PROCESS_H