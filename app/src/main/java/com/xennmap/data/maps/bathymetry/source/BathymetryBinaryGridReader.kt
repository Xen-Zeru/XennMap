package com.xennmap.data.maps.bathymetry.source

import android.content.Context
import com.xennmap.data.maps.bathymetry.model.DepthResult
import com.xennmap.domain.model.BathymetryMetadata
import com.xennmap.domain.model.TerrainType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * Reads GEBCO 2024 binary grid tiles for point depth queries.
 *
 * Tile format:
 * - 1° × 1° tiles (matching GEBCO native grid)
 * - 15 arc-second resolution = 240 cells per degree
 * - Each tile: 240 × 240 = 57,600 cells
 * - Each cell: int16 = depth in meters (native GEBCO precision)
 * - NoData value: -32768 (int16 min)
 * - File size per tile: 57,600 × 2 bytes = 115,200 bytes (~112 KB)
 *
 * Naming convention: gebco_N{lat}_E{lon}.bin (e.g., gebco_N10_E123.bin)
 * Latitude: 0-90 (N/S), Longitude: 0-180 (E/W)
 * Philippine coverage: N4-N21, E116-E127
 */
class BathymetryBinaryGridReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val tileCache = BathymetryTileCache()

    companion object {
        const val TILE_SIZE_DEG = 1
        const val RESOLUTION_ARC_SEC = 15
        const val CELLS_PER_DEG = 3600 / RESOLUTION_ARC_SEC // 240
        const val CELLS_PER_TILE = CELLS_PER_DEG * CELLS_PER_DEG // 57,600
        const val BYTES_PER_CELL = 2 // int16
        const val TILE_BYTES = CELLS_PER_TILE * BYTES_PER_CELL // 115,200
        const val NO_DATA_INT16 = -32768
        const val GRID_DIR = "bathymetry/gebco2024_tiles"
    }

    /**
     * Get depth at coordinate from binary grid.
     * Returns DepthResult with charted GEBCO integer-meter value preserved.
     */
    suspend fun depthAt(latitude: Double, longitude: Double): DepthResult = withContext(Dispatchers.IO) {
        // Philippine bounding box check
        if (!inPhilippinesCoverage(latitude, longitude)) {
            return@withContext DepthResult.NoCoverage("Outside Philippines bathymetry coverage")
        }

        val tileKey = tileKeyFor(latitude, longitude)
        val tile = tileCache.getOrLoad(tileKey) { loadTile(tileKey) }
            ?: return@withContext DepthResult.NoData("Tile not found: $tileKey")

        val (cellX, cellY) = cellIndicesFor(latitude, longitude)
        val depthMeters = tile.readDepthMeters(cellX, cellY)

        return@withContext when (depthMeters) {
            NO_DATA_INT16 -> DepthResult.NoData("GEBCO NoData value at this cell")
            in Int.MIN_VALUE..-1 -> DepthResult.Success(
                meters = depthMeters.toDouble(), // negative = depth below MSL
                terrain = when {
                    depthMeters > -5 -> TerrainType.COASTAL
                    else -> TerrainType.SEA
                },
                metadata = BathymetryMetadata(
                    source = "GEBCO 2024 Grid",
                    version = "202407",
                    resolutionArcSec = RESOLUTION_ARC_SEC,
                    resolutionMeters = 450, // at PH latitudes
                    verticalDatum = "MSL",
                    horizontalDatum = "WGS84",
                    attribution = "GEBCO Compilation Group (2024) GEBCO 2024 Grid (doi:10.5285/1c44ce99-0a0d-5f4f-e063-7086abc0ea0f)",
                    tileSource = "gebco2024_ph_v202407",
                )
            )
            in 0..Int.MAX_VALUE -> DepthResult.Land(elevationMeters = depthMeters.toDouble())
            else -> DepthResult.Error("Unexpected depth value: $depthMeters")
        }
    }

    private fun inPhilippinesCoverage(lat: Double, lon: Double): Boolean {
        // GEBCO Philippines subset bounding box
        return lat in 4.0..21.5 && lon in 116.0..127.0
    }

    private fun tileKeyFor(lat: Double, lon: Double): String {
        // Tile covers [lat, lat+1) × [lon, lon+1) for positive coordinates
        val tileLat = kotlin.math.floor(lat).toInt()
        val tileLon = kotlin.math.floor(lon).toInt()
        return "gebco_N${tileLat}_E${tileLon}"
    }

    private fun cellIndicesFor(lat: Double, lon: Double): Pair<Int, Int> {
        // Within 1° tile: 0-239
        // Latitude increases northward, but grid row 0 = northern edge
        val latFrac = lat - kotlin.math.floor(lat)
        val lonFrac = lon - kotlin.math.floor(lon)
        val cellX = (lonFrac * CELLS_PER_DEG).toInt().coerceIn(0, CELLS_PER_DEG - 1)
        // Flip Y because GEBCO is north-up but array index 0 = north edge
        val cellY = ((1.0 - latFrac) * CELLS_PER_DEG).toInt().coerceIn(0, CELLS_PER_DEG - 1)
        return cellX to cellY
    }

    private fun loadTile(tileKey: String): BathymetryTile? {
        val file = File(context.filesDir, "$GRID_DIR/$tileKey.bin")
        if (!file.exists()) {
            // Try assets as fallback (for bundled tiles)
            return try {
                val assetFile = "bathymetry/tiles/$tileKey.bin"
                context.assets.open(assetFile).use { input ->
                    val buffer = ByteArray(TILE_BYTES)
                    if (input.read(buffer) == TILE_BYTES) {
                        BathymetryTile(tileKey, buffer)
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }

        return try {
            val buffer = ByteArray(TILE_BYTES)
            RandomAccessFile(file, "r").use { raf ->
                if (raf.read(buffer) == TILE_BYTES) {
                    BathymetryTile(tileKey, buffer)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}