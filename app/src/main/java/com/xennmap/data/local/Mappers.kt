package com.xennmap.data.local

import com.xennmap.data.local.entity.OfflineRegionEntity
import com.xennmap.data.local.entity.SavedPlaceEntity
import com.xennmap.data.local.entity.TrackEntity
import com.xennmap.data.local.entity.TrackPointEntity
import com.xennmap.data.local.entity.UserLocationEntity
import com.xennmap.domain.model.OfflineRegion
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.domain.model.RegionStatus
import com.xennmap.domain.model.SavedPlace
import com.xennmap.domain.model.Track
import com.xennmap.domain.model.TrackPoint

fun SavedPlaceEntity.toDomain(): SavedPlace = SavedPlace(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    category = runCatching { PlaceCategory.valueOf(category) }.getOrDefault(PlaceCategory.OTHER),
    note = note,
    isFavorite = isFavorite,
    isNotify = isNotify,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SavedPlace.toEntity(): SavedPlaceEntity = SavedPlaceEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    category = category.name,
    note = note,
    isFavorite = isFavorite,
    isNotify = isNotify,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    name = name,
    startedAt = startedAt,
    endedAt = endedAt,
    pointCount = pointCount,
    distanceMeters = distanceMeters,
)

fun TrackPointEntity.toDomain(): TrackPoint = TrackPoint(
    id = id,
    trackId = trackId,
    latitude = latitude,
    longitude = longitude,
    speedMps = speedMps,
    bearingDeg = bearingDeg,
    accuracyMeters = accuracyMeters,
    timestamp = timestamp,
)

fun TrackPoint.toEntity(): TrackPointEntity = TrackPointEntity(
    id = id,
    trackId = trackId,
    latitude = latitude,
    longitude = longitude,
    speedMps = speedMps,
    bearingDeg = bearingDeg,
    accuracyMeters = accuracyMeters,
    timestamp = timestamp,
)

fun OfflineRegionEntity.toDomain(): OfflineRegion = OfflineRegion(
    id = id,
    name = name,
    south = south,
    west = west,
    north = north,
    east = east,
    minZoom = minZoom,
    maxZoom = maxZoom,
    status = runCatching { RegionStatus.valueOf(status) }.getOrDefault(RegionStatus.FAILED),
    progress = progress,
    sizeBytes = sizeBytes,
    styleUrl = styleUrl,
    requestedAt = requestedAt,
    completedAt = completedAt,
    lastError = lastError,
)
