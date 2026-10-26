package com.universal.virtual.integrity

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.database.UniversalVirtualDatabase
import com.universal.virtual.database.dao.InstanceDao
import com.universal.virtual.database.dao.PackageDao
import com.universal.virtual.database.dao.UserDao
import com.universal.virtual.database.entities.VirtualInstanceEntity
import com.universal.virtual.database.entities.VirtualPackageEntity
import com.universal.virtual.database.entities.VirtualUserEntity
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.checks.*
import com.universal.virtual.integrity.engine.IntegrityCheckRegistryImpl
import com.universal.virtual.integrity.engine.IntegrityDiffEngine
import com.universal.virtual.integrity.engine.IntegrityRemediationManager
import com.universal.virtual.integrity.engine.IntegrityScanEngine
import com.universal.virtual.integrity.export.IntegrityReportExporter
import com.universal.virtual.integrity.hashing.IntegrityHashStore
import com.universal.virtual.integrity.models.IntegrityScanMode
import com.universal.virtual.integrity.models.IntegrityScope
import com.universal.virtual.integrity.models.IntegritySeverity
import com.universal.virtual.pkg.PackageStorageManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.io.File

class IntegrityCheckerTestSuite {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockDb: UniversalVirtualDatabase
    private lateinit var mockPackageDao: PackageDao
    private lateinit var mockUserDao: UserDao
    private lateinit var mockInstanceDao: InstanceDao
    private lateinit var tempRoot: File
    private lateinit var storageManager: PackageStorageManager
    private lateinit var integrityContext: IntegrityContext

    @Before
    fun setup() {
        tempRoot = tempFolder.newFolder("integrity_test_root")
        storageManager = PackageStorageManager(tempRoot)

        mockDb = mock(UniversalVirtualDatabase::class.java)
        mockPackageDao = mock(PackageDao::class.java)
        mockUserDao = mock(UserDao::class.java)
        mockInstanceDao = mock(InstanceDao::class.java)

        val openHelper = mock(androidx.sqlite.db.SupportSQLiteOpenHelper::class.java)
        val readableDb = mock(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        val writableDb = mock(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        val mockCursor = mock(android.database.Cursor::class.java)

        `when`(mockDb.packageDao()).thenReturn(mockPackageDao)
        `when`(mockDb.userDao()).thenReturn(mockUserDao)
        `when`(mockDb.instanceDao()).thenReturn(mockInstanceDao)
        `when`(mockDb.openHelper).thenReturn(openHelper)
        `when`(mockDb.isOpen).thenReturn(true)
        `when`(openHelper.readableDatabase).thenReturn(readableDb)
        `when`(openHelper.writableDatabase).thenReturn(writableDb)
        `when`(readableDb.isOpen).thenReturn(true)
        `when`(readableDb.version).thenReturn(2)

        integrityContext = IntegrityContext(
            scope = IntegrityScope.EntireEnvironment,
            mode = IntegrityScanMode.FULL,
            database = mockDb,
            storageManager = storageManager,
            rootStorageDir = tempRoot
        )
    }

    @Test
    fun testEnvironmentIntegrityCheck_healthy() = runBlocking {
        val check = EnvironmentIntegrityCheck()
        val result = check.execute(integrityContext)
        assertThat(result.severity).isEqualTo(IntegritySeverity.PASS)
        assertThat(result.title).contains("Healthy")
    }

    @Test
    fun testDatabaseIntegrityCheck_handlesQueries() = runBlocking {
        `when`(mockPackageDao.getAllPackagesList()).thenReturn(emptyList())
        `when`(mockUserDao.getAllUsersList()).thenReturn(emptyList())
        `when`(mockInstanceDao.getAllInstancesList()).thenReturn(emptyList())

        val check = DatabaseIntegrityCheck()
        val result = check.execute(integrityContext)
        assertThat(result).isNotNull()
    }

    @Test
    fun testStorageIsolationCheck_enforced() = runBlocking {
        val check = StorageIsolationCheck()
        val result = check.execute(integrityContext)
        assertThat(result.severity).isEqualTo(IntegritySeverity.PASS)
        assertThat(result.evidence["crossUser"]).isEqualTo("PROTECTED")
    }

    @Test
    fun testNativeIntegrityCheck_supportedAbis() = runBlocking {
        `when`(mockPackageDao.getAllPackagesList()).thenReturn(
            listOf(
                VirtualPackageEntity(
                    packageName = "com.test.arm",
                    label = "ARM App",
                    versionName = "1.0",
                    versionCode = 1,
                    minSdk = 26,
                    targetSdk = 34,
                    primaryAbi = "arm64-v8a",
                    apkPath = "/path/to/apk"
                )
            )
        )
        val check = NativeIntegrityCheck()
        val result = check.execute(integrityContext)
        assertThat(result.severity).isEqualTo(IntegritySeverity.PASS)
    }

    @Test
    fun testUserAndInstanceIntegrityChecks() = runBlocking {
        `when`(mockUserDao.getAllUsersList()).thenReturn(
            listOf(
                VirtualUserEntity(
                    userId = 0,
                    displayName = "Owner",
                    storageQuotaMb = 1000L
                )
            )
        )
        `when`(mockPackageDao.getAllPackagesList()).thenReturn(
            listOf(
                VirtualPackageEntity(
                    packageName = "com.test.app",
                    label = "Test App",
                    versionName = "1.0",
                    versionCode = 1,
                    minSdk = 26,
                    targetSdk = 34,
                    primaryAbi = "arm64-v8a",
                    apkPath = "/path/to/apk"
                )
            )
        )
        `when`(mockInstanceDao.getAllInstancesList()).thenReturn(
            listOf(
                VirtualInstanceEntity(
                    userId = 0,
                    packageName = "com.test.app",
                    instanceId = 0,
                    displayName = "Main",
                    storagePath = "${tempRoot.absolutePath}/instance0"
                )
            )
        )

        val userCheck = UserIntegrityCheck()
        val userResult = userCheck.execute(integrityContext)
        assertThat(userResult.severity).isEqualTo(IntegritySeverity.PASS)

        val instCheck = InstanceIntegrityCheck()
        val instResult = instCheck.execute(integrityContext)
        assertThat(instResult.severity).isEqualTo(IntegritySeverity.PASS)
    }

    @Test
    fun testIntegrityScanEngine_executesAllChecks() = runBlocking {
        `when`(mockPackageDao.getAllPackagesList()).thenReturn(emptyList())
        `when`(mockUserDao.getAllUsersList()).thenReturn(emptyList())
        `when`(mockInstanceDao.getAllInstancesList()).thenReturn(emptyList())
        `when`(mockPackageDao.getAllPackagesWithDetails()).thenReturn(emptyList())

        val engine = IntegrityScanEngine(
            registry = IntegrityCheckRegistryImpl(),
            contextProvider = { integrityContext }
        )
        val report = engine.runScan(integrityContext)
        assertThat(report.checksExecuted).isGreaterThan(15)
        assertThat(report.results).isNotEmpty()
    }

    @Test
    fun testIntegrityHashStore_calculateAndVerify() {
        val hashStore = IntegrityHashStore()
        val testFile = File(tempRoot, "sample.txt").apply { writeText("UniversalVirtualCore Secure Hash Test") }
        val hash = hashStore.calculateSha256(testFile)
        assertThat(hash).isNotEmpty()

        hashStore.recordTrustedHash("sample", hash)
        val verified = hashStore.verifyHash("sample", testFile)
        assertThat(verified).isTrue()
    }

    @Test
    fun testIntegrityDiffEngine_computesDiff() = runBlocking {
        `when`(mockPackageDao.getAllPackagesList()).thenReturn(emptyList())
        `when`(mockUserDao.getAllUsersList()).thenReturn(emptyList())
        `when`(mockInstanceDao.getAllInstancesList()).thenReturn(emptyList())
        `when`(mockPackageDao.getAllPackagesWithDetails()).thenReturn(emptyList())

        val engine = IntegrityScanEngine(
            registry = IntegrityCheckRegistryImpl(),
            contextProvider = { integrityContext }
        )
        val report1 = engine.runScan(integrityContext)
        val report2 = engine.runScan(integrityContext)

        val diffEngine = IntegrityDiffEngine()
        val diff = diffEngine.computeDiff(report1, report2)
        assertThat(diff.currentReportId).isEqualTo(report2.reportId)
    }

    @Test
    fun testIntegrityReportExporter_formats() = runBlocking {
        `when`(mockPackageDao.getAllPackagesList()).thenReturn(emptyList())
        `when`(mockUserDao.getAllUsersList()).thenReturn(emptyList())
        `when`(mockInstanceDao.getAllInstancesList()).thenReturn(emptyList())
        `when`(mockPackageDao.getAllPackagesWithDetails()).thenReturn(emptyList())

        val engine = IntegrityScanEngine(
            registry = IntegrityCheckRegistryImpl(),
            contextProvider = { integrityContext }
        )
        val report = engine.runScan(integrityContext)
        val exporter = IntegrityReportExporter()

        val json = exporter.exportToJson(report)
        assertThat(json).contains("\"reportId\"")
        assertThat(json).contains("\"results\"")

        val txt = exporter.exportToText(report)
        assertThat(txt).contains("UNIVERSAL VIRTUAL CORE INTEGRITY REPORT")

        val csv = exporter.exportToCsv(report)
        assertThat(csv).contains("Check ID,Category,Severity")
    }

    @Test
    fun testIntegrityRemediationManager_safeRepairs() = runBlocking {
        val remediation = IntegrityRemediationManager(mockDb, tempRoot)
        val check = EnvironmentIntegrityCheck()
        val res = check.execute(integrityContext)
        val repairRes = remediation.repairIssue(res)
        assertThat(repairRes.isSuccess).isTrue()
    }
}
