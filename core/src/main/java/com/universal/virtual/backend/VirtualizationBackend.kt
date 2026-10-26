package com.universal.virtual.backend

import com.universal.virtual.common.capability.VirtualCapabilities
import com.universal.virtual.common.result.VirtualResult
import com.universal.virtual.diagnostics.DiagnosticSnapshot

interface VirtualizationBackend {
    val name: String
    fun initialize(): VirtualResult<Unit>
    fun shutdown(): VirtualResult<Unit>
    fun capabilities(): VirtualCapabilities
    fun diagnostics(): DiagnosticSnapshot
}
