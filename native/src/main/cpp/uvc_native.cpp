#include <jni.h>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <algorithm>
#include <sys/stat.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "UVC_NativeBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    struct IoRule {
        std::string sourcePrefix;
        std::string targetPrefix;
    };

    std::mutex g_ioMutex;
    std::vector<IoRule> g_ioRules;
    bool g_ioRedirectEnabled = false;

    std::mutex g_identityMutex;
    int g_virtualUserId = 0;
    int g_virtualInstanceId = 0;
    int g_virtualUid = -1;
    int g_virtualPid = -1;
    std::string g_virtualPackageName;
    bool g_hiddenApiUnlocked = false;

    std::string normalizePrefix(const std::string& path) {
        if (path.empty()) return path;
        if (path.size() > 1 && path.back() == '/') {
            return path.substr(0, path.size() - 1);
        }
        return path;
    }

    std::string applyRedirectInternal(const std::string& inputPath) {
        if (inputPath.empty()) return inputPath;
        std::lock_guard<std::mutex> lock(g_ioMutex);
        if (!g_ioRedirectEnabled || g_ioRules.empty()) {
            return inputPath;
        }

        // Rules are sorted by descending sourcePrefix length for longest-prefix matching
        for (const auto& rule : g_ioRules) {
            const std::string& src = rule.sourcePrefix;
            if (inputPath.compare(0, src.size(), src) == 0) {
                if (inputPath.size() == src.size()) {
                    return rule.targetPrefix;
                }
                if (inputPath[src.size()] == '/') {
                    // Prevent infinite redirect loops if path is already inside targetPrefix
                    if (inputPath.compare(0, rule.targetPrefix.size(), rule.targetPrefix) == 0) {
                        return inputPath;
                    }
                    return rule.targetPrefix + inputPath.substr(src.size());
                }
            }
        }
        return inputPath;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeGetVersion(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("1.0.0-uvc-core");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeIsAvailable(
        JNIEnv* /* env */,
        jobject /* this */) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeRuntimeInfo(
        JNIEnv* env,
        jobject /* this */) {
#if defined(__aarch64__)
    const char* abi = "arm64-v8a";
#elif defined(__arm__)
    const char* abi = "armeabi-v7a";
#elif defined(__x86_64__)
    const char* abi = "x86_64";
#elif defined(__i386__)
    const char* abi = "x86";
#else
    const char* abi = "unknown";
#endif

    std::string info = std::string("ABI: ") + abi +
                       ", Bridge: Active, HiddenAPI: " + (g_hiddenApiUnlocked ? "Unlocked" : "Standby") +
                       ", IORules: " + std::to_string(g_ioRules.size());
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeUnlockHiddenApi(
        JNIEnv* env,
        jobject /* this */) {
    if (g_hiddenApiUnlocked) {
        return JNI_TRUE;
    }

    jclass vmRuntimeClass = env->FindClass("dalvik/system/VMRuntime");
    if (vmRuntimeClass == nullptr) {
        env->ExceptionClear();
        LOGW("VMRuntime class not found");
        return JNI_FALSE;
    }

    jmethodID getRuntimeMethod = env->GetStaticMethodID(
            vmRuntimeClass, "getRuntime", "()Ldalvik/system/VMRuntime;");
    if (getRuntimeMethod == nullptr) {
        env->ExceptionClear();
        return JNI_FALSE;
    }

    jobject vmRuntimeObj = env->CallStaticObjectMethod(vmRuntimeClass, getRuntimeMethod);
    if (vmRuntimeObj == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        return JNI_FALSE;
    }

    jmethodID setExemptionsMethod = env->GetMethodID(
            vmRuntimeClass, "setHiddenApiExemptions", "([Ljava/lang/String;)V");
    if (setExemptionsMethod == nullptr) {
        env->ExceptionClear();
        return JNI_FALSE;
    }

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray exemptionsArray = env->NewObjectArray(1, stringClass, nullptr);
    jstring allPrefix = env->NewStringUTF("L");
    env->SetObjectArrayElement(exemptionsArray, 0, allPrefix);

    env->CallVoidMethod(vmRuntimeObj, setExemptionsMethod, exemptionsArray);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGW("setHiddenApiExemptions threw exception");
        return JNI_FALSE;
    }

    g_hiddenApiUnlocked = true;
    LOGI("ART Hidden API restrictions unlocked via JNI VMRuntime bridge");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeAddIoRule(
        JNIEnv* env,
        jobject /* this */,
        jstring sourcePrefixJ,
        jstring targetPrefixJ) {
    if (sourcePrefixJ == nullptr || targetPrefixJ == nullptr) return;

    const char* srcChars = env->GetStringUTFChars(sourcePrefixJ, nullptr);
    const char* dstChars = env->GetStringUTFChars(targetPrefixJ, nullptr);
    std::string src = normalizePrefix(srcChars ? srcChars : "");
    std::string dst = normalizePrefix(dstChars ? dstChars : "");
    if (srcChars) env->ReleaseStringUTFChars(sourcePrefixJ, srcChars);
    if (dstChars) env->ReleaseStringUTFChars(targetPrefixJ, dstChars);

    if (src.empty() || dst.empty()) return;

    std::lock_guard<std::mutex> lock(g_ioMutex);
    for (auto& existing : g_ioRules) {
        if (existing.sourcePrefix == src) {
            existing.targetPrefix = dst;
            g_ioRedirectEnabled = true;
            return;
        }
    }
    g_ioRules.push_back({src, dst});
    std::sort(g_ioRules.begin(), g_ioRules.end(), [](const IoRule& a, const IoRule& b) {
        return a.sourcePrefix.size() > b.sourcePrefix.size();
    });
    g_ioRedirectEnabled = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeClearIoRules(
        JNIEnv* /* env */,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_ioMutex);
    g_ioRules.clear();
    g_ioRedirectEnabled = false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeRedirectPath(
        JNIEnv* env,
        jobject /* this */,
        jstring pathJ) {
    if (pathJ == nullptr) return nullptr;
    const char* pathChars = env->GetStringUTFChars(pathJ, nullptr);
    if (pathChars == nullptr) return pathJ;
    std::string input(pathChars);
    env->ReleaseStringUTFChars(pathJ, pathChars);

    std::string redirected = applyRedirectInternal(input);
    return env->NewStringUTF(redirected.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeEnforceDexReadOnly(
        JNIEnv* env,
        jobject /* this */,
        jstring filePathJ) {
    if (filePathJ == nullptr) return JNI_FALSE;
    const char* pathChars = env->GetStringUTFChars(filePathJ, nullptr);
    if (pathChars == nullptr) return JNI_FALSE;
    std::string path(pathChars);
    env->ReleaseStringUTFChars(filePathJ, pathChars);

    int res = chmod(path.c_str(), S_IRUSR);
    return (res == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeSetVirtualIdentity(
        JNIEnv* env,
        jobject /* this */,
        jint userId,
        jint instanceId,
        jint virtualUid,
        jint virtualPid,
        jstring packageNameJ) {
    std::lock_guard<std::mutex> lock(g_identityMutex);
    g_virtualUserId = userId;
    g_virtualInstanceId = instanceId;
    g_virtualUid = virtualUid;
    g_virtualPid = virtualPid;
    if (packageNameJ != nullptr) {
        const char* pkgChars = env->GetStringUTFChars(packageNameJ, nullptr);
        if (pkgChars != nullptr) {
            g_virtualPackageName = pkgChars;
            env->ReleaseStringUTFChars(packageNameJ, pkgChars);
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeGetVirtualUid(
        JNIEnv* /* env */,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_identityMutex);
    return g_virtualUid > 0 ? g_virtualUid : static_cast<jint>(getuid());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_universal_virtual_nativebridge_NativeRuntimeBridge_nativeGetVirtualPid(
        JNIEnv* /* env */,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_identityMutex);
    return g_virtualPid > 0 ? g_virtualPid : static_cast<jint>(getpid());
}
