package com.xennmap.data.maps

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Estimates offline download size from tile counts.
 *
 * Vector tile sizes vary significantly by zoom level. Calibrated against
 * OpenFreeMap vector tiles (OpenStreetMap-based):
 * - Low zooms (5-8): ~30-50 KB/tile (large tiles, more data)
 * - Mid zooms (9-11): ~15-25 KB/tile
 * - High zooms (12-14): ~3-8 KB/tile (small tiles, less data per tile)
 * - Very high zooms (15+): ~1-3 KB/tile
 *
 * The Philippines (z5-13) ≈ 87,377 tiles ≈ 800-1200 MB depending on tile source.
 */
object RegionSizeEstimator {

    /** Approximate KB per tile by zoom level for OSM vector tiles. */
    private val KB_PER_TILE_BY_ZOOM = mapOf(
        0 to 50.0, 1 to 50.0, 2 to 50.0, 3 to 45.0, 4 to 40.0,
        5 to 35.0, 6 to 30.0, 7 to 25.0, 8 to 22.0, 9 to 18.0,
        10 to 14.0, 11 to 10.0, 12 to 6.0, 13 to 4.0, 14 to 3.0,
        15 to 2.0, 16 to 1.5, 17 to 1.0, 18 to 0.8, 19 to 0.6, 20 to 0.5,
    ).withDefault { 30.0 }

    /** Number of map tiles covering the bounding box at the given zoom. */
    fun tileCount(south: Double, west: Double, north: Double, east: Double, zoom: Int): Long {
        val n = 1L shl zoom
        val tx = ceil((east - west).coerceIn(0.0, 360.0) / 360.0 * n).coerceAtLeast(1.0)
        val ty = ceil((north - south).coerceIn(0.0, 180.0) / 180.0 * n).coerceAtLeast(1.0)
        return (tx * ty).toLong()
    }

    /** Total tiles across the zoom range. */
    fun tileCount(south: Double, west: Double, north: Double, east: Double, minZoom: Int, maxZoom: Int): Long {
        var tiles = 0L
        for (z in minZoom..maxZoom) tiles += tileCount(south, west, north, east, z)
        return tiles
    }

    /** Estimated download size in whole megabytes using per-zoom KB estimates. */
    fun estimateSizeMb(south: Double, west: Double, north: Double, east: Double, minZoom: Int, maxZoom: Int): Int {
        var totalKb = 0.0
        for (z in minZoom..maxZoom) {
            val tiles = tileCount(south, west, north, east, z)
            val kbPerTile: Double = KB_PER_TILE_BY_ZOOM[z] ?: 30.0
            totalKb += tiles.toDouble() * kbPerTile
        }
        return (totalKb / 1024.0).roundToInt().coerceAtLeast(1)
    }

    /**
     * Highest zoom that keeps the framed area within the tile budget.
     * Big frames stop at lower zooms so a stray huge selection can't
     * turn into a multi-gigabyte download.
     *
     * Default budget increased to 50,000 tiles to allow custom areas
     * to reach zoom 13 for coastal detail.
     */
    fun effectiveMaxZoom(
        south: Double, west: Double, north: Double, east: Double,
        minZoom: Int, desiredMax: Int, tileBudget: Long = 50_000L,
    ): Int {
        var total = 0L
        var z = minZoom
        while (z < desiredMax) {
            val n = tileCount(south, west, north, east, z)
            if (total + n > tileBudget) return z
            total += n
            z++
        }
        return desiredMax
    }

    /** Human-readable estimate, e.g. "29 MB". */
    fun estimateSizeMbText(south: Double, west: Double, north: Double, east: Double, minZoom: Int, maxZoom: Int): String =
        "${estimateSizeMb(south, west, north, east, minZoom, maxZoom)} MB"

    /** Returns tile count and estimated MB as a pair. */
    fun estimateTilesAndMb(south: Double, west: Double, north: Double, east: Double, minZoom: Int, maxZoom: Int): Pair<Long, Int> {
        val tiles = tileCount(south, west, north, east, minZoom, maxZoom)
        val mb = estimateSizeMb(south, west, north, east, minZoom, maxZoom)
        return tiles to mb
    }
}
