package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import com.universal.virtual.security.VirtualSecurityManager
import com.universal.virtual.common.identity.VirtualIdentity
import com.universal.virtual.common.identity.VirtualInstanceId
import com.universal.virtual.common.identity.VirtualPackageId
import com.universal.virtual.common.identity.VirtualUserId
import java.io.File
import kotlin.system.measureTimeMillis

class StorageIsolationCheck : IntegrityCheck {
    override val id: String = "check.security.storage_isolation"
    override val name: String = "Multi-User & Multi-Instance Storage Isolation"
    override val category: IntegrityCategory = IntegrityCategory.ISOLATION

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        var crossUserIsolated = false
        var crossInstanceIsolated = false
        var pathTraversalPrevented = false

        duration = measureTimeMillis {
            val secManager = VirtualSecurityManager()

            // 1. Cross-user isolation test
            val user0 = VirtualUserId(0)
            val user10 = VirtualUserId(10)
            val userAccess = secManager.validateUserAccess(user0, user10)
            crossUserIsolated = !userAccess.isAllowed

            // 2. Cross-instance isolation test
            val inst1 = VirtualIdentity.of(0, "com.app.a", 1)
            val inst2 = VirtualIdentity.of(0, "com.app.a", 2)
            val instAccess = secManager.validateInstanceAccess(inst1, inst2)
            crossInstanceIsolated = !instAccess.isAllowed

            // 3. Path traversal rejection test
            val maliciousFile = File(context.rootStorageDir, "../../etc/passwd")
            val pathAccess = secManager.validatePathAccess(inst1, maliciousFile)
            pathTraversalPrevented = !pathAccess.isAllowed
        }

        if (!crossUserIsolated || !crossInstanceIsolated || !pathTraversalPrevented) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.CRITICAL,
                title = "Virtual Storage Isolation Violation",
                message = "Virtual boundary enforcement failed: cross-user=$crossUserIsolated, cross-instance=$crossInstanceIsolated, traversalBlocked=$pathTraversalPrevented",
                evidence = mapOf(
                    "crossUserIsolated" to "$crossUserIsolated",
                    "crossInstanceIsolated" to "$crossInstanceIsolated",
                    "pathTraversalPrevented" to "$pathTraversalPrevented"
                ),
                repairAvailable = false,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Storage Isolation Boundaries Enforced",
            message = "Cross-user isolation, cross-instance sandboxing, and path traversal guards verified.",
            evidence = mapOf(
                "crossUser" to "PROTECTED",
                "crossInstance" to "PROTECTED",
                "pathTraversal" to "BLOCKED"
            ),
            durationMs = duration
        )
    }
}
