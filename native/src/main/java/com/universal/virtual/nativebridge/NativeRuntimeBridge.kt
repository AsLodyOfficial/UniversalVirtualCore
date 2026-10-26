package com.universal.virtual.nativebridge

import java.io.File
import java.util.concurrent.ConcurrentHashMap

object NativeRuntimeBridge {

    private var isLoaded = false
    private var loadError: Throwable? = null
    private val jvmFallbackIoRules = ConcurrentHashMap<String, String>()
    @Volatile
    private var jvmVirtualUid: Int = 10000
    @Volatile
    private var jvmVirtualPid: Int = 10100

    init {
        try {
            System.loadLibrary("uvc_native")
            isLoaded = true
        } catch (t: Throwable) {
            isLoaded = false
            loadError = t
        }
    }

    fun isAvailable(): Boolean = isLoaded && try {
        nativeIsAvailable()
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    fun getVersion(): String = if (isLoaded) {
        try {
            nativeGetVersion()
        } catch (_: UnsatisfiedLinkError) {
            "unavailable"
        }
    } else {
        "unavailable"
    }

    fun getRuntimeInfo(): String = if (isLoaded) {
        try {
            nativeRuntimeInfo()
        } catch (_: UnsatisfiedLinkError) {
            "unavailable"
        }
    } else {
        "unavailable"
    }

    fun unlockHiddenApi(): Boolean {
        if (isLoaded) {
            try {
                if (nativeUnlockHiddenApi()) return true
            } catch (_: UnsatisfiedLinkError) {
            }
        }
        return try {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime")
            val runtime = getRuntime.invoke(null)
            val setHiddenApiExemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            setHiddenApiExemptions.invoke(runtime, arrayOf("L"))
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun addIoRule(sourcePrefix: String, targetPrefix: String) {
        val normSrc = sourcePrefix.trimEnd('/')
        val normDst = targetPrefix.trimEnd('/')
        if (normSrc.isEmpty() || normDst.isEmpty()) return
        jvmFallbackIoRules[normSrc] = normDst
        if (isLoaded) {
            try {
                nativeAddIoRule(normSrc, normDst)
            } catch (_: UnsatisfiedLinkError) {
            }
        }
    }

    fun clearIoRules() {
        jvmFallbackIoRules.clear()
        if (isLoaded) {
            try {
                nativeClearIoRules()
            } catch (_: UnsatisfiedLinkError) {
            }
        }
    }

    fun redirectPath(path: String): String {
        if (path.isBlank()) return path
        if (isLoaded) {
            try {
                return nativeRedirectPath(path)
            } catch (_: UnsatisfiedLinkError) {
            }
        }
        // Sorted by longest prefix first on JVM fallback
        val sortedRules = jvmFallbackIoRules.entries.sortedByDescending { it.key.length }
        for ((src, dst) in sortedRules) {
            if (path == src) return dst
            if (path.startsWith("$src/")) {
                if (path.startsWith(dst)) return path
                return dst + path.substring(src.length)
            }
        }
        return path
    }

    fun enforceDexReadOnly(file: File): Boolean {
        if (!file.exists()) return false
        var success = file.setReadOnly()
        if (isLoaded) {
            try {
                success = nativeEnforceDexReadOnly(file.absolutePath) || success
            } catch (_: UnsatisfiedLinkError) {
            }
        }
        return success
    }

    fun setVirtualIdentity(
        userId: Int,
        instanceId: Int,
        virtualUid: Int,
        virtualPid: Int,
        packageName: String
    ) {
        jvmVirtualUid = virtualUid
        jvmVirtualPid = virtualPid
        if (isLoaded) {
            try {
                nativeSetVirtualIdentity(userId, instanceId, virtualUid, virtualPid, packageName)
            } catch (_: UnsatisfiedLinkError) {
            }
        }
    }

    fun getVirtualUid(): Int {
        if (isLoaded) {
            try {
                return nativeGetVirtualUid()
            } catch (_: UnsatisfiedLinkError) {
            }
        }
        return jvmVirtualUid
    }

    fun getVirtualPid(): Int {
        if (isLoaded) {
            try {
                return nativeGetVirtualPid()
            } catch (_: UnsatisfiedLinkError) {
            }
        }
        return jvmVirtualPid
    }

    fun getLoadError(): Throwable? = loadError

    private external fun nativeGetVersion(): String
    private external fun nativeIsAvailable(): Boolean
    private external fun nativeRuntimeInfo(): String
    private external fun nativeUnlockHiddenApi(): Boolean
    private external fun nativeAddIoRule(sourcePrefix: String, targetPrefix: String)
    private external fun nativeClearIoRules()
    private external fun nativeRedirectPath(path: String): String
    private external fun nativeEnforceDexReadOnly(filePath: String): Boolean
    private external fun nativeSetVirtualIdentity(
        userId: Int,
        instanceId: Int,
        virtualUid: Int,
        virtualPid: Int,
        packageName: String
    )
    private external fun nativeGetVirtualUid(): Int
    private external fun nativeGetVirtualPid(): Int
}
