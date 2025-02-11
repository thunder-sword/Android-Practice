cmake_minimum_required(VERSION 3.4.1)

# 构建本地库
add_library(opus-lib SHARED opus-lib.cpp)

# 添加预编译的 Opus 库（确保 libopus.so 已放入 jniLibs 目录）
add_library(opus SHARED IMPORTED)
set_target_properties(opus PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopus.so)

find_library(log-lib log)

target_link_libraries(opus-lib opus ${log-lib})