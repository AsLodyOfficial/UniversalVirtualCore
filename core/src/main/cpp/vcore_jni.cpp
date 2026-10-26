#include <jni.h>
#include "logging.hpp"
#include "page_size.hpp"
#include "native_hook.hpp"
#include "path_mapper.hpp"
#include "virtual_fs.hpp"
#include "native_loader.hpp"
#include "maps_virtualizer.hpp"
#include "env_masker.hpp"
#include "diagnostic_engine.hpp"
#include "block_anogs.hpp"

using namespace vcore::memory;
using namespace vcore::fs;
using namespace vcore::runtime;
using namespace vcore::cloaking;
using namespace vcore::diagnostics;

#define SECMAGIC 0xdeadbeef

#if defined(__aarch64__)
static uint64_t OriSyscall(uint64_t num, uint64_t a1, uint64_t a2, uint64_t a3, uint64_t a4, uint64_t a5, uint64_t a6) {
    uint64_t x0;
    __asm__ volatile (
        "mov x8, %1\n\t"
        "mov x0, %2\n\t"
        "mov x1, %3\n\t"
        "mov x2, %4\n\t"
        "mov x3, %5\n\t"
        "mov x4, %6\n\t"
        "mov x5, %7\n\t"
        "svc #0\n\t"
        "mov %0, x0\n\t"
        : "=r"(x0)
        : "r"(num), "r"(a1), "r"(a2), "r"(a3), "r"(a4), "r"(a5), "r"(a6)
        : "x8", "x0", "x1", "x2", "x3", "x4", "x5"
    );
    return x0;
}
#elif defined(__arm__)
static uint32_t OriSyscall(uint32_t num, uint32_t a1, uint32_t a2, uint32_t a3, uint32_t a4, uint32_t a5, uint32_t a6) {
    uint32_t x0;
    __asm__ volatile (
        "mov r7, %1\n\t"
        "mov r0, %2\n\t"
        "mov r1, %3\n\t"
        "mov r2, %4\n\t"
        "mov r3, %5\n\t"
        "mov r4, %6\n\t"
        "mov r5, %7\n\t"
        "svc #0\n\t"
        "mov %0, r0\n\t"
        : "=r"(x0)
        : "r"(num), "r"(a1), "r"(a2), "r"(a3), "r"(a4), "r"(a5), "r"(a6)
        : "r7", "r0", "r1", "r2", "r3", "r4", "r5"
    );
    return x0;
}
#endif

static void seccomp_sig_callback(int signo, siginfo_t* info, void* data) {
    (void)signo;
    (void)info;
#if defined(__aarch64__)
    unsigned long syscall_number = ((ucontext_t*)data)->uc_mcontext.regs[8];
    unsigned long a1 = ((ucontext_t*)data)->uc_mcontext.regs[0];
    unsigned long a2 = ((ucontext_t*)data)->uc_mcontext.regs[1];
    unsigned long a3 = ((ucontext_t*)data)->uc_mcontext.regs[2];
    unsigned long a4 = ((ucontext_t*)data)->uc_mcontext.regs[3];
    if (syscall_number == __NR_openat) {
        int fd = (int)a1;
        const char* pathname = (const char*)a2;
        int flags = (int)a3;
        int mode = (int)a4;
        ((ucontext_t*)data)->uc_mcontext.regs[0] = OriSyscall(__NR_openat, fd, (uint64_t)pathname, flags, mode, SECMAGIC, SECMAGIC);
    }
#elif defined(__arm__)
    unsigned long syscall_number = ((ucontext_t*)data)->uc_mcontext.arm_r7;
    unsigned long a1 = ((ucontext_t*)data)->uc_mcontext.arm_r0;
    unsigned long a2 = ((ucontext_t*)data)->uc_mcontext.arm_r1;
    unsigned long a3 = ((ucontext_t*)data)->uc_mcontext.arm_r2;
    unsigned long a4 = ((ucontext_t*)data)->uc_mcontext.arm_r3;
    if (syscall_number == __NR_openat) {
        int fd = (int)a1;
        const char* pathname = (const char*)a2;
        int flags = (int)a3;
        int mode = (int)a4;
        ((ucontext_t*)data)->uc_mcontext.arm_r0 = OriSyscall(__NR_openat, fd, (uint32_t)pathname, flags, mode, SECMAGIC, SECMAGIC);
    }
#else
    (void)data;
#endif
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeInit(
    JNIEnv* env, jclass clazz, jint apiLevel) {
    size_t pageSize = PageSizeManager::GetRuntimePageSize();
    EnvMasker::EnableAntiDetection();
    LOGI("Alok NativeBridge v3.0 initialized: API %d, PageSize=%zu (%s), Cloaking Active",
         apiLevel, pageSize, PageSizeManager::Is16KbPageSize() ? "16KB Mode" : "4KB Mode");
}

JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeInitSeccomp(
    JNIEnv* env, jclass clazz) {
#if defined(__aarch64__) || defined(__arm__)
    struct sock_filter filter[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 2),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[4])),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, SECMAGIC, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP)
    };

    struct sock_fprog prog;
    prog.filter = filter;
    prog.len = (unsigned short)(sizeof(filter) / sizeof(filter[0]));

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sigset_t sigset;
    sigfillset(&sigset);
    sa.sa_sigaction = seccomp_sig_callback;
    sa.sa_mask = sigset;
    sa.sa_flags = SA_SIGINFO;

    if (sigaction(SIGSYS, &sa, nullptr) == -1) return;
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) == -1) return;
    if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &prog) == -1) return;
    LOGI("Alok Seccomp CVM filter initialized successfully");
#endif
}

JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeNukeTargetLibrary(
    JNIEnv* env, jclass clazz, jstring libName, jint layerMask) {
    if (!libName) return;
    const char* utf = env->GetStringUTFChars(libName, nullptr);
    if (!utf) return;
    std::string name(utf);
    env->ReleaseStringUTFChars(libName, utf);
    uint32_t mask = layerMask < 0 ? NUKE_LAYERS_ALL : static_cast<uint32_t>(layerMask);
    std::thread([name, mask]() { nukeLibrary(name, mask); }).detach();
}

JNIEXPORT jlong JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeGetPageSize(
    JNIEnv* env, jclass clazz) {
    return static_cast<jlong>(PageSizeManager::GetRuntimePageSize());
}

JNIEXPORT jboolean JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeIs16KbPage(
    JNIEnv* env, jclass clazz) {
    return PageSizeManager::Is16KbPageSize() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeAddPathRule(
    JNIEnv* env, jclass clazz, jstring vPrefix, jstring hostTarget) {
    const char* c_v = env->GetStringUTFChars(vPrefix, nullptr);
    const char* c_h = env->GetStringUTFChars(hostTarget, nullptr);
    if (c_v && c_h) {
        PathMapper::GetInstance().AddRule(std::string(c_v), std::string(c_h));
    }
    if (c_v) env->ReleaseStringUTFChars(vPrefix, c_v);
    if (c_h) env->ReleaseStringUTFChars(hostTarget, c_h);
}

JNIEXPORT jstring JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeResolvePath(
    JNIEnv* env, jclass clazz, jstring origPath) {
    if (!origPath) return nullptr;
    const char* c_path = env->GetStringUTFChars(origPath, nullptr);
    std::string resolved = PathMapper::GetInstance().Resolve(std::string(c_path));
    env->ReleaseStringUTFChars(origPath, c_path);
    return env->NewStringUTF(resolved.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeInitUserStorage(
    JNIEnv* env, jclass clazz, jint userId, jstring pkgName, jstring basePath) {
    const char* c_pkg = env->GetStringUTFChars(pkgName, nullptr);
    const char* c_base = env->GetStringUTFChars(basePath, nullptr);
    bool ok = false;
    if (c_pkg && c_base) {
        ok = VirtualFileSystem::InitializeUserStorage(userId, std::string(c_pkg), std::string(c_base));
    }
    if (c_pkg) env->ReleaseStringUTFChars(pkgName, c_pkg);
    if (c_base) env->ReleaseStringUTFChars(basePath, c_base);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeGetPrimaryAbi(
    JNIEnv* env, jclass clazz) {
    std::string abi = NativeLoader::GetPrimaryAbi();
    return env->NewStringUTF(abi.c_str());
}

// Cloaking & Anti-Detection JNI
JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeSetupCloaking(
    JNIEnv* env, jclass clazz, jstring guestPkg, jstring hostPkg) {
    const char* c_guest = env->GetStringUTFChars(guestPkg, nullptr);
    const char* c_host = env->GetStringUTFChars(hostPkg, nullptr);
    if (c_guest) {
        MapsVirtualizer::GetInstance().SetGuestPackage(std::string(c_guest));
        env->ReleaseStringUTFChars(guestPkg, c_guest);
    }
    if (c_host) {
        MapsVirtualizer::GetInstance().SetHostPackage(std::string(c_host));
        env->ReleaseStringUTFChars(hostPkg, c_host);
    }
    EnvMasker::MaskTracerPid();
    LOGI("Native cloaking configured for guest app.");
}

JNIEXPORT jstring JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeFilterMaps(
    JNIEnv* env, jclass clazz, jstring rawMaps) {
    if (!rawMaps) return nullptr;
    const char* c_raw = env->GetStringUTFChars(rawMaps, nullptr);
    std::string processed = MapsVirtualizer::GetInstance().ProcessMapsContent(std::string(c_raw));
    env->ReleaseStringUTFChars(rawMaps, c_raw);
    return env->NewStringUTF(processed.c_str());
}

// Native In-Memory Hooking JNI
JNIEXPORT jlong JNICALL
Java_com_universal_virtualcore_native_1core_NativeHookEngine_nativeFindSymbol(
    JNIEnv* env, jclass clazz, jstring libName, jstring symName) {
    const char* c_lib = libName ? env->GetStringUTFChars(libName, nullptr) : nullptr;
    const char* c_sym = env->GetStringUTFChars(symName, nullptr);
    uintptr_t addr = NativeHook::FindSymbolAddress(c_lib ? std::string(c_lib) : "", std::string(c_sym));
    if (c_lib) env->ReleaseStringUTFChars(libName, c_lib);
    if (c_sym) env->ReleaseStringUTFChars(symName, c_sym);
    return static_cast<jlong>(addr);
}

JNIEXPORT jboolean JNICALL
Java_com_universal_virtualcore_native_1core_NativeHookEngine_nativePatchMemory(
    JNIEnv* env, jclass clazz, jlong address, jbyteArray bytes) {
    if (!address || !bytes) return JNI_FALSE;
    jsize len = env->GetArrayLength(bytes);
    jbyte* buffer = env->GetByteArrayElements(bytes, nullptr);
    bool ok = NativeHook::PatchMemory(
        static_cast<uintptr_t>(address),
        reinterpret_cast<const uint8_t*>(buffer),
        static_cast<size_t>(len)
    );
    env->ReleaseByteArrayElements(bytes, buffer, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Diagnostic Engine & Logcat Generator JNI
JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeInitDiagnostics(
    JNIEnv* env, jclass clazz, jstring logDir) {
    const char* c_dir = logDir ? env->GetStringUTFChars(logDir, nullptr) : nullptr;
    if (c_dir) {
        DiagnosticEngine::get().initialize(std::string(c_dir));
        env->ReleaseStringUTFChars(logDir, c_dir);
    }
}

JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeStartDiagnostics(
    JNIEnv* env, jclass clazz) {
    DiagnosticEngine::get().startMonitoring();
}

JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeStopDiagnostics(
    JNIEnv* env, jclass clazz) {
    DiagnosticEngine::get().stopMonitoring();
}

JNIEXPORT void JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeUpdateDiagnosticsFrame(
    JNIEnv* env, jclass clazz, jfloat dt) {
    DiagnosticEngine::get().updateFrame(static_cast<float>(dt));
}

JNIEXPORT jstring JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeGetDiagnosticReport(
    JNIEnv* env, jclass clazz, jstring appName, jint activeContainers) {
    const char* c_app = appName ? env->GetStringUTFChars(appName, nullptr) : "UniversalVirtualCore";
    std::string report = DiagnosticEngine::get().generateJSONReport(std::string(c_app), activeContainers);
    if (appName) env->ReleaseStringUTFChars(appName, c_app);
    return env->NewStringUTF(report.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeGetMemoryRssKb(
    JNIEnv* env, jclass clazz) {
    return static_cast<jlong>(DiagnosticEngine::get().getMemoryStats().vm_rss_kb);
}

JNIEXPORT jlong JNICALL
Java_com_universal_virtualcore_native_1core_NativeBridge_nativeGetMemoryPeakKb(
    JNIEnv* env, jclass clazz) {
    return static_cast<jlong>(DiagnosticEngine::get().getMemoryStats().vm_peak_kb);
}

} // extern "C"