package com.xennmap.domain.model

/** A recorded trip (breadcrumb trail). */
data class Track(
    val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long = 0,
    val pointCount: Int = 0,
    val distanceMeters: Double = 0.0,
)

/** A single breadcrumb of a recorded trip. */
data class TrackPoint(
    val id: Long = 0,
    val trackId: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float = 0f,
    val bearingDeg: Float = 0f,
    val accuracyMeters: Float = 0f,
    val timestamp: Long,
)
