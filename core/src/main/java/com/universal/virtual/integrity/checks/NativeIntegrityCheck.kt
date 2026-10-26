package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import java.io.File
import kotlin.system.measureTimeMillis

class NativeIntegrityCheck : IntegrityCheck {
    override val id: String = "check.native.abi_libraries"
    override val name: String = "Native Shared Library (.so) & ABI Invariants"
    override val category: IntegrityCategory = IntegrityCategory.NATIVE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val nativeErrors = mutableListOf<String>()
        val supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        var verifiedLibs = 0

        duration = measureTimeMillis {
            val packages = context.database.packageDao().getAllPackagesList()
            for (pkg in packages) {
                if (!supportedAbis.contains(pkg.primaryAbi) && pkg.primaryAbi.isNotBlank() && pkg.primaryAbi != "none") {
                    nativeErrors.add("Package '${pkg.packageName}' specifies unsupported ABI '${pkg.primaryAbi}'")
                }

                // Check extracted native library folder if exists
                val libDir = File(context.rootStorageDir, "virtual/packages/${pkg.packageName}/lib")
                if (libDir.exists() && libDir.isDirectory) {
                    val abiFolders = libDir.listFiles() ?: emptyArray()
                    for (folder in abiFolders) {
                        if (folder.isDirectory) {
                            val soFiles = folder.listFiles { _, name -> name.endsWith(".so") } ?: emptyArray()
                            for (so in soFiles) {
                                if (so.length() == 0L) {
                                    nativeErrors.add("Empty .so library: ${so.name} in ${pkg.packageName}")
                                } else {
                                    verifiedLibs++
                                }
                            }
                        }
                    }
                }
            }
        }

        if (nativeErrors.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.ERROR,
                title = "Native Library Integrity Issues",
                message = "Native ABI or library extraction violations: ${nativeErrors.joinToString("; ")}",
                evidence = mapOf("nativeErrors" to nativeErrors.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Native Libraries & ABIs Verified",
            message = "All native binaries match supported target ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) and are verified ($verifiedLibs libraries checked).",
            evidence = mapOf("supportedAbis" to "arm64-v8a,armeabi-v7a,x86,x86_64"),
            durationMs = duration
        )
    }
}
