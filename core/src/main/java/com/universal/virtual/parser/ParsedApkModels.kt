package com.universal.virtual.parser

import java.io.File
import java.io.Serializable

data class ParsedApk(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int = 34,
    val originalApkFile: File,
    val originalApkSize: Long,
    val application: ParsedApplication,
    val activities: List<ParsedActivity> = emptyList(),
    val services: List<ParsedService> = emptyList(),
    val providers: List<ParsedProvider> = emptyList(),
    val receivers: List<ParsedReceiver> = emptyList(),
    val permissions: List<ParsedPermission> = emptyList(),
    val features: List<ParsedFeature> = emptyList(),
    val nativeLibraries: List<ParsedNativeLibrary> = emptyList()
) : Serializable {
    val primaryAbi: String
        get() = nativeLibraries.firstOrNull { it.abi == "arm64-v8a" }?.abi
            ?: nativeLibraries.firstOrNull()?.abi
            ?: "none"

    val hasNativeLibraries: Boolean get() = nativeLibraries.isNotEmpty()
}

data class ParsedApplication(
    val label: String,
    val className: String? = null,
    val iconResId: Int = 0,
    val flags: Int = 0,
    val isEnabled: Boolean = true
) : Serializable

data class ParsedActivity(
    val name: String,
    val label: String? = null,
    val launchMode: Int = 0,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val permission: String? = null,
    val theme: String? = null,
    val screenOrientation: Int = -1,
    val configChanges: Int = 0,
    val intentActions: List<String> = emptyList()
) : Serializable

data class ParsedService(
    val name: String,
    val permission: String? = null,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val foregroundServiceType: Int = 0,
    val isolatedProcess: Boolean = false
) : Serializable

data class ParsedProvider(
    val name: String,
    val authority: String,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val grantUriPermissions: Boolean = false,
    val readPermission: String? = null,
    val writePermission: String? = null,
    val multiprocess: Boolean = false
) : Serializable

data class ParsedReceiver(
    val name: String,
    val permission: String? = null,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val intentActions: List<String> = emptyList()
) : Serializable

data class ParsedPermission(
    val name: String,
    val protectionLevel: Int = 0
) : Serializable

data class ParsedFeature(
    val name: String,
    val required: Boolean = false
) : Serializable

data class ParsedNativeLibrary(
    val abi: String,
    val libraryName: String,
    val fileSizeBytes: Long
) : Serializable