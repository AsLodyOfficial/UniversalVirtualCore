package com.universal.virtual.integrity.api

interface IntegrityCheckRegistry {
    fun register(check: IntegrityCheck)
    fun unregister(checkId: String)
    fun getChecks(): List<IntegrityCheck>
    fun getCheck(checkId: String): IntegrityCheck?
}
