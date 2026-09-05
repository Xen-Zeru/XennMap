package com.xennmap.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata of a downloaded offline map area. The actual tiles are managed by the
 * MapLibre offline pack storage; this row is the app-facing bookkeeping.
 */
@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val status: String,
    val progress: Int = 0,
    val sizeBytes: Long = 0,
    val styleUrl: String,
    val requestedAt: Long,
    val completedAt: Long = 0,
    val lastError: String? = null,
)
