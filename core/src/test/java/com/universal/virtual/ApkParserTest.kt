package com.universal.virtual

import com.universal.virtual.parser.ApkParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var parser: ApkParser

    @Before
    fun setUp() {
        parser = ApkParser()
    }

    @Test
    fun `parse extracts native libraries and determines primary ABI`() {
        val apkFile = tempFolder.newFile("sample_multiabi.apk")
        ZipOutputStream(FileOutputStream(apkFile)).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("MOCK_AXML_CONTENT".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("lib/armeabi-v7a/libfoo.so"))
            zip.write("ARM32_SO_BYTES".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libfoo.so"))
            zip.write("ARM64_SO_BYTES_123456789".toByteArray())
            zip.closeEntry()
        }

        val result = parser.parse(apkFile)
        assertTrue(result.isSuccess)

        val parsed = result.getOrNull()!!
        assertEquals(2, parsed.nativeLibraries.size)
        assertTrue(parsed.hasNativeLibraries)
        assertEquals("arm64-v8a", parsed.primaryAbi)
        assertTrue(parsed.packageName.contains("sample_multiabi"))
    }

    @Test
    fun `parse handles APK without native libraries gracefully`() {
        val apkFile = tempFolder.newFile("sample_purejava.apk")
        ZipOutputStream(FileOutputStream(apkFile)).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("MOCK_AXML_CONTENT".toByteArray())
            zip.closeEntry()
        }

        val result = parser.parse(apkFile)
        assertTrue(result.isSuccess)

        val parsed = result.getOrNull()!!
        assertEquals(0, parsed.nativeLibraries.size)
        assertFalse(parsed.hasNativeLibraries)
        assertEquals("none", parsed.primaryAbi)
    }

    @Test
    fun `parse fails gracefully on non-existent file`() {
        val missing = File(tempFolder.root, "missing.apk")
        val result = parser.parse(missing)

        assertTrue(result.isFailure)
    }
}