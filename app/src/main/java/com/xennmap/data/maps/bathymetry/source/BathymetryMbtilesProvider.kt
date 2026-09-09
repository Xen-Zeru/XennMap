package com.xennmap.data.maps.bathymetry.source

import android.content.Context
import com.xennmap.data.maps.bathymetry.model.BathymetryRegion
import com.xennmap.domain.model.BathymetryMetadata
import org.maplibre.android.style.sources.RasterSource
import java.io.File

/**
 * Provides MapLibre RasterSource for bathymetry visualization
 * from local MBTiles files using the mbtiles:// scheme.
 * 
 * The color ramp styling is handled separately in the MapRenderer
 * using the style JSON configuration.
 */
class BathymetryMbtilesProvider constructor(
    private val context: Context,
) {

    private val bathymetryDir = File(context.filesDir, "bathymetry/mbtiles")

    companion object {
        const val SOURCE_ID = "gebco-bathymetry"
        const val TILE_SIZE = 256
        const val MIN_ZOOM = 0
        const val MAX_ZOOM = 13
    }

    /**
     * Get the path to the downloaded MBTiles file for a region.
     */
    fun mbtilesPathFor(regionId: String): String {
        return File(bathymetryDir, "$regionId.mbtiles").absolutePath
    }

    /**
     * Check if MBTiles file exists for region.
     */
    fun hasMbtiles(regionId: String): Boolean {
        return File(mbtilesPathFor(regionId)).exists()
    }

    /**
     * Create RasterSource for MapLibre from local MBTiles.
     */
    fun createRasterSource(regionId: String): RasterSource? {
        val path = mbtilesPathFor(regionId)
        if (!File(path).exists()) return null

        // Use mbtiles:// scheme - verified working in MapLibre Android 11.5.1
        val uri = "mbtiles://$path"
        return RasterSource(SOURCE_ID, uri, TILE_SIZE)
    }

    /**
     * Get metadata for the bathymetry dataset.
     */
    fun getMetadata(regionId: String): BathymetryMetadata {
        return BathymetryMetadata(
            source = "GEBCO 2024 Grid",
            version = "202407",
            resolutionArcSec = 15,
            resolutionMeters = 450,
            verticalDatum = "MSL",
            horizontalDatum = "WGS84",
            attribution = "GEBCO Compilation Group (2024) GEBCO 2024 Grid (doi:10.5285/1c44ce99-0a0d-5f4f-e063-7086abc0ea0f)",
            tileSource = regionId,
        )
    }

    /**
     * Get size of MBTiles file in bytes.
     */
    fun getMbtilesSize(regionId: String): Long {
        return File(mbtilesPathFor(regionId)).length()
    }
}