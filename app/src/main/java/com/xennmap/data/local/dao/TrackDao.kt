package com.xennmap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.xennmap.data.local.entity.TrackEntity
import com.xennmap.data.local.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY startedAt DESC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY startedAt DESC LIMIT 1")
    fun observeLatest(): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: Long): TrackEntity?

    @Query("SELECT COUNT(*) FROM track_points")
    fun observePointCount(): Flow<Int>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    fun observePoints(trackId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    suspend fun points(trackId: Long): List<TrackPointEntity>

    @Insert
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Insert
    suspend fun insertPoint(point: TrackPointEntity)

    @Query(
        "UPDATE tracks SET endedAt = :endedAt, distanceMeters = :distance, pointCount = :pointCount WHERE id = :id"
    )
    suspend fun finishTrack(id: Long, endedAt: Long, distance: Double, pointCount: Int)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)
}
