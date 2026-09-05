package com.xennmap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.xennmap.data.local.entity.OfflineRegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineRegionDao {

    @Query("SELECT * FROM offline_regions ORDER BY requestedAt DESC")
    fun observeAll(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions ORDER BY requestedAt DESC")
    suspend fun allOnce(): List<OfflineRegionEntity>

    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun byId(id: Long): OfflineRegionEntity?

    @Query("SELECT * FROM offline_regions WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): OfflineRegionEntity?

    @Query("SELECT COUNT(*) FROM offline_regions WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(region: OfflineRegionEntity): Long

    @Update
    suspend fun update(region: OfflineRegionEntity)

    @Query("UPDATE offline_regions SET status = :status, progress = :progress, sizeBytes = :sizeBytes WHERE id = :id AND status = 'DOWNLOADING'")
    suspend fun updateProgress(id: Long, status: String, progress: Int, sizeBytes: Long)

    @Query("UPDATE offline_regions SET status = :status, progress = 100, sizeBytes = :sizeBytes, completedAt = :completedAt, lastError = NULL WHERE id = :id")
    suspend fun updateCompleted(id: Long, status: String, sizeBytes: Long, completedAt: Long)

    @Query("UPDATE offline_regions SET status = 'FAILED', lastError = :error WHERE id = :id")
    suspend fun updateFailed(id: Long, error: String)

    @Query("UPDATE offline_regions SET sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateSize(id: Long, sizeBytes: Long)

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun delete(id: Long)
}
