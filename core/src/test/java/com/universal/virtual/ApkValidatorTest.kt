package com.universal.virtual

import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.parser.ApkValidator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var validator: ApkValidator

    @Before
    fun setUp() {
        validator = ApkValidator(maxApkSizeBytes = 10 * 1024 * 1024L) // 10MB for test
    }

    @Test
    fun `validate rejects non-existent file with NOT_FOUND`() {
        val nonExistent = File(tempFolder.root, "non_existent.apk")
        val result = validator.validate(nonExistent)

        assertTrue(result.isFailure)
        assertEquals(VirtualErrorCode.NOT_FOUND, result.errorOrNull()?.code)
    }

    @Test
    fun `validate rejects empty 0-byte file`() {
        val emptyFile = tempFolder.newFile("empty.apk")
        val result = validator.validate(emptyFile)

        assertTrue(result.isFailure)
        assertEquals(VirtualErrorCode.INVALID_ARGUMENT, result.errorOrNull()?.code)
        assertTrue(result.errorOrNull()?.message?.contains("empty") == true)
    }

    @Test
    fun `validate rejects non-zip corrupt file`() {
        val corruptFile = tempFolder.newFile("corrupt.apk")
        corruptFile.writeText("THIS IS NOT A VALID ZIP HEADER OR ARCHIVE")
        val result = validator.validate(corruptFile)

        assertTrue(result.isFailure)
        assertEquals(VirtualErrorCode.INVALID_ARGUMENT, result.errorOrNull()?.code)
    }

    @Test
    fun `validate rejects zip missing AndroidManifest`() {
        val zipWithoutManifest = tempFolder.newFile("no_manifest.apk")
        ZipOutputStream(FileOutputStream(zipWithoutManifest)).use { zip ->
            zip.putNextEntry(ZipEntry("res/values/strings.xml"))
            zip.write("<resources/>".toByteArray())
            zip.closeEntry()
        }

        val result = validator.validate(zipWithoutManifest)
        assertTrue(result.isFailure)
        assertEquals(VirtualErrorCode.INVALID_ARGUMENT, result.errorOrNull()?.code)
        assertTrue(result.errorOrNull()?.message?.contains("AndroidManifest.xml") == true)
    }

    @Test
    fun `validate accepts valid zip containing AndroidManifest`() {
        val validApk = tempFolder.newFile("valid.apk")
        ZipOutputStream(FileOutputStream(validApk)).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("BINARY_OR_PLAINTEXT_XML".toByteArray())
            zip.closeEntry()
        }

        val result = validator.validate(validApk)
        assertTrue(result.isSuccess)
    }
}