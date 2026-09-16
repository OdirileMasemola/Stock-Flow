package com.example.stockflow.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PendingOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(op: PendingOperationEntity)

    @Update
    suspend fun update(op: PendingOperationEntity)

    @Query("SELECT * FROM pending_operations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingOperationEntity?

    @Query(
        """
        SELECT * FROM pending_operations
        WHERE userId = :userId AND status IN ('PENDING', 'SYNCING')
        ORDER BY createdAt ASC
        """
    )
    suspend fun getActiveForUser(userId: Int): List<PendingOperationEntity>

    @Query(
        """
        SELECT * FROM pending_operations
        WHERE userId = :userId AND status = 'PENDING'
        ORDER BY createdAt ASC
        """
    )
    suspend fun getPendingForUser(userId: Int): List<PendingOperationEntity>

    @Query(
        """
        SELECT * FROM pending_operations
        WHERE userId = :userId
        ORDER BY createdAt ASC
        """
    )
    suspend fun getAllForUser(userId: Int): List<PendingOperationEntity>

    @Query(
        """
        SELECT COUNT(*) FROM pending_operations
        WHERE userId = :userId AND status IN ('PENDING', 'SYNCING')
        """
    )
    suspend fun countActive(userId: Int): Int

    @Query(
        """
        SELECT COUNT(*) FROM pending_operations
        WHERE userId = :userId AND status = 'FAILED'
        """
    )
    suspend fun countFailed(userId: Int): Int

    @Query(
        """
        SELECT * FROM pending_operations
        WHERE userId = :userId
          AND entityType = :entityType
          AND localEntityId = :localEntityId
          AND status IN ('PENDING', 'SYNCING', 'FAILED')
        ORDER BY createdAt ASC
        """
    )
    suspend fun getActiveForEntity(
        userId: Int,
        entityType: String,
        localEntityId: String
    ): List<PendingOperationEntity>

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pending_operations WHERE userId = :userId")
    suspend fun clearUser(userId: Int)

    @Query(
        """
        UPDATE pending_operations
        SET status = 'PENDING', updatedAt = :updatedAt
        WHERE userId = :userId AND status = 'SYNCING'
        """
    )
    suspend fun resetSyncingToPending(userId: Int, updatedAt: Long)
}
