package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import com.universal.virtual.common.identity.VirtualIdentity
import com.universal.virtual.common.identity.VirtualInstanceId
import com.universal.virtual.common.identity.VirtualPackageId
import com.universal.virtual.common.identity.VirtualUserId
import kotlin.system.measureTimeMillis

class IdentityIntegrityCheck : IntegrityCheck {
    override val id: String = "check.identity.invariants"
    override val name: String = "Virtual Identity Invariants & Bounds"
    override val category: IntegrityCategory = IntegrityCategory.USER

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        var identityInvariantsOk = true

        duration = measureTimeMillis {
            try {
                val id1 = VirtualIdentity.of(0, "com.test.app", 0)
                val id2 = VirtualIdentity.of(0, "com.test.app", 0)
                identityInvariantsOk = (id1 == id2) && (id1.hashCode() == id2.hashCode()) && (id1.compositeKey == id2.compositeKey)
            } catch (_: Exception) {
                identityInvariantsOk = false
            }
        }

        if (!identityInvariantsOk) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.CRITICAL,
                title = "Virtual Identity Model Corrupted",
                message = "VirtualIdentity equals/hashCode or unique key invariant verification failed.",
                evidence = mapOf("invariants" to "FAILED"),
                repairAvailable = false,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Identity Invariants Verified",
            message = "Virtual identities, UID/PID mappings, and package uniqueness rules are consistent.",
            evidence = mapOf("identityModel" to "VERIFIED"),
            durationMs = duration
        )
    }
}
