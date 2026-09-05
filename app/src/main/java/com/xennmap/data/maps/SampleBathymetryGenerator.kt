package com.xennmap.data.maps

import com.xennmap.domain.model.TerrainType
import com.xennmap.utils.GeoJsonBuilder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates the SAMPLE bathymetry layer used by the MVP demo — covering ALL
 * Philippine waters on a uniform grid.
 *
 * IMPORTANT SAFETY NOTE
 * ---------------------
 * This grid is a synthetic depth model derived from distance-to-coast, included so the
 * bathymetry visualisation architecture has data to render out of the box. It is clearly
 * labelled "sample" everywhere in the UI. Production depth data must come from an
 * authoritative dataset (e.g. GEBCO-derived contours or NAMRIA charts, respecting their
 * licenses). Bathymetry is NEVER presented as a live sonar measurement.
 */
object SampleBathymetryGenerator {

    data class CoastPoly(val south: Double, val west: Double, val north: Double, val east: Double, val rings: List<List<DoubleArray>>)

    /** Grid resolutions (degrees): fine along coasts, coarse offshore. */
    const val FINE_STEP = 0.05
    const val COARSE_STEP = 0.15

    /** Coverage of the demo model: the entire Philippine archipelago. */
    private const val PH_SOUTH = 4.0
    private const val PH_WEST = 116.0
    private const val PH_NORTH = 21.5
    private const val PH_EAST = 127.0

    /** Depth band breakpoints in meters. */
    val BAND_BREAKS = listOf(5, 10, 20, 50, 100, 200)

    private const val LAND_THRESHOLD_KM = 0.5
    private const val LAST_BAND = 6

    /** Max cells per merged feature — huge single polygons fail to render at low zoom. */
    private const val MAX_RUN_CELLS = 32

    /**
     * Cells farther than this (degrees) from every coastline bounding box are
     * guaranteed to be in the deepest band, so the expensive per-segment
     * distance is skipped for open ocean (keeps whole-country generation fast).
     */
    private const val DEEP_BAND_MIN_DEG = 0.21

    fun bandIndex(depthMeters: Double): Int {
        for ((index, breakM) in BAND_BREAKS.withIndex()) {
            if (depthMeters < breakM) return index
        }
        return BAND_BREAKS.size
    }

    /** True when the point lies inside the model's coverage (Philippine waters). */
    fun inCoverage(latitude: Double, longitude: Double): Boolean =
        longitude in PH_WEST..PH_EAST && latitude in PH_SOUTH..PH_NORTH

    /**
     * Chart-model depth in meters at an arbitrary water position.
     * Returns null on land, within the shoreline band, or outside coverage.
     * IMPORTANT: model-derived depth for the chart layer — never a live measurement.
     */
    fun depthAtPosition(latitude: Double, longitude: Double, polys: List<CoastPoly>): Double? {
        if (!inCoverage(latitude, longitude)) return null
        if (isLand(longitude, latitude, polys)) return null
        val distanceKm = minDistanceKm(longitude, latitude, polys)
        if (distanceKm < LAND_THRESHOLD_KM) return null
        return depthMeters(distanceKm, longitude, latitude)
    }

    /** Classifies a coordinate for the "Selected Location" sheet. */
    fun classifyPosition(latitude: Double, longitude: Double, polys: List<CoastPoly>): TerrainType {
        if (!inCoverage(latitude, longitude)) return TerrainType.OUTSIDE
        if (isLand(longitude, latitude, polys)) return TerrainType.LAND
        val distanceKm = minDistanceKm(longitude, latitude, polys)
        return if (distanceKm < LAND_THRESHOLD_KM) TerrainType.COASTAL else TerrainType.SEA
    }

    fun generate(polys: List<CoastPoly>): Triple<String, String, String> {
        // TWO-TIER grid: fine cells along coasts (where depth detail matters),
        // coarse cells offshore (vast deep ocean with almost no band changes).
        // This keeps the whole-country payload small enough for low-zoom tiles.
        val fineCols = ((PH_EAST - PH_WEST) / FINE_STEP).toInt()
        val fineRows = ((PH_NORTH - PH_SOUTH) / FINE_STEP).toInt()
        val fineBands = Array(fineRows) { IntArray(fineCols) { -1 } } // -1 land, -2 deferred

        for (r in 0 until fineRows) {
            val lat = PH_SOUTH + (r + 0.5) * FINE_STEP
            for (c in 0 until fineCols) {
                val lon = PH_WEST + (c + 0.5) * FINE_STEP
                if (isLand(lon, lat, polys)) continue
                if (bboxDistanceDeg(lon, lat, polys) > DEEP_BAND_MIN_DEG) {
                    fineBands[r][c] = -2 // open ocean — handled by the coarse pass
                    continue
                }
                val dKm = minDistanceKm(lon, lat, polys)
                if (dKm < LAND_THRESHOLD_KM) continue
                fineBands[r][c] = bandIndex(depthMeters(dKm, lon, lat)).coerceAtMost(4)
            }
        }

        val coarseCols = ((PH_EAST - PH_WEST) / COARSE_STEP).toInt()
        val coarseRows = ((PH_NORTH - PH_SOUTH) / COARSE_STEP).toInt()
        val coarseBands = Array(coarseRows) { IntArray(coarseCols) { -1 } }
        for (r in 0 until coarseRows) {
            val lat = PH_SOUTH + (r + 0.5) * COARSE_STEP
            for (c in 0 until coarseCols) {
                val lon = PH_WEST + (c + 0.5) * COARSE_STEP
                if (isLand(lon, lat, polys)) continue
                val dKm = minDistanceKm(lon, lat, polys)
                if (dKm < LAND_THRESHOLD_KM) continue
                val band = bandIndex(depthMeters(dKm, lon, lat))
                if (band >= 5) coarseBands[r][c] = band
            }
        }

        // Fills: coarse deep-ocean rectangles first, fine coastal rectangles
        // painted over them (later features win at the same layer position).
        val fills = ArrayList<String>(16_384)
        for (r in 0 until coarseRows) {
            var c = 0
            while (c < coarseCols) {
                val band = coarseBands[r][c]
                if (band < 0) { c++; continue }
                var end = c
                while (end + 1 < coarseCols && coarseBands[r][end + 1] == band) end++
                var start = c
                while (start <= end) {
                    val stop = minOf(start + MAX_RUN_CELLS - 1, end)
                    val w = snap(PH_WEST + start * COARSE_STEP)
                    val e = snap(PH_WEST + (stop + 1) * COARSE_STEP)
                    val s = snap(PH_SOUTH + r * COARSE_STEP)
                    val n = snap(PH_SOUTH + (r + 1) * COARSE_STEP)
                    fills += GeoJsonBuilder.polygon(
                        listOf(w to s, e to s, e to n, w to n, w to s),
                        GeoJsonBuilder.props("band" to band.toString()),
                    )
                    start = stop + 1
                }
                c = end + 1
            }
        }
        for (r in 0 until fineRows) {
            var c = 0
            while (c < fineCols) {
                val band = fineBands[r][c]
                if (band < 0) { c++; continue }
                var end = c
                while (end + 1 < fineCols && fineBands[r][end + 1] == band) end++
                var start = c
                while (start <= end) {
                    val stop = minOf(start + MAX_RUN_CELLS - 1, end)
                    val w = snap(PH_WEST + start * FINE_STEP)
                    val e = snap(PH_WEST + (stop + 1) * FINE_STEP)
                    val s = snap(PH_SOUTH + r * FINE_STEP)
                    val n = snap(PH_SOUTH + (r + 1) * FINE_STEP)
                    fills += GeoJsonBuilder.polygon(
                        listOf(w to s, e to s, e to n, w to n, w to s),
                        GeoJsonBuilder.props("band" to band.toString()),
                    )
                    start = stop + 1
                }
                c = end + 1
            }
        }

        // Contours: band-change edges, merged along their run (fine grid only —
        // the coarse deep zone reads as one band at sailing zooms anyway).
        val verticalEdges = HashMap<Long, MutableList<Int>>()
        val horizontalEdges = HashMap<Long, MutableList<Int>>()
        for (r in 0 until fineRows) {
            for (c in 0 until fineCols) {
                val band = fineBands[r][c]
                val right = if (c + 1 < fineCols) fineBands[r][c + 1] else -1
                if (band >= 0 && right >= 0 && band != right) {
                    val deeper = max(band, right)
                    verticalEdges.getOrPut((c + 1L) * 8 + deeper) { ArrayList() }.add(r)
                }
                val bottom = if (r + 1 < fineRows) fineBands[r + 1][c] else -1
                if (band >= 0 && bottom >= 0 && band != bottom) {
                    val deeper = max(band, bottom)
                    horizontalEdges.getOrPut((r + 1L) * 8 + deeper) { ArrayList() }.add(c)
                }
            }
        }
        val contours = ArrayList<String>(4_096)
        contours += mergedSegments(verticalEdges, vertical = true)
        contours += mergedSegments(horizontalEdges, vertical = false)

        // Sparse depth labels on the fine coastal grid.
        val labels = ArrayList<String>(2_048)
        var rowOffset = 0
        var r = 3
        while (r < fineRows) {
            var c = (3 + rowOffset) % 5
            while (c < fineCols) {
                val band = fineBands[r][c]
                if (band in 1..4) {
                    val lon = snap(PH_WEST + (c + 0.5) * FINE_STEP)
                    val lat = snap(PH_SOUTH + (r + 0.5) * FINE_STEP)
                    labels += GeoJsonBuilder.point(
                        lon, lat,
                        GeoJsonBuilder.props("band" to band.toString(), "icon" to GeoJsonBuilder.str("depth-label-$band")),
                    )
                }
                c += 5
            }
            rowOffset++
            r += 5
        }

        return Triple(
            GeoJsonBuilder.collection(fills),
            GeoJsonBuilder.collection(contours),
            GeoJsonBuilder.collection(labels),
        )
    }

    /** Merges consecutive grid edges with the same band into longer polylines (chunked). */
    private fun mergedSegments(
        edges: HashMap<Long, MutableList<Int>>,
        vertical: Boolean,
    ): List<String> {
        val out = ArrayList<String>()
        for ((key, indices) in edges) {
            val band = (key % 8).toInt()
            val lineIndex = (key / 8).toInt()
            val fixed = if (vertical) {
                snap(PH_WEST + lineIndex * FINE_STEP)
            } else {
                snap(PH_SOUTH + lineIndex * FINE_STEP)
            }
            val base = if (vertical) PH_SOUTH else PH_WEST
            indices.sort()
            var i = 0
            while (i < indices.size) {
                var j = i
                while (j + 1 < indices.size && indices[j + 1] == indices[j] + 1) j++
                var start = i
                while (start <= j) {
                    val stop = minOf(start + MAX_RUN_CELLS - 1, j)
                    val a = snap(base + indices[start] * FINE_STEP)
                    val b = snap(base + (indices[stop] + 1) * FINE_STEP)
                    val coords = if (vertical) {
                        listOf(fixed to a, fixed to b)
                    } else {
                        listOf(a to fixed, b to fixed)
                    }
                    out += GeoJsonBuilder.lineString(coords, GeoJsonBuilder.props("band" to band.toString()))
                    start = stop + 1
                }
                i = j + 1
            }
        }
        return out
    }

    /** Grid coordinate snapped to ~1 m to keep the JSON payload compact. */
    private fun snap(value: Double): Double = Math.round(value * 1e5) / 1e5

    /** Cheapest possible distance check: distance to each polygon's bounding box. */
    private fun bboxDistanceDeg(lon: Double, lat: Double, polys: List<CoastPoly>): Double {
        var best = Double.MAX_VALUE
        for (poly in polys) {
            val dx = maxOf(poly.west - lon, 0.0, lon - poly.east)
            val dy = maxOf(poly.south - lat, 0.0, lat - poly.north)
            val d = if (dx == 0.0 && dy == 0.0) 0.0 else sqrt(dx * dx + dy * dy)
            if (d < best) best = d
            if (best == 0.0) break
        }
        return best
    }

    /** Rough shelf profile with gentle variation so contours look organic. */
    private fun depthMeters(distanceKm: Double, lon: Double, lat: Double): Double {
        val wiggle = 1.0 + 0.28 * sin(lon * 53.0 + 1.7) * sin(lat * 41.0 + 0.6)
        val depth = 3.2 * Math.pow(distanceKm, 1.45) * wiggle
        return min(depth, 260.0)
    }

    private fun isLand(lon: Double, lat: Double, polys: List<CoastPoly>): Boolean {
        for (poly in polys) {
            if (lon < poly.west || lon > poly.east || lat < poly.south || lat > poly.north) continue
            for (ring in poly.rings) {
                if (pointInRing(lon, lat, ring)) return true
            }
        }
        return false
    }

    private fun pointInRing(lon: Double, lat: Double, ring: List<DoubleArray>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i][0]; val yi = ring[i][1]
            val xj = ring[j][0]; val yj = ring[j][1]
            val intersects = (yi > lat) != (yj > lat) &&
                lon < (xj - xi) * (lat - yi) / (yj - yi) + xi
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun minDistanceKm(lon: Double, lat: Double, polys: List<CoastPoly>): Double {
        var best = Double.MAX_VALUE
        val pad = 0.55 // degrees, generous search padding
        // Planar approximation (km per degree at this latitude) — accurate to well
        // under a percent at these distances and ~10x faster than per-segment
        // haversine, which matters when classifying ~150k whole-country cells.
        val kmPerDegLat = 111.32
        val kmPerDegLon = 111.32 * cos(Math.toRadians(lat))
        for (poly in polys) {
            if (lon < poly.west - pad || lon > poly.east + pad || lat < poly.south - pad || lat > poly.north + pad) continue
            for (ring in poly.rings) {
                var j = ring.size - 1
                for (i in ring.indices) {
                    val d = pointSegmentDistanceKm(lon, lat, ring[j], ring[i], kmPerDegLat, kmPerDegLon)
                    if (d < best) best = d
                    j = i
                }
            }
        }
        return best
    }

    private fun pointSegmentDistanceKm(
        px: Double, py: Double,
        a: DoubleArray, b: DoubleArray,
        kmPerDegLat: Double, kmPerDegLon: Double,
    ): Double {
        val ax = a[0]; val ay = a[1]; val bx = b[0]; val by = b[1]
        val dx = bx - ax; val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        val ex = (px - (ax + t * dx)) * kmPerDegLon
        val ey = (py - (ay + t * dy)) * kmPerDegLat
        return sqrt(ex * ex + ey * ey)
    }
}
