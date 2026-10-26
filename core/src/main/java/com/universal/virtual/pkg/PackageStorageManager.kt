package com.universal.virtual.pkg

import com.universal.virtual.common.identity.VirtualPackageId
import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile

class PackageStorageManager(
    private val rootDir: File
) {

    val packagesRoot: File = File(rootDir, "virtual/packages").apply {
        if (!exists()) mkdirs()
    }

    fun getPackageDirectory(packageId: VirtualPackageId): File {
        return File(packagesRoot, packageId.value)
    }

    fun getInstalledApkFile(packageId: VirtualPackageId): File {
        return File(getPackageDirectory(packageId), "source.apk")
    }

    fun getNativeLibDirectory(packageId: VirtualPackageId, abi: String): File {
        return File(File(getPackageDirectory(packageId), "lib"), abi)
    }

    fun validatePathWithinRoot(targetFile: File, allowedRoot: File = rootDir): Boolean {
        return try {
            val canonicalRoot = allowedRoot.canonicalPath
            val canonicalTarget = targetFile.canonicalPath
            canonicalTarget.startsWith(canonicalRoot + File.separator) || canonicalTarget == canonicalRoot
        } catch (_: Exception) {
            false
        }
    }

    fun stageApk(sourceApkFile: File, packageId: VirtualPackageId): VirtualResult<File> {
        val packageDir = getPackageDirectory(packageId)
        if (!validatePathWithinRoot(packageDir, packagesRoot)) {
            return VirtualResult.failure(
                code = VirtualErrorCode.SECURITY_VIOLATION,
                message = "Path traversal detected for package " + packageId.value
            )
        }

        val stagingDir = File(packageDir, "staging")
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to create staging directory: " + stagingDir.absolutePath
            )
        }

        val stagedFile = File(stagingDir, "stage_" + System.currentTimeMillis() + ".apk")
        return try {
            FileInputStream(sourceApkFile).use { input ->
                FileOutputStream(stagedFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (stagedFile.length() != sourceApkFile.length()) {
                stagedFile.delete()
                return VirtualResult.failure(
                    code = VirtualErrorCode.INTERNAL_ERROR,
                    message = "Staged APK file size mismatch (source: " + sourceApkFile.length() + ", staged: " + stagedFile.length() + ")"
                )
            }

            VirtualResult.success(stagedFile)
        } catch (e: Exception) {
            stagedFile.delete()
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to stage APK file: " + e.message,
                cause = e
            )
        }
    }

    fun commitStagedApk(stagedFile: File, packageId: VirtualPackageId): VirtualResult<File> {
        val packageDir = getPackageDirectory(packageId)
        val targetApk = File(packageDir, "source.apk")

        return try {
            if (targetApk.exists()) {
                targetApk.delete()
            }

            val renamed = stagedFile.renameTo(targetApk)
            if (!renamed) {
                // Fallback copy if cross-filesystem rename fails
                FileInputStream(stagedFile).use { input ->
                    FileOutputStream(targetApk).use { output ->
                        input.copyTo(output)
                    }
                }
                stagedFile.delete()
            }

            // Clean staging dir
            val stagingDir = File(packageDir, "staging")
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }

            VirtualResult.success(targetApk)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to commit staged APK: " + e.message,
                cause = e
            )
        }
    }

    fun rollback(packageId: VirtualPackageId): VirtualResult<Unit> {
        val packageDir = getPackageDirectory(packageId)
        return try {
            val stagingDir = File(packageDir, "staging")
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            // If package wasn't installed prior (no source.apk), delete entire directory
            val sourceApk = File(packageDir, "source.apk")
            if (!sourceApk.exists() && packageDir.exists()) {
                packageDir.deleteRecursively()
            }
            VirtualResult.success(Unit)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to rollback package storage for " + packageId.value + ": " + e.message,
                cause = e
            )
        }
    }

    fun extractNativeLibraries(packageId: VirtualPackageId, apkFile: File): VirtualResult<List<File>> {
        val packageDir = getPackageDirectory(packageId)
        val libBaseDir = File(packageDir, "lib")
        val extractedFiles = mutableListOf<File>()

        return try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                        val parts = entry.name.split("/")
                        if (parts.size >= 3) {
                            val abi = parts[1]
                            val libName = parts.last()
                            val abiDir = File(libBaseDir, abi)
                            if (!abiDir.exists()) abiDir.mkdirs()

                            val destFile = File(abiDir, libName)
                            if (validatePathWithinRoot(destFile, libBaseDir)) {
                                zip.getInputStream(entry).use { inStream ->
                                    FileOutputStream(destFile).use { outStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }
                                extractedFiles.add(destFile)
                            }
                        }
                    }
                }
            }
            VirtualResult.success(extractedFiles)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to extract native libraries: " + e.message,
                cause = e
            )
        }
    }

    fun deletePackageStorage(packageId: VirtualPackageId): VirtualResult<Unit> {
        val packageDir = getPackageDirectory(packageId)
        return try {
            if (packageDir.exists()) {
                packageDir.deleteRecursively()
            }
            VirtualResult.success(Unit)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to delete package storage for " + packageId.value + ": " + e.message,
                cause = e
            )
        }
    }
}