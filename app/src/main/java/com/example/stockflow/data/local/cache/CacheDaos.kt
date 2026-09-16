package com.example.stockflow.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ProductCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedProduct>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedProduct)

    @Query("SELECT * FROM cached_products WHERE userId = :userId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(userId: Int): List<CachedProduct>

    @Query("SELECT * FROM cached_products WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getById(userId: Int, id: Int): CachedProduct?

    @Query("DELETE FROM cached_products WHERE userId = :userId")
    suspend fun clearUser(userId: Int)

    @Query("DELETE FROM cached_products WHERE userId = :userId AND id = :id")
    suspend fun deleteById(userId: Int, id: Int)

    @Transaction
    suspend fun replaceAll(userId: Int, items: List<CachedProduct>) {
        clearUser(userId)
        if (items.isNotEmpty()) upsertAll(items)
    }
}

@Dao
interface CategoryCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedCategory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedCategory)

    @Query("SELECT * FROM cached_categories WHERE userId = :userId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(userId: Int): List<CachedCategory>

    @Query("SELECT * FROM cached_categories WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getById(userId: Int, id: Int): CachedCategory?

    @Query("DELETE FROM cached_categories WHERE userId = :userId")
    suspend fun clearUser(userId: Int)

    @Transaction
    suspend fun replaceAll(userId: Int, items: List<CachedCategory>) {
        clearUser(userId)
        if (items.isNotEmpty()) upsertAll(items)
    }
}

@Dao
interface SupplierCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedSupplier>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedSupplier)

    @Query("SELECT * FROM cached_suppliers WHERE userId = :userId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(userId: Int): List<CachedSupplier>

    @Query("SELECT * FROM cached_suppliers WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getById(userId: Int, id: Int): CachedSupplier?

    @Query("DELETE FROM cached_suppliers WHERE userId = :userId")
    suspend fun clearUser(userId: Int)

    @Query("DELETE FROM cached_suppliers WHERE userId = :userId AND id = :id")
    suspend fun deleteById(userId: Int, id: Int)

    @Transaction
    suspend fun replaceAll(userId: Int, items: List<CachedSupplier>) {
        clearUser(userId)
        if (items.isNotEmpty()) upsertAll(items)
    }
}

@Dao
interface PurchaseOrderCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(items: List<CachedPurchaseOrder>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(item: CachedPurchaseOrder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<CachedPurchaseOrderItem>)

    @Query("SELECT * FROM cached_purchase_orders WHERE userId = :userId ORDER BY id DESC")
    suspend fun getAllOrders(userId: Int): List<CachedPurchaseOrder>

    @Query("SELECT * FROM cached_purchase_orders WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getOrderById(userId: Int, id: Int): CachedPurchaseOrder?

    @Query("SELECT * FROM cached_purchase_order_items WHERE userId = :userId AND purchaseOrderId = :purchaseOrderId")
    suspend fun getItemsForOrder(userId: Int, purchaseOrderId: Int): List<CachedPurchaseOrderItem>

    @Query("DELETE FROM cached_purchase_order_items WHERE userId = :userId")
    suspend fun clearItemsUser(userId: Int)

    @Query("DELETE FROM cached_purchase_orders WHERE userId = :userId")
    suspend fun clearOrdersUser(userId: Int)

    @Query("DELETE FROM cached_purchase_order_items WHERE userId = :userId AND purchaseOrderId = :purchaseOrderId")
    suspend fun clearItemsForOrder(userId: Int, purchaseOrderId: Int)

    @Transaction
    suspend fun clearUser(userId: Int) {
        clearItemsUser(userId)
        clearOrdersUser(userId)
    }

    @Transaction
    suspend fun replaceAll(userId: Int, orders: List<CachedPurchaseOrder>, items: List<CachedPurchaseOrderItem>) {
        clearUser(userId)
        if (orders.isNotEmpty()) upsertOrders(orders)
        if (items.isNotEmpty()) upsertItems(items)
    }

    @Transaction
    suspend fun upsertOrderWithItems(order: CachedPurchaseOrder, items: List<CachedPurchaseOrderItem>) {
        upsertOrder(order)
        clearItemsForOrder(order.userId, order.id)
        if (items.isNotEmpty()) upsertItems(items)
    }
}

@Dao
interface ProfileCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedProfile)

    @Query("SELECT * FROM cached_profiles WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: Int): CachedProfile?

    @Query("DELETE FROM cached_profiles WHERE userId = :userId")
    suspend fun clearUser(userId: Int)
}

@Dao
interface BusinessCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedBusiness)

    @Query("SELECT * FROM cached_businesses WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: Int): CachedBusiness?

    @Query("DELETE FROM cached_businesses WHERE userId = :userId")
    suspend fun clearUser(userId: Int)
}
