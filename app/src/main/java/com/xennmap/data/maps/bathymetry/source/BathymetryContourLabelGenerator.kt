package com.xennmap.data.maps.bathymetry.source

import android.content.Context
import android.util.Log
import com.xennmap.utils.GeoJsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates contour lines and depth labels from real GEBCO binary grid data.
 * Runs during provisioning and stores GeoJSON files for map rendering.
 * Uses the actual GEBCO 15 arc-second grid data.
 * Optimized for memory efficiency - processes tiles individually and writes incrementally.
 */
class BathymetryContourLabelGenerator(private val context: Context) {

    companion object {
        private const val TAG = "XennMap/BathymetryContour"
        private const val RESOLUTION_ARC_SEC = 15
        private const val CELLS_PER_DEG = 3600 / RESOLUTION_ARC_SEC // 240
        private const val NO_DATA_INT16 = -32768

        // Philippine coverage bounds
        private const val PH_SOUTH = 4.0
        private const val PH_WEST = 116.0
        private const val PH_NORTH = 21.5
        private const val PH_EAST = 127.0

        // Depth band breaks matching GEBCO charted depth zones
        val BAND_BREAKS = listOf(5, 10, 20, 50, 100, 200)

        // Contour intervals (matching band breaks) - reduced to major intervals only
        val CONTOUR_INTERVALS = listOf(10.0, 20.0, 50.0, 100.0, 200.0)

        // Label spacing (degrees) - show labels at this grid spacing
        const val LABEL_SPACING_DEG = 0.75 // ~80 km at PH latitudes (reduced density for performance)

        // Max cells per merged feature for performance
        private const val MAX_RUN_CELLS = 32
    }

    /**
     * Generate contour lines and depth labels for the entire Philippines region.
     * Writes GeoJSON files incrementally to avoid memory issues.
     * Returns empty strings to avoid loading large files into memory.
     */
    suspend fun generateContoursAndLabels(): Pair<String, String> = withContext(Dispatchers.IO) {
        val gridDir = File(context.filesDir, "bathymetry/gebco2024_tiles")
        if (!gridDir.exists()) {
            Log.w(TAG, "Grid directory not found, cannot generate contours/labels")
            return@withContext Pair(emptyFC(), emptyFC())
        }

        val contourDir = File(context.filesDir, "bathymetry/contours_labels")
        contourDir.mkdirs()
        val contourFile = File(contourDir, "contours.geojson")
        val labelFile = File(contourDir, "labels.geojson")

        // Generate contours and labels incrementally
        generateContoursIncremental(contourFile)
        generateLabelsIncremental(labelFile)

        // Return empty strings to avoid loading large files into memory
        val contoursSize = if (contourFile.exists()) contourFile.length() else 0
        val labelsSize = if (labelFile.exists()) labelFile.length() else 0
        Log.i(TAG, "Generated contours: $contoursSize bytes, labels: $labelsSize bytes")
        return@withContext Pair("", "")
    }

    /**
     * Generate contour lines incrementally, writing directly to file.
     * Processes one tile at a time to minimize memory usage.
     */
    private fun generateContoursIncremental(outputFile: File) {
        val gridDir = File(context.filesDir, "bathymetry/gebco2024_tiles")
        val gridFiles = gridDir.listFiles()!!.filter { it.name.endsWith(".bin") }.toTypedArray()
        val tileCount = gridFiles.size
        var processed = 0

        BufferedWriter(OutputStreamWriter(FileOutputStream(outputFile))).use { writer ->
            writer.write("""{"type":"FeatureCollection","features":[""")
            var first = true

            for (interval in CONTOUR_INTERVALS) {
                // Process each tile individually for this interval
                val files = File(context.filesDir, "bathymetry/gebco2024_tiles").listFiles()!!.filter { it.name.endsWith(".bin") }.toTypedArray()
                for (file in files) {
                    val tileKey = file.nameWithoutExtension
                    if (!tileKey.startsWith("gebco_")) continue

                    val parts = tileKey.split('_')
                    if (parts.size != 3) continue
                    val latPart = parts[1].removePrefix("N")
                    val lonPart = parts[2].removePrefix("E")
                    val tileLat = latPart.toIntOrNull() ?: continue
                    val tileLon = lonPart.toIntOrNull() ?: continue

                    val bytes = file.readBytes()
                    if (bytes.size != CELLS_PER_DEG * CELLS_PER_DEG * 2) continue

                    // Generate contours for this tile at this interval
                    val tileContours = generateTileContours(bytes, tileLat, tileLon, interval)
                    for (contour in tileContours) {
                        if (!first) writer.write(",")
                        writer.write(contour)
                        first = false
                    }
                }
                processed++
                Log.d(TAG, "Processed interval $interval for $processed/$tileCount tiles")
            }

            writer.write("]}")
        }
    }

    /**
     * Generate contour lines for a single tile at a specific interval.
     * Returns a list of GeoJSON LineString features as strings.
     */
    private fun generateTileContours(bytes: ByteArray, tileLat: Int, tileLon: Int, interval: Double): List<String> {
        val contourList = mutableListOf<String>()

        // Build depth grid for this tile (negative = depth below MSL)
        val tileDepth = Array(CELLS_PER_DEG) { DoubleArray(CELLS_PER_DEG) { Double.NaN } }
        for (y in 0 until CELLS_PER_DEG) {
            for (x in 0 until CELLS_PER_DEG) {
                val index = (y * CELLS_PER_DEG + x) * 2
                val depthInt = bytes[index].toInt() + (bytes[index + 1].toInt() shl 8)
                if (depthInt != NO_DATA_INT16) {
                    tileDepth[y][x] = depthInt.toDouble()
                }
            }
        }

        val verticalEdges = mutableMapOf<Int, MutableList<Int>>()
        val horizontalEdges = mutableMapOf<Int, MutableList<Int>>()

        // Find contour edges within this tile
        for (y in 0 until CELLS_PER_DEG) {
            for (x in 0 until CELLS_PER_DEG) {
                val depth = tileDepth[y][x]
                if (depth.isNaN()) continue

                // Check right neighbor
                if (x + 1 < CELLS_PER_DEG) {
                    val rightDepth = tileDepth[y][x + 1]
                    if (!rightDepth.isNaN() && (depth < interval && rightDepth >= interval || depth >= interval && rightDepth < interval)) {
                        verticalEdges.getOrPut(x + 1) { mutableListOf() }.add(y)
                    }
                }

                // Check bottom neighbor
                if (y + 1 < CELLS_PER_DEG) {
                    val bottomDepth = tileDepth[y + 1][x]
                    if (!bottomDepth.isNaN() && (depth < interval && bottomDepth >= interval || depth >= interval && bottomDepth < interval)) {
                        horizontalEdges.getOrPut(y + 1) { mutableListOf() }.add(x)
                    }
                }
            }
        }

        val contourLines = mutableListOf<String>()

        // Merge vertical edges into line strings
        for ((xIdx, indices) in verticalEdges) {
            val indicesList = indices.toMutableList().apply { sort() }
            contourLines.addAll(mergeVerticalSegmentsTile(indicesList, xIdx, interval, tileLat, tileLon))
        }

        // Merge horizontal edges into line strings
        for ((yIdx, indices) in horizontalEdges) {
            val indicesList = indices.toMutableList().apply { sort() }
            contourLines.addAll(mergeHorizontalSegmentsTile(indicesList, yIdx, interval, tileLat, tileLon))
        }

        return contourLines
    }

    private fun mergeVerticalSegmentsTile(indices: MutableList<Int>, xIdx: Int, interval: Double, tileLat: Int, tileLon: Int): List<String> {
        val lines = mutableListOf<String>()
        var i = 0
        while (i < indices.size) {
            var j = i
            while (j + 1 < indices.size && indices[j + 1] == indices[j] + 1) j++
            var start = i
            while (start <= j) {
                val end = minOf(start + MAX_RUN_CELLS - 1, j)
                val lat1 = tileLat + indices[start] / CELLS_PER_DEG.toDouble()
                val lat2 = tileLat + (indices[end] + 1) / CELLS_PER_DEG.toDouble()
                val lon = tileLon + xIdx / CELLS_PER_DEG.toDouble()
                val coords = listOf(lon to lat1, lon to lat2)
                lines += GeoJsonBuilder.lineString(coords, GeoJsonBuilder.props("interval" to interval.toString()))
                start = end + 1
            }
            i = j + 1
        }
        return lines
    }

    private fun mergeHorizontalSegmentsTile(indices: MutableList<Int>, yIdx: Int, interval: Double, tileLat: Int, tileLon: Int): List<String> {
        val lines = mutableListOf<String>()
        var i = 0
        while (i < indices.size) {
            var j = i
            while (j + 1 < indices.size && indices[j + 1] == indices[j] + 1) j++
            var start = i
            while (start <= j) {
                val end = minOf(start + MAX_RUN_CELLS - 1, j)
                val lon1 = tileLon + indices[start] / CELLS_PER_DEG.toDouble()
                val lon2 = tileLon + (indices[end] + 1) / CELLS_PER_DEG.toDouble()
                val lat = tileLat + yIdx / CELLS_PER_DEG.toDouble()
                val coords = listOf(lon1 to lat, lon2 to lat)
                lines += GeoJsonBuilder.lineString(coords, GeoJsonBuilder.props("interval" to interval.toString()))
                start = end + 1
            }
            i = j + 1
        }
        return lines
    }

    /**
     * Generate depth labels incrementally.
     */
    private fun generateLabelsIncremental(outputFile: File) {
        val gridDir = File(context.filesDir, "bathymetry/gebco2024_tiles")
        val files = gridDir.listFiles()!!.filter { it.name.endsWith(".bin") }.toTypedArray()

        BufferedWriter(OutputStreamWriter(FileOutputStream(outputFile))).use { writer ->
            writer.write("""{"type":"FeatureCollection","features":[""")
            var first = true

            for (file in files) {
                val tileKey = file.nameWithoutExtension
                if (!tileKey.startsWith("gebco_")) continue

                val parts = tileKey.split('_')
                if (parts.size != 3) continue
                val latPart = parts[1].removePrefix("N")
                val lonPart = parts[2].removePrefix("E")
                val tileLat = latPart.toIntOrNull() ?: continue
                val tileLon = lonPart.toIntOrNull() ?: continue

                val bytes = file.readBytes()
                if (bytes.size != CELLS_PER_DEG * CELLS_PER_DEG * 2) continue

                val tileLabels = generateTileLabels(bytes, tileLat, tileLon)
                for (label in tileLabels) {
                    if (!first) writer.write(",")
                    writer.write(label)
                    first = false
                }
            }

            writer.write("]}")
        }
    }

    private fun generateTileLabels(bytes: ByteArray, tileLat: Int, tileLon: Int): List<String> {
        val labels = mutableListOf<String>()

        // Build depth grid for this tile
        val tileDepth = Array(CELLS_PER_DEG) { DoubleArray(CELLS_PER_DEG) { Double.NaN } }
        for (y in 0 until CELLS_PER_DEG) {
            for (x in 0 until CELLS_PER_DEG) {
                val index = (y * CELLS_PER_DEG + x) * 2
                val depthInt = bytes[index].toInt() + (bytes[index + 1].toInt() shl 8)
                if (depthInt != NO_DATA_INT16) {
                    tileDepth[y][x] = depthInt.toDouble()
                }
            }
        }

        val labelSpacingCells = (LABEL_SPACING_DEG * CELLS_PER_DEG).roundToInt()

        for (y in 0 until CELLS_PER_DEG step labelSpacingCells) {
            for (x in 0 until CELLS_PER_DEG step labelSpacingCells) {
                val depth = tileDepth[y][x]
                if (depth.isNaN() || depth >= 0) continue // Skip land/NoData

                val band = bandIndex(depth)
                if (band in 1..4) { // Only label intermediate depths
                    val lon = tileLon + (x + 0.5) / CELLS_PER_DEG.toDouble()
                    val lat = tileLat + (y + 0.5) / CELLS_PER_DEG.toDouble()
                    labels += GeoJsonBuilder.point(
                        lon, lat,
                        GeoJsonBuilder.props("band" to band.toString(), "depth" to depth.toInt().toString())
                    )
                }
            }
        }

        return labels
    }

    private fun bandIndex(depthMeters: Double): Int {
        for ((index, breakM) in BAND_BREAKS.withIndex()) {
            if (depthMeters < breakM) return index
        }
        return BAND_BREAKS.size
    }

    private fun emptyFC(): String = """{"type":"FeatureCollection","features":[]}"""

    /**
     * Save generated contours and labels to files for quick loading.
     */
    suspend fun saveContoursAndLabels(contoursJson: String, labelsJson: String) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "bathymetry/contours_labels")
        dir.mkdirs()
        File(dir, "contours.geojson").writeText(contoursJson)
        File(dir, "labels.geojson").writeText(labelsJson)
        Log.i(TAG, "Saved contours and labels to ${dir.absolutePath}")
    }

    /**
     * Load pre-generated contours and labels from files.
     */
    suspend fun loadContoursAndLabels(): Pair<String, String> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "bathymetry/contours_labels")
        val contoursFile = File(dir, "contours.geojson")
        val labelsFile = File(dir, "labels.geojson")
        if (contoursFile.exists() && labelsFile.exists()) {
            return@withContext Pair(contoursFile.readText(), labelsFile.readText())
        }
        return@withContext Pair(emptyFC(), emptyFC())
    }
}