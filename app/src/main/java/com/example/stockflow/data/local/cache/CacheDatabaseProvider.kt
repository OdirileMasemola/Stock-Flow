package com.example.stockflow.data.local.cache

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Application-scoped singleton for the offline READ cache + WRITE queue database
 * ([StockFlowCacheDatabase.DB_NAME]).
 */
object CacheDatabaseProvider {
    @Volatile
    private var database: StockFlowCacheDatabase? = null

    fun init(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database == null) {
                database = Room.databaseBuilder(
                    context.applicationContext,
                    StockFlowCacheDatabase::class.java,
                    StockFlowCacheDatabase.DB_NAME
                )
                    .addMigrations(
                        StockFlowCacheDatabase.MIGRATION_1_2,
                        StockFlowCacheDatabase.MIGRATION_2_3
                    )
                    .build()
            }
        }
    }

    fun get(): StockFlowCacheDatabase {
        return database
            ?: error("CacheDatabaseProvider.init() must be called from Application.onCreate")
    }

    fun getOrNull(): StockFlowCacheDatabase? = database

    suspend fun clearUser(userId: Int) = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext
        db.productDao().clearUser(userId)
        db.categoryDao().clearUser(userId)
        db.supplierDao().clearUser(userId)
        db.purchaseOrderDao().clearUser(userId)
        db.profileDao().clearUser(userId)
        db.businessDao().clearUser(userId)
        db.dashboardDao().clearUser(userId)
        db.pendingOperationDao().clearUser(userId)
    }

    fun clearUserBlocking(userId: Int) {
        runBlocking { clearUser(userId) }
    }
}
