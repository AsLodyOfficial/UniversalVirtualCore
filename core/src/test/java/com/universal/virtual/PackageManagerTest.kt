package com.universal.virtual

import com.universal.virtual.common.events.VirtualEventBus
import com.universal.virtual.common.events.VirtualEventBusImpl
import com.universal.virtual.common.events.VirtualEventType
import com.universal.virtual.common.identity.VirtualPackageId
import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.database.UniversalVirtualDatabase
import com.universal.virtual.database.dao.PackageDao
import com.universal.virtual.database.entities.VirtualPackageEntity
import com.universal.virtual.pkg.DefaultVirtualPackageManager
import com.universal.virtual.pkg.PackageStorageManager
import com.universal.virtual.repository.VirtualPackageRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storageManager: PackageStorageManager
    private lateinit var eventBus: VirtualEventBus
    private lateinit var mockDb: UniversalVirtualDatabase
    private lateinit var mockDao: PackageDao
    private lateinit var repository: VirtualPackageRepository
    private lateinit var packageManager: DefaultVirtualPackageManager
    private lateinit var sampleApk: File

    @Before
    fun setUp() {
        val rootDir = tempFolder.newFolder("pkg_mgr_test_root")
        storageManager = PackageStorageManager(rootDir)
        eventBus = VirtualEventBusImpl()
        mockDb = mock(UniversalVirtualDatabase::class.java)
        mockDao = mock(PackageDao::class.java)
        `when`(mockDb.packageDao()).thenReturn(mockDao)

        repository = VirtualPackageRepository(mockDb)
        packageManager = DefaultVirtualPackageManager(
            storageManager = storageManager,
            repository = repository,
            eventBus = eventBus
        )

        sampleApk = tempFolder.newFile("com.demo.app.apk")
        ZipOutputStream(FileOutputStream(sampleApk)).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("MOCK_XML".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libdemo.so"))
            zip.write("DEMO_SO".toByteArray())
            zip.closeEntry()
        }
    }

    @Test
    fun `installPackage successfully stages, commits, saves to DB and publishes event`() = runBlocking {
        var receivedEvent: VirtualEventType? = null
        eventBus.subscribe { event ->
            receivedEvent = event.type
        }

        val result = packageManager.installPackage(sampleApk.absolutePath)
        assertTrue(result.isSuccess)

        val metadata = result.getOrNull()!!
        assertTrue(metadata.packageId.value.contains("demo.app"))
        assertTrue(metadata.apkFile.exists())
        assertEquals("source.apk", metadata.apkFile.name)
        assertEquals(VirtualEventType.PACKAGE_INSTALLED, receivedEvent)
    }

    @Test
    fun `installPackage returns NOT_FOUND when apk file path does not exist`() {
        val nonExistent = File(tempFolder.root, "non_existent.apk").absolutePath
        val result = packageManager.installPackage(nonExistent)

        assertTrue(result.isFailure)
        assertEquals(VirtualErrorCode.NOT_FOUND, result.errorOrNull()?.code)
    }

    @Test
    fun `uninstallPackage removes storage and DB record`() = runBlocking {
        val pkgId = VirtualPackageId.of("com.demo.app")
        val existingEntity = VirtualPackageEntity(
            packageName = pkgId.value,
            label = "Demo App",
            versionName = "1.0.0",
            versionCode = 1L,
            minSdk = 26,
            targetSdk = 34,
            apkPath = File(storageManager.getPackageDirectory(pkgId), "source.apk").absolutePath
        )
        `when`(mockDao.getPackage(pkgId.value)).thenReturn(existingEntity)

        var uninstalledEventReceived = false
        eventBus.subscribe { event ->
            if (event.type == VirtualEventType.PACKAGE_UNINSTALLED) {
                uninstalledEventReceived = true
            }
        }

        val uninstallResult = packageManager.uninstallPackage(pkgId)
        assertTrue(uninstallResult.isSuccess)
        assertTrue(uninstalledEventReceived)
        verify(mockDao).deletePackage(pkgId.value)
    }

    @Test
    fun `uninstallPackage fails with NOT_FOUND when package is not registered`() = runBlocking {
        val pkgId = VirtualPackageId.of("com.uninstalled.app")
        `when`(mockDao.getPackage(pkgId.value)).thenReturn(null)

        val result = packageManager.uninstallPackage(pkgId)
        assertTrue(result.isFailure)
        assertEquals(VirtualErrorCode.PACKAGE_NOT_FOUND, result.errorOrNull()?.code)
    }
}