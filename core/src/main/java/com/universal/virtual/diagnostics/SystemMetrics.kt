package com.universal.virtual.diagnostics

import java.io.Serializable

data class SystemMetrics(
    val availableMemoryBytes: Long,
    val usedMemoryBytes: Long,
    val maxMemoryBytes: Long,
    val runtimeUptimeMs: Long
) : Serializable {

    val usedMemoryMb: Long get() = usedMemoryBytes / (1024 * 1024)
    val maxMemoryMb: Long get() = maxMemoryBytes / (1024 * 1024)

    companion object {
        fun capture(startTimeMs: Long): SystemMetrics {
            val runtime = Runtime.getRuntime()
            val total = runtime.totalMemory()
            val free = runtime.freeMemory()
            val max = runtime.maxMemory()
            val used = total - free
            val uptime = if (startTimeMs > 0) System.currentTimeMillis() - startTimeMs else 0L

            return SystemMetrics(
                availableMemoryBytes = free,
                usedMemoryBytes = used,
                maxMemoryBytes = max,
                runtimeUptimeMs = uptime
            )
        }
    }
}
