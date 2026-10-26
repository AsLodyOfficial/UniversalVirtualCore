package com.universal.virtual

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.common.identity.*
import org.junit.Test

class IdentityTest {

    @Test
    fun testSameIdentityEquals() {
        val id1 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 1, processId = 10)
        val id2 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 1, processId = 10)

        assertThat(id1).isEqualTo(id2)
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode())
        assertThat(id1.compositeKey).isEqualTo("0:com.example.app:1:10")
    }

    @Test
    fun testDifferentUsersNotEqual() {
        val idUser0 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0)
        val idUser1 = VirtualIdentity.of(userId = 1, packageName = "com.example.app", instanceId = 0)

        assertThat(idUser0).isNotEqualTo(idUser1)
    }

    @Test
    fun testDifferentPackagesNotEqual() {
        val idAppA = VirtualIdentity.of(userId = 0, packageName = "com.example.app.a", instanceId = 0)
        val idAppB = VirtualIdentity.of(userId = 0, packageName = "com.example.app.b", instanceId = 0)

        assertThat(idAppA).isNotEqualTo(idAppB)
    }

    @Test
    fun testDifferentInstancesNotEqual() {
        val idInst0 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0)
        val idInst1 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 1)

        assertThat(idInst0).isNotEqualTo(idInst1)
    }

    @Test
    fun testDifferentProcessesNotEqual() {
        val idProc1 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0, processId = 100)
        val idProc2 = VirtualIdentity.of(userId = 0, packageName = "com.example.app", instanceId = 0, processId = 200)

        assertThat(idProc1).isNotEqualTo(idProc2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeUserIdThrows() {
        VirtualUserId(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlankPackageThrows() {
        VirtualPackageId("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidPackageWithoutDotThrows() {
        VirtualPackageId("invalidpackagename")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeInstanceIdThrows() {
        VirtualInstanceId(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeProcessIdThrows() {
        VirtualProcessId(-1)
    }
}
