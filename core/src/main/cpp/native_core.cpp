#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <unistd.h>
#include <android/log.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <sys/stat.h>

#define TAG "VCore-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_mutex;
static std::map<std::string, std::string> g_path_rules;
static bool g_io_sandbox_enabled = false;

// 16KB-safe memory page helper for Android 16
static size_t get_safe_page_size() {
    long sz = sysconf(_SC_PAGESIZE);
    return (sz > 0) ? (size_t)sz : 16384;
}

static std::string redirect_path(const std::string& orig_path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    for (const auto& [src, target] : g_path_rules) {
        if (orig_path.rfind(src, 0) == 0) {
            std::string res = target + orig_path.substr(src.length());
            LOGI("Redirecting [%s] -> [%s]", orig_path.c_str(), res.c_str());
            return res;
        }
    }
    return orig_path;
}

extern "C" JNIEXPORT void JNICALL
Java_com_universal_virtualcore_VirtualCore_nativeInit(
    JNIEnv* env,
    jclass clazz,
    jint apiLevel) {
    LOGI("VirtualCore native initialized for API %d (Page Size: %zu bytes)", apiLevel, get_safe_page_size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_universal_virtualcore_VirtualCore_nativeAddPathRule(
    JNIEnv* env,
    jclass clazz,
    jstring srcPath,
    jstring targetPath) {
    const char* c_src = env->GetStringUTFChars(srcPath, nullptr);
    const char* c_target = env->GetStringUTFChars(targetPath, nullptr);

    if (c_src && c_target) {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_path_rules[std::string(c_src)] = std::string(c_target);
        LOGI("Added IO Rule: %s => %s", c_src, c_target);
    }

    if (c_src) env->ReleaseStringUTFChars(srcPath, c_src);
    if (c_target) env->ReleaseStringUTFChars(targetPath, c_target);
}

extern "C" JNIEXPORT void JNICALL
Java_com_universal_virtualcore_VirtualCore_nativeEnableIOSandbox(
    JNIEnv* env,
    jclass clazz,
    jboolean enable) {
    g_io_sandbox_enabled = enable;
    LOGI("IO Sandbox status: %s", enable ? "ENABLED" : "DISABLED");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_universal_virtualcore_VirtualCore_nativeGetRedirectedPath(
    JNIEnv* env,
    jclass clazz,
    jstring origPath) {
    if (!origPath) return nullptr;
    const char* c_orig = env->GetStringUTFChars(origPath, nullptr);
    std::string redirected = redirect_path(std::string(c_orig));
    env->ReleaseStringUTFChars(origPath, c_orig);
    return env->NewStringUTFString(redirected.c_str());
}
