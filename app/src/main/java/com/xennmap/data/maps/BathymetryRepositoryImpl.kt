package com.xennmap.data.maps

import android.content.Context
import com.xennmap.domain.model.BathymetryData
import com.xennmap.domain.model.BathymetryDataset
import com.xennmap.domain.repository.BathymetryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the coastline asset and the generated sample bathymetry.
 * Swap [BathymetryRepository] for a real contour dataset (GEBCO/NAMRIA) later —
 * the layer pipeline consumes GeoJSON strings, so only this class changes.
 */
@Singleton
class BathymetryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BathymetryRepository {

    override val dataset: BathymetryDataset = BathymetryDataset(
        id = "sample-ph-demo",
        name = "Philippine demo depth grid (sample)",
        source = "Synthetic demonstration grid generated from the simplified coastline",
        license = "Demo data — not authoritative, not for navigation",
        vintage = "Generated on install",
        coverageNote = "All Philippine waters (coast-derived demo model)",
        isSample = true,
    )

    private val mutex = Mutex()

    @Volatile
    private var cachedData: BathymetryData? = null

    @Volatile
    private var cachedCoastline: String? = null

    @Volatile
    private var cachedPolys: List<SampleBathymetryGenerator.CoastPoly>? = null

    /** Raw simplified coastline GeoJSON (also used as the land layer source). */
    override suspend fun coastlineGeoJson(): String = withContext(Dispatchers.IO) {
        cachedCoastline ?: context.assets.open(COASTLINE_ASSET).bufferedReader().use {
            it.readText()
        }.also { cachedCoastline = it }
    }

    override suspend fun data(): BathymetryData = mutex.withLock {
        cachedData ?: generate().also { cachedData = it }
    }

    override suspend fun depthAt(latitude: Double, longitude: Double): Double? =
        withContext(Dispatchers.Default) {
            SampleBathymetryGenerator.depthAtPosition(latitude, longitude, coastPolys())
        }

    override suspend fun classifyAt(latitude: Double, longitude: Double): com.xennmap.domain.model.TerrainType =
        withContext(Dispatchers.Default) {
            SampleBathymetryGenerator.classifyPosition(latitude, longitude, coastPolys())
        }

    private suspend fun generate(): BathymetryData = withContext(Dispatchers.Default) {
        val polys = coastPolys()
        val (fill, contours, labels) = SampleBathymetryGenerator.generate(polys)
        BathymetryData(
            dataset = dataset,
            fillGeoJson = fill,
            contourGeoJson = contours,
            labelGeoJson = labels,
            bandBreaks = SampleBathymetryGenerator.BAND_BREAKS,
        )
    }

    private suspend fun coastPolys(): List<SampleBathymetryGenerator.CoastPoly> = withContext(Dispatchers.Default) {
        cachedPolys ?: parseCoastline(coastlineGeoJson()).also { cachedPolys = it }
    }

    private fun parseCoastline(json: String): List<SampleBathymetryGenerator.CoastPoly> {
        val result = ArrayList<SampleBathymetryGenerator.CoastPoly>()
        val root = org.json.JSONObject(json)
        val features = root.getJSONArray("features")
        val geometry = features.getJSONObject(0).getJSONObject("geometry")
        val polygons = geometry.getJSONArray("coordinates")
        for (p in 0 until polygons.length()) {
            val ringsJson = polygons.getJSONArray(p)
            val rings = ArrayList<List<DoubleArray>>(ringsJson.length())
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            for (r in 0 until ringsJson.length()) {
                val ringJson = ringsJson.getJSONArray(r)
                val ring = ArrayList<DoubleArray>(ringJson.length())
                for (i in 0 until ringJson.length()) {
                    val pt = ringJson.getJSONArray(i)
                    val lon = pt.getDouble(0)
                    val lat = pt.getDouble(1)
                    ring.add(doubleArrayOf(lon, lat))
                    if (lon < minLon) minLon = lon
                    if (lon > maxLon) maxLon = lon
                    if (lat < minLat) minLat = lat
                    if (lat > maxLat) maxLat = lat
                }
                rings.add(ring)
            }
            if (rings.isNotEmpty()) {
                result.add(
                    SampleBathymetryGenerator.CoastPoly(
                        south = minLat, west = minLon, north = maxLat, east = maxLon, rings = rings,
                    )
                )
            }
        }
        return result
    }

    companion object {
        private const val COASTLINE_ASSET = "geo/coastline_ph.geojson"
    }
}
