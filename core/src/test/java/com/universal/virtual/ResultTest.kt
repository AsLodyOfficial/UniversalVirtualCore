package com.universal.virtual

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.common.result.*
import org.junit.Test

class ResultTest {

    @Test
    fun testSuccessOperations() {
        val result = VirtualResult.success("test_data")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.isFailure).isFalse()
        assertThat(result.getOrNull()).isEqualTo("test_data")
        assertThat(result.getOrThrow()).isEqualTo("test_data")
        assertThat(result.errorOrNull()).isNull()

        val mapped = result.map { it.length }
        assertThat(mapped.getOrNull()).isEqualTo(9)

        val flatMapped = result.flatMap { VirtualResult.success(it.uppercase()) }
        assertThat(flatMapped.getOrNull()).isEqualTo("TEST_DATA")
    }

    @Test
    fun testFailureOperations() {
        val result: VirtualResult<String> = VirtualResult.failure(
            code = VirtualErrorCode.NOT_FOUND,
            message = "Entity not found"
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.isFailure).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()?.code).isEqualTo(VirtualErrorCode.NOT_FOUND)
        assertThat(result.errorOrNull()?.message).isEqualTo("Entity not found")
    }

    @Test(expected = VirtualException::class)
    fun testGetOrThrowOnFailureThrows() {
        val result: VirtualResult<String> = VirtualResult.failure(
            code = VirtualErrorCode.INTERNAL_ERROR,
            message = "Critical failure"
        )
        result.getOrThrow()
    }
}
