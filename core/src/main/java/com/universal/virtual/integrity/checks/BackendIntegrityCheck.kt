package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import com.universal.virtual.backend.FrameworkContainerBackend
import kotlin.system.measureTimeMillis

class BackendIntegrityCheck : IntegrityCheck {
    override val id: String = "check.backend.state"
    override val name: String = "Virtualization Backend Capabilities & Health"
    override val category: IntegrityCategory = IntegrityCategory.BACKEND

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val backend = FrameworkContainerBackend()

        duration = measureTimeMillis {
            backend.initialize()
        }

        val caps = backend.capabilities()
        val multiUser = caps.isSupported(com.universal.virtual.common.capability.VirtualCapability.MULTI_USER)
        val multiInstance = caps.isSupported(com.universal.virtual.common.capability.VirtualCapability.MULTI_INSTANCE)
        val native = caps.isSupported(com.universal.virtual.common.capability.VirtualCapability.NATIVE_RUNTIME)

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Backend Active: ${backend.name}",
            message = "Virtualization backend operational. Capabilities: multiUser=$multiUser, multiInstance=$multiInstance, native=$native.",
            evidence = mapOf(
                "backend" to backend.name,
                "multiUser" to "$multiUser",
                "multiInstance" to "$multiInstance",
                "native" to "$native"
            ),
            durationMs = duration
        )
    }
}
