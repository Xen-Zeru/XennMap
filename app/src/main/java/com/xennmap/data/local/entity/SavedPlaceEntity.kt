package com.xennmap.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val note: String,
    val isFavorite: Boolean = false,
    val isNotify: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
