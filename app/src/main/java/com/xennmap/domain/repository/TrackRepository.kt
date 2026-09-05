package com.xennmap.domain.repository

import com.xennmap.domain.model.Track
import com.xennmap.domain.model.TrackPoint
import kotlinx.coroutines.flow.Flow

/** Local storage of recorded trips (breadcrumb trails). */
interface TrackRepository {

    fun observeTracks(): Flow<List<Track>>

    fun observeLatestTrack(): Flow<Track?>

    fun observePointCount(): Flow<Int>

    fun observeTrackPoints(trackId: Long): Flow<List<TrackPoint>>

    suspend fun points(trackId: Long): List<TrackPoint>

    suspend fun createTrack(name: String): Long

    suspend fun addPoint(point: TrackPoint)

    suspend fun updateTrack(track: Track)

    suspend fun finishTrack(trackId: Long, endedAt: Long, distanceMeters: Double, pointCount: Int)

    suspend fun deleteTrack(trackId: Long)
}
