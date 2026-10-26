package com.universal.virtual.parser

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

class ApkParser(
    private val context: Context? = null,
    private val validator: ApkValidator = ApkValidator()
) {

    fun parse(apkFile: File): VirtualResult<ParsedApk> {
        val validation = validator.validate(apkFile)
        if (validation.isFailure) {
            return VirtualResult.failure(validation.errorOrNull()!!)
        }

        return try {
            val nativeLibs = extractNativeLibraries(apkFile)

            // Try Android PackageArchiveInfo first if Context is available
            val archiveInfo = getAndroidPackageInfo(apkFile)
            if (archiveInfo != null && archiveInfo.packageName != null) {
                mapFromPackageInfo(apkFile, archiveInfo, nativeLibs)
            } else {
                // Binary XML / AXML parser fallback
                parseFromBinaryXml(apkFile, nativeLibs)
            }
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to parse APK: ${e.message}",
                cause = e
            )
        }
    }

    private fun getAndroidPackageInfo(apkFile: File): PackageInfo? {
        if (context == null) return null
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_CONFIGURATIONS
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (_: Exception) {
            null
        }
    }

    private fun mapFromPackageInfo(
        apkFile: File,
        info: PackageInfo,
        nativeLibs: List<ParsedNativeLibrary>
    ): VirtualResult<ParsedApk> {
        val appInfo = info.applicationInfo
        val appLabel = appInfo?.name ?: info.packageName
        val parsedApp = ParsedApplication(
            label = appLabel,
            className = appInfo?.className,
            iconResId = appInfo?.icon ?: 0,
            flags = appInfo?.flags ?: 0
        )

        val activities = info.activities?.map {
            ParsedActivity(
                name = it.name,
                label = it.loadLabel(context?.packageManager ?: return@map ParsedActivity(it.name)).toString(),
                launchMode = it.launchMode,
                exported = it.exported,
                enabled = it.enabled,
                permission = it.permission
            )
        } ?: emptyList()

        val services = info.services?.map {
            ParsedService(
                name = it.name,
                permission = it.permission,
                exported = it.exported,
                enabled = it.enabled
            )
        } ?: emptyList()

        val providers = info.providers?.map {
            ParsedProvider(
                name = it.name,
                authority = it.authority ?: "",
                exported = it.exported,
                enabled = it.enabled,
                grantUriPermissions = it.grantUriPermissions,
                readPermission = it.readPermission,
                writePermission = it.writePermission
            )
        } ?: emptyList()

        val receivers = info.receivers?.map {
            ParsedReceiver(
                name = it.name,
                permission = it.permission,
                exported = it.exported,
                enabled = it.enabled
            )
        } ?: emptyList()

        val permissions = info.requestedPermissions?.map {
            ParsedPermission(name = it)
        } ?: emptyList()

        val parsedApk = ParsedApk(
            packageName = info.packageName,
            versionName = info.versionName ?: "1.0.0",
            versionCode = info.versionCode.toLong(),
            minSdk = appInfo?.minSdkVersion ?: 26,
            targetSdk = appInfo?.targetSdkVersion ?: 34,
            compileSdk = 34,
            originalApkFile = apkFile,
            originalApkSize = apkFile.length(),
            application = parsedApp,
            activities = activities,
            services = services,
            providers = providers,
            receivers = receivers,
            permissions = permissions,
            features = emptyList(),
            nativeLibraries = nativeLibs
        )

        return VirtualResult.success(parsedApk)
    }

    private fun extractNativeLibraries(apkFile: File): List<ParsedNativeLibrary> {
        val libs = mutableListOf<ParsedNativeLibrary>()
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                    val parts = entry.name.split("/")
                    if (parts.size >= 3) {
                        val abi = parts[1]
                        val libName = parts.last()
                        libs.add(
                            ParsedNativeLibrary(
                                abi = abi,
                                libraryName = libName,
                                fileSizeBytes = entry.size
                            )
                        )
                    }
                }
            }
        }
        return libs
    }

    /**
     * Fallback Binary Android XML parser to extract package & components directly from zip.
     */
    private fun parseFromBinaryXml(apkFile: File, nativeLibs: List<ParsedNativeLibrary>): VirtualResult<ParsedApk> {
        return try {
            var packageName: String? = null
            var versionName: String = "1.0.0"
            var versionCode: Long = 1
            var minSdk = 26
            var targetSdk = 34
            val activities = mutableListOf<ParsedActivity>()
            val services = mutableListOf<ParsedService>()
            val providers = mutableListOf<ParsedProvider>()
            val receivers = mutableListOf<ParsedReceiver>()
            val permissions = mutableListOf<ParsedPermission>()

            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                    ?: return VirtualResult.failure(VirtualErrorCode.NOT_FOUND, "AndroidManifest.xml not found")

                zip.getInputStream(manifestEntry).use { input ->
                    val axml = SimpleAxmlReader.parse(input)
                    packageName = axml.packageName
                    versionName = axml.versionName ?: "1.0.0"
                    versionCode = axml.versionCode
                    minSdk = axml.minSdk
                    targetSdk = axml.targetSdk
                    activities.addAll(axml.activities)
                    services.addAll(axml.services)
                    providers.addAll(axml.providers)
                    receivers.addAll(axml.receivers)
                    permissions.addAll(axml.permissions)
                }
            }

            if (packageName.isNullOrBlank()) {
                // Fallback default from filename if binary XML has custom obfuscation
                packageName = "com.virtual." + apkFile.nameWithoutExtension.lowercase().replace("[^a-z0-9_.]".toRegex(), "")
                if (!packageName!!.contains('.')) packageName = "$packageName.app"
            }

            val parsedApk = ParsedApk(
                packageName = packageName!!,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                compileSdk = 34,
                originalApkFile = apkFile,
                originalApkSize = apkFile.length(),
                application = ParsedApplication(label = packageName!!),
                activities = activities,
                services = services,
                providers = providers,
                receivers = receivers,
                permissions = permissions,
                nativeLibraries = nativeLibs
            )
            VirtualResult.success(parsedApk)
        } catch (e: Exception) {
            VirtualResult.failure(VirtualErrorCode.INTERNAL_ERROR, "AXML parsing failed: ${e.message}", cause = e)
        }
    }
}

/**
 * Lightweight binary Android XML reader extracting tags and string pool attributes.
 */
internal object SimpleAxmlReader {

    data class ManifestData(
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long,
        val minSdk: Int,
        val targetSdk: Int,
        val activities: List<ParsedActivity>,
        val services: List<ParsedService>,
        val providers: List<ParsedProvider>,
        val receivers: List<ParsedReceiver>,
        val permissions: List<ParsedPermission>
    )

    fun parse(input: InputStream): ManifestData {
        val bytes = input.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read string pool
        val stringPool = mutableListOf<String>()
        val activities = mutableListOf<ParsedActivity>()
        val services = mutableListOf<ParsedService>()
        val providers = mutableListOf<ParsedProvider>()
        val receivers = mutableListOf<ParsedReceiver>()
        val permissions = mutableListOf<ParsedPermission>()

        var packageName: String? = null
        var versionName: String? = "1.0.0"
        var versionCode: Long = 1
        var minSdk = 26
        var targetSdk = 34

        try {
            val magic = buffer.int
            if (magic == 0x00080003) { // AXML magic
                buffer.int // fileSize
                while (buffer.hasRemaining()) {
                    val chunkType = buffer.int
                    val chunkSize = buffer.int
                    val chunkStart = buffer.position() - 8

                    if (chunkType == 0x001C0001) { // String Pool
                        val stringCount = buffer.int
                        val styleCount = buffer.int
                        val flags = buffer.int
                        val stringsStart = buffer.int
                        val stylesStart = buffer.int

                        val isUtf8 = (flags and (1 shl 8)) != 0
                        val stringOffsets = IntArray(stringCount) { buffer.int }
                        val poolDataOffset = chunkStart + stringsStart

                        for (offset in stringOffsets) {
                            val strPos = poolDataOffset + offset
                            if (strPos < bytes.size) {
                                val s = if (isUtf8) readUtf8String(bytes, strPos) else readUtf16String(bytes, strPos)
                                stringPool.add(s)
                            }
                        }
                    } else if (chunkType == 0x00100102) { // Start Tag
                        buffer.int // lineNumber
                        buffer.int // comment
                        val nsUri = buffer.int
                        val nameIndex = buffer.int
                        val attrStart = buffer.short
                        val attrSize = buffer.short
                        val attrCount = buffer.short
                        buffer.short // idIndex
                        buffer.short // classIndex
                        buffer.short // styleIndex

                        val tagName = stringPool.getOrNull(nameIndex) ?: ""
                        val attrMap = mutableMapOf<String, String>()

                        for (i in 0 until attrCount) {
                            val aNs = buffer.int
                            val aName = buffer.int
                            val aRawValue = buffer.int
                            val aTypedValSize = buffer.short
                            val aTypedValRes = buffer.get()
                            val aTypedValType = buffer.get()
                            val aTypedValData = buffer.int

                            val attrKey = stringPool.getOrNull(aName) ?: ""
                            val attrVal = if (aRawValue != -1) {
                                stringPool.getOrNull(aRawValue) ?: aTypedValData.toString()
                            } else {
                                aTypedValData.toString()
                            }
                            if (attrKey.isNotBlank()) {
                                attrMap[attrKey] = attrVal
                            }
                        }

                        when (tagName) {
                            "manifest" -> {
                                packageName = attrMap["package"]
                                versionName = attrMap["versionName"]
                                versionCode = attrMap["versionCode"]?.toLongOrNull() ?: 1L
                            }
                            "uses-sdk" -> {
                                minSdk = attrMap["minSdkVersion"]?.toIntOrNull() ?: 26
                                targetSdk = attrMap["targetSdkVersion"]?.toIntOrNull() ?: 34
                            }
                            "activity" -> {
                                val name = attrMap["name"]
                                if (!name.isNullOrBlank()) {
                                    activities.add(ParsedActivity(name = name, label = attrMap["label"]))
                                }
                            }
                            "service" -> {
                                val name = attrMap["name"]
                                if (!name.isNullOrBlank()) {
                                    services.add(ParsedService(name = name))
                                }
                            }
                            "provider" -> {
                                val name = attrMap["name"]
                                val auth = attrMap["authorities"] ?: ""
                                if (!name.isNullOrBlank()) {
                                    providers.add(ParsedProvider(name = name, authority = auth))
                                }
                            }
                            "receiver" -> {
                                val name = attrMap["name"]
                                if (!name.isNullOrBlank()) {
                                    receivers.add(ParsedReceiver(name = name))
                                }
                            }
                            "uses-permission" -> {
                                val name = attrMap["name"]
                                if (!name.isNullOrBlank()) {
                                    permissions.add(ParsedPermission(name = name))
                                }
                            }
                        }
                    }

                    // Jump to end of chunk safely
                    val nextPos = chunkStart + chunkSize
                    if (nextPos in (buffer.position() + 1)..bytes.size) {
                        buffer.position(nextPos)
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through with whatever was extracted
        }

        return ManifestData(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            activities = activities,
            services = services,
            providers = providers,
            receivers = receivers,
            permissions = permissions
        )
    }

    private fun readUtf8String(bytes: ByteArray, offset: Int): String {
        var pos = offset
        if (pos >= bytes.size) return ""
        var len = bytes[pos].toInt() and 0xFF
        pos++
        if ((len and 0x80) != 0) {
            pos++ // skip 2nd byte of length
        }
        var byteLen = bytes[pos].toInt() and 0xFF
        pos++
        if ((byteLen and 0x80) != 0) {
            pos++
        }
        val sb = StringBuilder()
        for (i in 0 until byteLen) {
            if (pos + i < bytes.size) {
                val b = bytes[pos + i].toInt()
                if (b == 0) break
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun readUtf16String(bytes: ByteArray, offset: Int): String {
        var pos = offset
        if (pos + 1 >= bytes.size) return ""
        val len = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        val sb = StringBuilder()
        for (i in 0 until len) {
            val charPos = pos + i * 2
            if (charPos + 1 < bytes.size) {
                val ch = ((bytes[charPos].toInt() and 0xFF) or ((bytes[charPos + 1].toInt() and 0xFF) shl 8)).toChar()
                if (ch == '\u0000') break
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}