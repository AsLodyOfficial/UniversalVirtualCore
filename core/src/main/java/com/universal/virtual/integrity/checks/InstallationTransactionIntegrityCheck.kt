package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import java.io.File
import kotlin.system.measureTimeMillis

class InstallationTransactionIntegrityCheck : IntegrityCheck {
    override val id: String = "check.transaction.incomplete_installs"
    override val name: String = "Installation & Staging Transaction Integrity"
    override val category: IntegrityCategory = IntegrityCategory.RECOVERY

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val staleStagingFolders = mutableListOf<String>()

        duration = measureTimeMillis {
            val packagesDir = File(context.rootStorageDir, "virtual/packages")
            if (packagesDir.exists()) {
                packagesDir.listFiles()?.forEach { pkgDir ->
                    val staging = File(pkgDir, "staging")
                    if (staging.exists() && staging.isDirectory && (staging.listFiles()?.isNotEmpty() == true)) {
                        staleStagingFolders.add("${pkgDir.name}/staging")
                    }
                }
            }
        }

        if (staleStagingFolders.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.WARNING,
                title = "Stale Installation Staging Data Detected",
                message = "Temporary installation staging directories left uncommitted: ${staleStagingFolders.joinToString(", ")}",
                evidence = mapOf("staleFolders" to staleStagingFolders.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Installation Staging Clean",
            message = "No incomplete installation transactions or uncommitted staging directories detected.",
            evidence = mapOf("stagingClean" to "TRUE"),
            durationMs = duration
        )
    }
}
