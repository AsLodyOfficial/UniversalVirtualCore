package com.universal.virtual

import com.universal.virtual.common.identity.VirtualPackageId
import com.universal.virtual.pkg.PackageStorageManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storageManager: PackageStorageManager
    private lateinit var testApk: File

    @Before
    fun setUp() {
        val rootDir = tempFolder.newFolder("virtual_storage_root")
        storageManager = PackageStorageManager(rootDir)

        testApk = tempFolder.newFile("test_sample.apk")
        ZipOutputStream(FileOutputStream(testApk)).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("MOCK_MANIFEST_DATA".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libtest.so"))
            zip.write("ELF_ARM64_BINARY".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("lib/x86_64/libtest.so"))
            zip.write("ELF_X86_BINARY".toByteArray())
            zip.closeEntry()
        }
    }

    @Test
    fun testValidatePathWithinRoot() {
        val packageDir = storageManager.packagesRoot
        val safeFile = File(packageDir, "com.example.app/source.apk")
        val maliciousFile = File(packageDir, "../../system/app/Malicious.apk")

        assertTrue(storageManager.validatePathWithinRoot(safeFile, packageDir))
        assertFalse(storageManager.validatePathWithinRoot(maliciousFile, packageDir))
    }

    @Test
    fun testStageApk() {
        val packageId = VirtualPackageId.of("com.example.stageapp")
        val stageResult = storageManager.stageApk(testApk, packageId)

        assertTrue(stageResult.isSuccess)
        val stagedFile = stageResult.getOrNull()!!
        assertTrue(stagedFile.exists())
        assertEquals(testApk.length(), stagedFile.length())
        assertTrue(stagedFile.absolutePath.contains("staging"))
    }

    @Test
    fun testCommitStagedApk() {
        val packageId = VirtualPackageId.of("com.example.commitapp")
        val stageResult = storageManager.stageApk(testApk, packageId)
        assertTrue(stageResult.isSuccess)

        val commitResult = storageManager.commitStagedApk(stageResult.getOrNull()!!, packageId)
        assertTrue(commitResult.isSuccess)

        val committedFile = commitResult.getOrNull()!!
        assertTrue(committedFile.exists())
        assertEquals("source.apk", committedFile.name)
        assertEquals(testApk.length(), committedFile.length())

        val stagingDir = File(storageManager.getPackageDirectory(packageId), "staging")
        assertFalse(stagingDir.exists())
    }

    @Test
    fun testRollback() {
        val packageId = VirtualPackageId.of("com.example.rollbackapp")
        val stageResult = storageManager.stageApk(testApk, packageId)
        assertTrue(stageResult.isSuccess)

        val rollbackResult = storageManager.rollback(packageId)
        assertTrue(rollbackResult.isSuccess)

        val packageDir = storageManager.getPackageDirectory(packageId)
        assertFalse(packageDir.exists())
    }

    @Test
    fun testExtractNativeLibraries() {
        val packageId = VirtualPackageId.of("com.example.nativeapp")
        val extractResult = storageManager.extractNativeLibraries(packageId, testApk)

        assertTrue(extractResult.isSuccess)
        val extractedFiles = extractResult.getOrNull()!!
        assertEquals(2, extractedFiles.size)

        val arm64Lib = File(storageManager.getNativeLibDirectory(packageId, "arm64-v8a"), "libtest.so")
        val x86Lib = File(storageManager.getNativeLibDirectory(packageId, "x86_64"), "libtest.so")

        assertTrue(arm64Lib.exists())
        assertTrue(x86Lib.exists())
    }

    @Test
    fun testDeletePackageStorage() {
        val packageId = VirtualPackageId.of("com.example.delapp")
        val stageResult = storageManager.stageApk(testApk, packageId)
        storageManager.commitStagedApk(stageResult.getOrNull()!!, packageId)
        storageManager.extractNativeLibraries(packageId, testApk)

        val packageDir = storageManager.getPackageDirectory(packageId)
        assertTrue(packageDir.exists())

        val deleteResult = storageManager.deletePackageStorage(packageId)
        assertTrue(deleteResult.isSuccess)
        assertFalse(packageDir.exists())
    }
}