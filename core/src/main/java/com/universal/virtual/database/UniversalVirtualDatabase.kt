package com.universal.virtual.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.universal.virtual.database.dao.*
import com.universal.virtual.database.entities.*

@Database(
    entities = [
        VirtualUserEntity::class,
        VirtualPackageEntity::class,
        VirtualActivityEntity::class,
        VirtualServiceEntity::class,
        VirtualProviderEntity::class,
        VirtualReceiverEntity::class,
        VirtualPermissionEntity::class,
        VirtualFeatureEntity::class,
        VirtualNativeLibraryEntity::class,
        VirtualInstanceEntity::class,
        VirtualProcessEntity::class,
        DiagnosticEventEntity::class,
        StorageRecordEntity::class,
        CrashRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class UniversalVirtualDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun packageDao(): PackageDao
    abstract fun instanceDao(): InstanceDao
    abstract fun processDao(): ProcessDao
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun storageDao(): StorageDao

    companion object {
        @Volatile
        private var INSTANCE: UniversalVirtualDatabase? = null

        fun getInstance(context: Context): UniversalVirtualDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val targetContext = try {
                        context.applicationContext ?: context
                    } catch (_: Exception) {
                        context
                    }
                    val instance = Room.databaseBuilder(
                        targetContext,
                        UniversalVirtualDatabase::class.java,
                        "universal_virtual.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    INSTANCE = instance
                    instance
                }
            }
        }

        fun setInstanceForTesting(database: UniversalVirtualDatabase?) {
            INSTANCE = database
        }
    }
}