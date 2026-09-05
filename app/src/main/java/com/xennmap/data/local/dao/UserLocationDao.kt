package com.xennmap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xennmap.data.local.entity.UserLocationEntity

@Dao
interface UserLocationDao {

    @Insert
    suspend fun insert(location: UserLocationEntity)

    @Query("SELECT MAX(timestamp) FROM user_locations")
    suspend fun latestTimestamp(): Long?

    @Query("DELETE FROM user_locations WHERE timestamp < :cutoff")
    suspend fun trimOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM user_locations")
    suspend fun count(): Int
}
