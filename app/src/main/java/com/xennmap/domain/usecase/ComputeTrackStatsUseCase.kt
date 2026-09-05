package com.xennmap.domain.usecase

import com.xennmap.domain.model.TrackPoint
import com.xennmap.utils.GeoUtils
import javax.inject.Inject
import kotlin.math.roundToInt

data class TrackStats(
    val distanceMeters: Double,
    val durationMs: Long,
    val averageSpeedMps: Float,
    val maxSpeedMps: Float,
    val pointCount: Int,
)

/** Aggregates breadcrumb points into trip statistics. */
class ComputeTrackStatsUseCase @Inject constructor() {

    operator fun invoke(
        points: List<TrackPoint>,
        startedAt: Long,
        endedAt: Long,
    ): TrackStats {
        var distance = 0.0
        var maxSpeed = 0f
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            distance += GeoUtils.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        points.forEach { if (it.speedMps > maxSpeed) maxSpeed = it.speedMps }
        val duration = ((endedAt - startedAt).coerceAtLeast(0))
        val avg = if (duration > 0) ((distance / duration) * 1000).toFloat() else 0f
        return TrackStats(
            distanceMeters = distance,
            durationMs = duration,
            averageSpeedMps = avg,
            maxSpeedMps = maxSpeed,
            pointCount = points.size,
        )
    }
}
