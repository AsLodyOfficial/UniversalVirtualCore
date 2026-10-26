package com.universal.virtual.integrity.engine

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityCheckRegistry
import com.universal.virtual.integrity.checks.*
import java.util.concurrent.ConcurrentHashMap

class IntegrityCheckRegistryImpl : IntegrityCheckRegistry {
    private val checkMap = ConcurrentHashMap<String, IntegrityCheck>()

    init {
        registerDefaultChecks()
    }

    private fun registerDefaultChecks() {
        register(EnvironmentIntegrityCheck())
        register(DatabaseIntegrityCheck())
        register(DatabaseStorageConsistencyCheck())
        register(PackageIntegrityCheck())
        register(ApkIntegrityCheck())
        register(PackageMetadataIntegrityCheck())
        register(ComponentIntegrityCheck())
        register(UserIntegrityCheck())
        register(InstanceIntegrityCheck())
        register(InstanceStorageIntegrityCheck())
        register(StorageIsolationCheck())
        register(IdentityIntegrityCheck())
        register(ProcessIntegrityCheck())
        register(NativeIntegrityCheck())
        register(ResourceIntegrityCheck())
        register(ConfigurationIntegrityCheck())
        register(PermissionIntegrityCheck())
        register(BackendIntegrityCheck())
        register(VirtualFileIntegrityCheck())
        register(InstallationTransactionIntegrityCheck())
        register(RecoveryIntegrityCheck())
        register(CrossSubsystemIntegrityCheck())
    }

    override fun register(check: IntegrityCheck) {
        checkMap[check.id] = check
    }

    override fun unregister(checkId: String) {
        checkMap.remove(checkId)
    }

    override fun getChecks(): List<IntegrityCheck> = checkMap.values.toList()

    override fun getCheck(checkId: String): IntegrityCheck? = checkMap[checkId]
}
