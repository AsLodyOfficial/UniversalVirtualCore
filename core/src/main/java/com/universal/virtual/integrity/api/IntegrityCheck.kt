package com.universal.virtual.integrity.api

import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult

interface IntegrityCheck {
    val id: String
    val name: String
    val category: IntegrityCategory

    suspend fun execute(
        context: IntegrityContext
    ): IntegrityCheckResult
}
