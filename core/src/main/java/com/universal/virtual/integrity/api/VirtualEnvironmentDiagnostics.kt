package com.universal.virtual.integrity.api

import com.universal.virtual.integrity.models.IntegrityDiff
import com.universal.virtual.integrity.models.IntegrityProgress
import com.universal.virtual.integrity.models.IntegrityReport
import kotlinx.coroutines.flow.StateFlow

interface VirtualEnvironmentDiagnostics {
    suspend fun runQuickScan(): IntegrityReport
    suspend fun runStandardScan(): IntegrityReport
    suspend fun runFullScan(): IntegrityReport
    suspend fun runDeepScan(): IntegrityReport

    fun observeProgress(): StateFlow<IntegrityProgress>
    fun observeLastReport(): StateFlow<IntegrityReport?>
    fun observeHistory(): StateFlow<List<IntegrityReport>>
    suspend fun compareWithPrevious(): IntegrityDiff?
}
