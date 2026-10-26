package com.universal.virtual

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.common.identity.*
import com.universal.virtual.security.SecurityPolicy
import com.universal.virtual.security.SecurityValidationResult
import com.universal.virtual.security.VirtualSecurityManager
import org.junit.Test
import java.io.File

class SecurityIsolationTest {

    private val securityManager = VirtualSecurityManager()

    @Test
    fun testCrossUserAccessRejected() {
        val user0 = VirtualUserId(0)
        val user1 = VirtualUserId(1)

        val result = securityManager.validateUserAccess(caller = user0, target = user1)
        assertThat(result).isEqualTo(SecurityValidationResult.DENIED_USER_ISOLATION)
        assertThat(result.isDenied).isTrue()
    }

    @Test
    fun testSameUserAccessAllowed() {
        val user1 = VirtualUserId(1)
        val result = securityManager.validateUserAccess(caller = user1, target = user1)
        assertThat(result).isEqualTo(SecurityValidationResult.ALLOWED)
        assertThat(result.isAllowed).isTrue()
    }

    @Test
    fun testCrossInstanceAccessRejected() {
        val idInst0 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0)
        val idInst1 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 1)

        val result = securityManager.validateInstanceAccess(caller = idInst0, target = idInst1)
        assertThat(result).isEqualTo(SecurityValidationResult.DENIED_INSTANCE_ISOLATION)
    }

    @Test
    fun testCrossPackageImpersonationRejected() {
        val appA = VirtualPackageId("com.example.app.a")
        val appB = VirtualPackageId("com.example.app.b")

        val result = securityManager.validatePackageAccess(caller = appA, target = appB)
        assertThat(result).isEqualTo(SecurityValidationResult.DENIED_PACKAGE_IMPERSONATION)
    }

    @Test
    fun testCrossProcessMismatchRejected() {
        val proc1 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0, processId = 10)
        val proc2 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0, processId = 20)

        val result = securityManager.validateProcessAccess(caller = proc1, target = proc2)
        assertThat(result).isEqualTo(SecurityValidationResult.DENIED_PROCESS_ISOLATION)
    }

    @Test
    fun testPathTraversalDetectedAndDenied() {
        val identity = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0)
        val maliciousPath = File("../../../system/etc/hosts")

        val result = securityManager.validatePathAccess(caller = identity, targetFile = maliciousPath)
        assertThat(result).isEqualTo(SecurityValidationResult.DENIED_PATH_TRAVERSAL)
    }

    @Test
    fun testCleanPathAccessAllowed() {
        val identity = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0)
        val safeFile = File("users/0/instances/0/files/data.json")

        val result = securityManager.validatePathAccess(caller = identity, targetFile = safeFile)
        assertThat(result).isEqualTo(SecurityValidationResult.ALLOWED)
    }
}
