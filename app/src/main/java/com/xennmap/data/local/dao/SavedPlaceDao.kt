package com.xennmap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.xennmap.data.local.entity.SavedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {

    @Query("SELECT * FROM saved_places ORDER BY isFavorite DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places WHERE isNotify = 1 ORDER BY updatedAt DESC")
    fun observeNotifyPlaces(): Flow<List<SavedPlaceEntity>>

    @Query("UPDATE saved_places SET isNotify = :notify, updatedAt = :now WHERE id = :id")
    suspend fun setNotify(id: Long, notify: Boolean, now: Long)

    @Query("SELECT * FROM saved_places WHERE id = :id")
    fun observeById(id: Long): Flow<SavedPlaceEntity?>

    @Query("SELECT * FROM saved_places WHERE id = :id")
    suspend fun byId(id: Long): SavedPlaceEntity?

    @Query("SELECT COUNT(*) FROM saved_places")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(place: SavedPlaceEntity): Long

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE saved_places SET isFavorite = NOT isFavorite, updatedAt = :now WHERE id = :id")
    suspend fun toggleFavorite(id: Long, now: Long)
}
