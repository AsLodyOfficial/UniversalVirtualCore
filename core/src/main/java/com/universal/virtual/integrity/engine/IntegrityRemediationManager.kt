package com.universal.virtual.integrity.engine

import com.universal.virtual.common.result.VirtualResult
import com.universal.virtual.database.UniversalVirtualDatabase
import com.universal.virtual.integrity.models.IntegrityCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class IntegrityRemediationManager(
    private val database: UniversalVirtualDatabase,
    private val rootStorageDir: File
) {

    suspend fun repairIssue(result: IntegrityCheckResult): VirtualResult<String> = withContext(Dispatchers.IO) {
        when (result.checkId) {
            "check.instance.storage_layout" -> {
                // Ensure instance folders exist
                val instances = database.instanceDao().getAllInstancesList()
                for (inst in instances) {
                    val dir = File(inst.storagePath)
                    dir.mkdirs()
                    File(dir, "files").mkdirs()
                    File(dir, "cache").mkdirs()
                    File(dir, "databases").mkdirs()
                    File(dir, "shared_prefs").mkdirs()
                }
                VirtualResult.success("Repaired missing instance storage directories.")
            }
            "check.transaction.incomplete_installs" -> {
                val packagesDir = File(rootStorageDir, "virtual/packages")
                if (packagesDir.exists()) {
                    packagesDir.listFiles()?.forEach { pkgDir ->
                        val staging = File(pkgDir, "staging")
                        if (staging.exists()) staging.deleteRecursively()
                    }
                }
                VirtualResult.success("Pruned stale staging directories.")
            }
            "check.recovery.state" -> {
                database.openHelper.writableDatabase.execSQL("DELETE FROM virtual_crash_records")
                VirtualResult.success("Pruned stale crash log records.")
            }
            "check.process.lifecycle" -> {
                database.openHelper.writableDatabase.execSQL("UPDATE virtual_processes SET state = 'STOPPED' WHERE state = 'RUNNING'")
                VirtualResult.success("Reset stale virtual process states to STOPPED.")
            }
            else -> {
                VirtualResult.success("No automated action required or repair completed safely.")
            }
        }
    }
}
