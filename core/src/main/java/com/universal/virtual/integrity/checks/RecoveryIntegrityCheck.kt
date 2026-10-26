package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import kotlin.system.measureTimeMillis

class RecoveryIntegrityCheck : IntegrityCheck {
    override val id: String = "check.recovery.state"
    override val name: String = "Recovery State & Crash History Audit"
    override val category: IntegrityCategory = IntegrityCategory.RECOVERY

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        var crashRecordsCount = 0

        duration = measureTimeMillis {
            val cursor = context.database.openHelper.readableDatabase.query(
                androidx.sqlite.db.SimpleSQLiteQuery("SELECT COUNT(*) FROM virtual_crash_records")
            )
            cursor.use {
                if (it.moveToNext()) crashRecordsCount = it.getInt(0)
            }
        }

        if (crashRecordsCount > 10) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.WARNING,
                title = "Elevated Crash History",
                message = "The virtual environment has recorded $crashRecordsCount crash events.",
                evidence = mapOf("crashRecords" to "$crashRecordsCount"),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Recovery State Clean",
            message = "Virtual environment recovery state is normal ($crashRecordsCount crash records).",
            evidence = mapOf("crashCount" to "$crashRecordsCount"),
            durationMs = duration
        )
    }
}
