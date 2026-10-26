package com.universal.virtual.parser

import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import java.io.File
import java.util.zip.ZipFile

class ApkValidator(
    private val maxApkSizeBytes: Long = 500 * 1024 * 1024L // 500 MB
) {

    fun validate(apkFile: File): VirtualResult<Unit> {
        if (!apkFile.exists()) {
            return VirtualResult.failure(
                code = VirtualErrorCode.NOT_FOUND,
                message = "APK file does not exist: ${apkFile.absolutePath}"
            )
        }

        if (!apkFile.isFile || !apkFile.canRead()) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INVALID_ARGUMENT,
                message = "APK path is not a readable regular file: ${apkFile.absolutePath}"
            )
        }

        val size = apkFile.length()
        if (size <= 0) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INVALID_ARGUMENT,
                message = "APK file is empty (0 bytes): ${apkFile.absolutePath}"
            )
        }

        if (size > maxApkSizeBytes) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INVALID_ARGUMENT,
                message = "APK size ($size bytes) exceeds configured limit ($maxApkSizeBytes bytes)."
            )
        }

        // Verify ZIP header and manifest presence
        try {
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                if (manifestEntry == null) {
                    return VirtualResult.failure(
                        code = VirtualErrorCode.INVALID_ARGUMENT,
                        message = "Malformed APK: AndroidManifest.xml entry missing."
                    )
                }
            }
        } catch (e: Exception) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INVALID_ARGUMENT,
                message = "Corrupt or unreadable APK archive: ${e.message}",
                cause = e
            )
        }

        return VirtualResult.success(Unit)
    }
}