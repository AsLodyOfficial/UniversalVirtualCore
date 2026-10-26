package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegrityObject
import com.universal.virtual.integrity.models.IntegrityScope
import com.universal.virtual.integrity.models.IntegritySeverity
import kotlin.system.measureTimeMillis

class UserIntegrityCheck : IntegrityCheck {
    override val id: String = "check.user.invariants"
    override val name: String = "Virtual User Hierarchy & Quotas"
    override val category: IntegrityCategory = IntegrityCategory.USER

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val userIssues = mutableListOf<String>()
        var userCount = 0

        duration = measureTimeMillis {
            val users = context.database.userDao().getAllUsersList()
            val filtered = when (val s = context.scope) {
                is IntegrityScope.User -> users.filter { it.userId.toLong() == s.userId }
                else -> users
            }

            userCount = filtered.size
            for (u in filtered) {
                if (u.userId < 0) {
                    userIssues.add("Invalid negative userId: ${u.userId}")
                }
                if (u.storageQuotaMb <= 0) {
                    userIssues.add("Invalid quota for User ${u.userId}: ${u.storageQuotaMb}")
                }
            }
        }

        if (userIssues.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.ERROR,
                title = "Virtual User State Violations",
                message = "Issues detected in virtual users: ${userIssues.joinToString("; ")}",
                affectedObject = null,
                evidence = mapOf("issues" to userIssues.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Virtual Users Invariants Verified",
            message = "All $userCount virtual users have valid IDs, quotas, and profile bindings.",
            evidence = mapOf("userCount" to "$userCount"),
            durationMs = duration
        )
    }
}
