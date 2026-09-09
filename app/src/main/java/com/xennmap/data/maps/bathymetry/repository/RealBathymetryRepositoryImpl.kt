package com.xennmap.data.maps.bathymetry.repository

import android.content.Context
import com.xennmap.data.maps.bathymetry.model.BathymetryRegion
import com.xennmap.data.maps.bathymetry.model.DepthResult as DataDepthResult
import com.xennmap.data.maps.bathymetry.source.BathymetryBinaryGridReader
import com.xennmap.data.maps.bathymetry.source.BathymetryMbtilesProvider
import com.xennmap.domain.model.BathymetryData
import com.xennmap.domain.model.BathymetryDataset
import com.xennmap.domain.model.DepthResult
import com.xennmap.domain.model.BathymetryMetadata
import com.xennmap.domain.model.TerrainType
import com.xennmap.domain.repository.BathymetryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real bathymetry repository using GEBCO 2024 data.
 * Provides point depth queries from binary grid tiles.
 * No synthetic fallback - returns NoData/NoCoverage when real data unavailable.
 */
@Singleton
class RealBathymetryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val binaryGridReader: BathymetryBinaryGridReader,
    private val mbtilesProvider: BathymetryMbtilesProvider,
) : BathymetryRepository {

    override val dataset: BathymetryDataset = BathymetryDataset(
        id = "gebco2024",
        name = "GEBCO 2024 Grid",
        source = "GEBCO Compilation Group (2024)",
        license = "CC-BY 4.0",
        vintage = "202407",
        coverageNote = "Global 15 arc-second grid (~450m at PH latitudes)",
        isSample = false,
    )

    private val mutex = Mutex()
    @Volatile private var cachedData: BathymetryData? = null
    @Volatile private var cachedCoastline: String? = null

    // Track which regions have been downloaded
    private val _downloadedRegions = MutableStateFlow<Set<String>>(emptySet())
    val downloadedRegions = _downloadedRegions.asStateFlow()

    init {
        checkDownloadedRegions()
    }

    private fun checkDownloadedRegions() {
        val downloaded = BathymetryRegion.values().filter { region ->
            mbtilesProvider.hasMbtiles(region.id)
        }.map { it.id }.toSet()
        _downloadedRegions.value = downloaded
    }

    /** Raw simplified coastline GeoJSON (also used as the offline land layer). */
    override suspend fun coastlineGeoJson(): String = withContext(Dispatchers.IO) {
        cachedCoastline ?: context.assets.open("geo/coastline_ph.geojson").bufferedReader().use {
            it.readText()
        }.also { cachedCoastline = it }
    }

    /** Bathymetry data for rendering (GeoJSON not used for real bathymetry - MBTiles instead). */
    override suspend fun data(): BathymetryData = mutex.withLock {
        cachedData ?: generatePlaceholderData().also { cachedData = it }
    }

    private fun generatePlaceholderData(): BathymetryData {
        // Return empty GeoJSON - real rendering uses MBTiles via BathymetryMbtilesProvider
        val emptyCollection = """{"type":"FeatureCollection","features":[]}"""
        return BathymetryData(
            dataset = dataset,
            fillGeoJson = emptyCollection,
            contourGeoJson = emptyCollection,
            labelGeoJson = emptyCollection,
            bandBreaks = listOf(5, 10, 20, 50, 100, 200),
        )
    }

    /**
     * Get chart-model depth at position from real GEBCO binary grid.
     * Returns depth in meters or null for land/no-data/no-coverage.
     * NO synthetic fallback.
     */
    override suspend fun depthAt(latitude: Double, longitude: Double): Double? = withContext(Dispatchers.Default) {
        val result = binaryGridReader.depthAt(latitude, longitude)
        when (result) {
            is DataDepthResult.Success -> result.meters
            is DataDepthResult.Land -> null
            is DataDepthResult.NoData, is DataDepthResult.NoCoverage, is DataDepthResult.Error -> null
        }
    }

    /**
     * Full depth result with metadata at the given position.
     * Converts data-layer DepthResult to domain DepthResult.
     */
    override suspend fun depthResultAt(latitude: Double, longitude: Double): DepthResult = withContext(Dispatchers.Default) {
        val dataResult = binaryGridReader.depthAt(latitude, longitude)
        convertToDomain(dataResult)
    }

    private fun convertToDomain(dataResult: DataDepthResult): DepthResult = when (dataResult) {
        is DataDepthResult.Success -> DepthResult.Success(
            meters = dataResult.meters,
            terrain = dataResult.terrain,
            metadata = BathymetryMetadata(
                source = dataResult.metadata.source,
                version = dataResult.metadata.version,
                resolutionArcSec = dataResult.metadata.resolutionArcSec,
                resolutionMeters = dataResult.metadata.resolutionMeters,
                verticalDatum = dataResult.metadata.verticalDatum,
                horizontalDatum = dataResult.metadata.horizontalDatum,
                attribution = dataResult.metadata.attribution,
                tileSource = dataResult.metadata.tileSource,
            )
        )
        is DataDepthResult.NoCoverage -> DepthResult.NoCoverage(dataResult.reason)
        is DataDepthResult.NoData -> DepthResult.NoData(dataResult.reason)
        is DataDepthResult.Land -> DepthResult.Land(dataResult.elevationMeters)
        is DataDepthResult.Error -> DepthResult.Error(dataResult.message)
    }

    /** Classify coordinate using real depth data. */
    override suspend fun classifyAt(latitude: Double, longitude: Double): TerrainType = withContext(Dispatchers.Default) {
        val result = binaryGridReader.depthAt(latitude, longitude)
        when (result) {
            is DataDepthResult.Success -> result.terrain
            is DataDepthResult.Land -> TerrainType.LAND
            is DataDepthResult.NoCoverage -> TerrainType.OUTSIDE
            is DataDepthResult.NoData -> TerrainType.OUTSIDE
            is DataDepthResult.Error -> TerrainType.OUTSIDE
        }
    }

    /** Check if a region's bathymetry is downloaded. */
    fun isRegionDownloaded(region: BathymetryRegion): Boolean = mbtilesProvider.hasMbtiles(region.id)

    /** Get metadata for a downloaded region. */
    fun getRegionMetadata(region: BathymetryRegion): BathymetryMetadata = mbtilesProvider.getMetadata(region.id)

    /** Get MBTiles file size for a region. */
    fun getRegionSize(region: BathymetryRegion): Long = mbtilesProvider.getMbtilesSize(region.id)
}