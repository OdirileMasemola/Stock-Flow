package com.example.stockflow.data.local.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedProduct::class,
        CachedCategory::class,
        CachedSupplier::class,
        CachedPurchaseOrder::class,
        CachedPurchaseOrderItem::class,
        CachedProfile::class,
        CachedBusiness::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StockFlowCacheDatabase : RoomDatabase() {
    abstract fun productDao(): ProductCacheDao
    abstract fun categoryDao(): CategoryCacheDao
    abstract fun supplierDao(): SupplierCacheDao
    abstract fun purchaseOrderDao(): PurchaseOrderCacheDao
    abstract fun profileDao(): ProfileCacheDao
    abstract fun businessDao(): BusinessCacheDao

    companion object {
        const val DB_NAME = "stockflow_read_cache.db"
    }
}
