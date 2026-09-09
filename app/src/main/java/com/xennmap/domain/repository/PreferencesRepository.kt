package com.xennmap.domain.repository

import com.xennmap.domain.model.AppSettings
import com.xennmap.domain.model.BathymetryData
import com.xennmap.domain.model.DepthResult
import kotlinx.coroutines.flow.Flow

/** User preferences (DataStore) + bathymetry data access. */
interface PreferencesRepository {

    val settings: Flow<AppSettings>

    val settingsState: kotlinx.coroutines.flow.StateFlow<AppSettings>

    suspend fun setThemeMode(mode: com.xennmap.domain.model.ThemeMode)

    suspend fun setDistanceUnit(unit: com.xennmap.domain.model.DistanceUnit)

    suspend fun setDepthUnit(unit: com.xennmap.domain.model.DepthUnit)

    suspend fun setSpeedUnit(unit: com.xennmap.domain.model.SpeedUnit)

    suspend fun setAccuracyMode(mode: com.xennmap.domain.model.LocationAccuracyMode)

    suspend fun setTrackingInterval(seconds: Int)

    suspend fun setTileStyleUrl(url: String)

    suspend fun setLayers(layers: com.xennmap.domain.model.MapLayers)

    suspend fun saveCamera(lat: Double, lng: Double, zoom: Double)
}

interface BathymetryRepository {

    /** Metadata about the dataset currently powering the bathymetry layer. */
    val dataset: com.xennmap.domain.model.BathymetryDataset

    /** Lazily generated (and cached) GeoJSON for the sample bathymetry areas. */
    suspend fun data(): BathymetryData

    /** Raw simplified coastline GeoJSON used as the offline land layer. */
    suspend fun coastlineGeoJson(): String

    /**
     * Chart-model depth in meters at the given position, or null when the
     * position is on land, too close to shore, or outside coverage.
     * This is depth FROM THE DATASET, never a live phone measurement.
     */
    suspend fun depthAt(latitude: Double, longitude: Double): Double?

    /**
     * Full depth result with metadata at the given position.
     * Returns Success with metadata, or NoCoverage/NoData/Land/Error as appropriate.
     */
    suspend fun depthResultAt(latitude: Double, longitude: Double): DepthResult

    /** Classifies a coordinate as sea / coastal / land / outside chart coverage. */
    suspend fun classifyAt(latitude: Double, longitude: Double): com.xennmap.domain.model.TerrainType
}
