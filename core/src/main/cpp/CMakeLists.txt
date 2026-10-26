cmake_minimum_required(VERSION 3.22.1)
project("vcore")

# Enforce 16 KB max page size for Android 16 compatibility on ARM64
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    .
    memory
    filesystem
    runtime
    utils
    cloaking
    diagnostics
)

add_library(
    vcore
    SHARED
    vcore_jni.cpp
    memory/page_size.cpp
    memory/native_hook.cpp
    filesystem/path_mapper.cpp
    filesystem/virtual_fs.cpp
    runtime/native_loader.cpp
    cloaking/maps_virtualizer.cpp
    cloaking/env_masker.cpp
    diagnostics/diagnostic_engine.cpp
)

find_library(log-lib log)

target_link_libraries(
    vcore
    ${log-lib}
)