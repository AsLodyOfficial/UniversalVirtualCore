cmake_minimum_required(VERSION 3.22.1)
project("uvc_native")

add_library(
    uvc_native
    SHARED
    src/main/cpp/uvc_native.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    uvc_native
    
)
