package com.xennmap.data.repository

import com.xennmap.data.local.dao.TrackDao
import com.xennmap.data.local.toDomain
import com.xennmap.data.local.toEntity
import com.xennmap.data.local.entity.TrackEntity
import com.xennmap.domain.model.Track
import com.xennmap.domain.model.TrackPoint
import com.xennmap.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val dao: TrackDao,
) : TrackRepository {

    override fun observeTracks(): Flow<List<Track>> =
        dao.observeTracks().map { rows -> rows.map { it.toDomain() } }

    override fun observeLatestTrack(): Flow<Track?> =
        dao.observeLatest().map { it?.toDomain() }

    override fun observePointCount(): Flow<Int> = dao.observePointCount()

    override fun observeTrackPoints(trackId: Long): Flow<List<TrackPoint>> =
        dao.observePoints(trackId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun points(trackId: Long): List<TrackPoint> =
        dao.points(trackId).map { it.toDomain() }

    override suspend fun createTrack(name: String): Long =
        dao.insertTrack(TrackEntity(name = name, startedAt = System.currentTimeMillis()))

    override suspend fun addPoint(point: TrackPoint) = dao.insertPoint(point.toEntity())

    override suspend fun updateTrack(track: Track) =
        dao.updateTrack(
            TrackEntity(
                id = track.id,
                name = track.name,
                startedAt = track.startedAt,
                endedAt = track.endedAt,
                pointCount = track.pointCount,
                distanceMeters = track.distanceMeters,
            )
        )

    override suspend fun finishTrack(trackId: Long, endedAt: Long, distanceMeters: Double, pointCount: Int) =
        dao.finishTrack(trackId, endedAt, distanceMeters, pointCount)

    override suspend fun deleteTrack(trackId: Long) = dao.deleteTrack(trackId)
}
