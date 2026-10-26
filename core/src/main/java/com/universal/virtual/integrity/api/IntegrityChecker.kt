package com.universal.virtual.integrity.api

import com.universal.virtual.integrity.models.IntegrityReport
import com.universal.virtual.integrity.models.IntegrityScope

interface IntegrityChecker {
    suspend fun check(
        scope: IntegrityScope = IntegrityScope.EntireEnvironment
    ): IntegrityReport
}
