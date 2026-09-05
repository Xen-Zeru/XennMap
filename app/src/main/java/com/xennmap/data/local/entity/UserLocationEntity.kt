package com.xennmap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lightweight position history (one row per minute while GPS is active).
 * Used for recent-visit bookkeeping; kept small by a maintenance job.
 */
@Entity(tableName = "user_locations", indices = [Index("timestamp")])
data class UserLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long,
)
