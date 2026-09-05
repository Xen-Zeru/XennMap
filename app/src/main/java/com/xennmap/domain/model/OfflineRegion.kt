package com.xennmap.domain.model

enum class RegionStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED }

/** A downloaded offline map area stored on the device. */
data class OfflineRegion(
    val id: Long = 0,
    val name: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val status: RegionStatus = RegionStatus.QUEUED,
    /** 0..100, only meaningful while DOWNLOADING. */
    val progress: Int = 0,
    val sizeBytes: Long = 0,
    val styleUrl: String,
    val requestedAt: Long,
    val completedAt: Long = 0,
    val lastError: String? = null,
) {
    val isAvailableOffline: Boolean get() = status == RegionStatus.COMPLETED
}

/** A pre-defined download suggestion for common Philippine coastal areas. */
data class RegionPreset(
    val id: String,
    val name: String,
    val description: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val estimatedSizeMb: Int,
    val minZoom: Int = 6,
    val maxZoom: Int = 11,
)
