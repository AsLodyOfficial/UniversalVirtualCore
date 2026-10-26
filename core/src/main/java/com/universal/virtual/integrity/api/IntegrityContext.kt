package com.universal.virtual.integrity.api

import com.universal.virtual.api.interfaces.IVirtualPackageManager
import com.universal.virtual.common.events.VirtualEventBus
import com.universal.virtual.database.UniversalVirtualDatabase
import com.universal.virtual.integrity.hashing.IntegrityHashStore
import com.universal.virtual.integrity.models.IntegrityScanMode
import com.universal.virtual.integrity.models.IntegrityScope
import com.universal.virtual.pkg.PackageStorageManager
import java.io.File

data class IntegrityContext(
    val scope: IntegrityScope = IntegrityScope.EntireEnvironment,
    val mode: IntegrityScanMode = IntegrityScanMode.FULL,
    val database: UniversalVirtualDatabase,
    val storageManager: PackageStorageManager,
    val rootStorageDir: File,
    val packageManager: IVirtualPackageManager? = null,
    val hashStore: IntegrityHashStore = IntegrityHashStore(),
    val eventBus: VirtualEventBus? = null
)
