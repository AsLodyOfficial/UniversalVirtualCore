package com.universal.virtual.integrity.engine

import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.api.VirtualEnvironmentDiagnostics
import com.universal.virtual.integrity.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VirtualEnvironmentDiagnosticsImpl(
    private val scanEngine: IntegrityScanEngine,
    private val contextProvider: () -> IntegrityContext,
    private val diffEngine: IntegrityDiffEngine = IntegrityDiffEngine()
) : VirtualEnvironmentDiagnostics {

    private val _lastReport = MutableStateFlow<IntegrityReport?>(null)
    private val _history = MutableStateFlow<List<IntegrityReport>>(emptyList())

    override suspend fun runQuickScan(): IntegrityReport {
        val ctx = contextProvider().copy(mode = IntegrityScanMode.QUICK)
        val report = scanEngine.runScan(ctx)
        recordReport(report)
        return report
    }

    override suspend fun runStandardScan(): IntegrityReport {
        val ctx = contextProvider().copy(mode = IntegrityScanMode.STANDARD)
        val report = scanEngine.runScan(ctx)
        recordReport(report)
        return report
    }

    override suspend fun runFullScan(): IntegrityReport {
        val ctx = contextProvider().copy(mode = IntegrityScanMode.FULL)
        val report = scanEngine.runScan(ctx)
        recordReport(report)
        return report
    }

    override suspend fun runDeepScan(): IntegrityReport {
        val ctx = contextProvider().copy(mode = IntegrityScanMode.DEEP)
        val report = scanEngine.runScan(ctx)
        recordReport(report)
        return report
    }

    override fun observeProgress(): StateFlow<IntegrityProgress> = scanEngine.progress

    override fun observeLastReport(): StateFlow<IntegrityReport?> = _lastReport.asStateFlow()

    override fun observeHistory(): StateFlow<List<IntegrityReport>> = _history.asStateFlow()

    override suspend fun compareWithPrevious(): IntegrityDiff? {
        val history = _history.value
        if (history.size < 2) return null
        val current = history[0]
        val previous = history[1]
        return diffEngine.computeDiff(previous, current)
    }

    private fun recordReport(report: IntegrityReport) {
        _lastReport.value = report
        val list = _history.value.toMutableList()
        list.add(0, report)
        if (list.size > 20) list.removeAt(list.size - 1)
        _history.value = list
    }
}
