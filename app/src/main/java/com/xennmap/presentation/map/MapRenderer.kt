package com.xennmap.presentation.map

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.xennmap.data.maps.bathymetry.source.BathymetryMbtilesProvider
import com.xennmap.domain.model.GpsFix
import com.xennmap.domain.model.MapLayers
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.domain.model.SavedPlace
import com.xennmap.domain.model.TrackPoint
import com.xennmap.ui.theme.Primary
import com.xennmap.ui.theme.Secondary
import com.xennmap.ui.theme.Warning
import com.xennmap.utils.GeoJsonBuilder
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Outline of a download area being previewed from the Offline screen. */
data class PresetOutline(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val name: String,
)

/** Everything the renderer needs for one frame of map data. */
data class MapRenderState(
    val dark: Boolean,
    val coastlineJson: String?,
    val layers: MapLayers,
    val places: List<SavedPlace>,
    val selectedPlaceId: Long?,
    val fix: GpsFix?,
    val trackPoints: List<TrackPoint>,
    val navDestination: SavedPlace?,
    val navActive: Boolean,
    /** Coordinate the user picked by tapping/long-pressing the map (lat to lng). */
    val selectedPoint: Pair<Double, Double>? = null,
    val presetOutline: PresetOutline? = null,
    /** ID of the downloaded bathymetry region for real depth rendering */
    val bathymetryRegionId: String? = null,
)

/**
 * Installs and updates all runtime style layers of the XennMap ocean style:
 * land, bathymetry raster overlay, saved places, track, navigation line
 * and the heading-aware location marker. All data flows through GeoJSON string
 * updates so nothing needs an internet connection after startup.
 */
class MapRenderer(private val context: Context) {

    private var style: org.maplibre.android.maps.Style? = null
    private var dark: Boolean = true
    private var latest: MapRenderState? = null

    /** Fired once when the depth grid first reaches the renderer. */
    var onBathymetryFirstApplied: (() -> Unit)? = null

    fun onStyleLoaded(style: org.maplibre.android.maps.Style, dark: Boolean) {
        this.style = style
        this.dark = dark
        install(style, dark)
        latest?.let { apply(it) }
    }

    /** Called before a style (re)load starts: the old style must not be touched. */
    fun invalidateStyle() {
        style = null
    }

    fun apply(state: MapRenderState) {
        latest = state
        val style = style ?: return

        // Add real bathymetry RasterSource if region is available
        if (state.bathymetryRegionId != null) {
            addRealBathymetrySource(style, state.bathymetryRegionId!!)
        }

        runCatching {
            updateSources(style, state)
            updateVisibility(style, state)
        }.onFailure { android.util.Log.e("XennMap", "map apply failed", it) }
    }

    // ------------------------------------------------------------------ setup

    private fun install(style: org.maplibre.android.maps.Style, dark: Boolean) {
        addImages(style, dark)
        emptySources(style)
        addLayers(style, dark)
    }

    private fun addImages(style: org.maplibre.android.maps.Style, dark: Boolean) {
        style.addImage(ICON_ARROW, MarkerBitmapFactory.headingArrow(context))
        style.addImage(ICON_CROSSHAIR, MarkerBitmapFactory.crosshair(context))
        PlaceCategory.entries.forEach { category ->
            style.addImage(iconNameFor(category), MarkerBitmapFactory.placeIcon(context, category))
        }
    }

    private fun emptySources(style: org.maplibre.android.maps.Style) {
        listOf(
            SRC_LAND, SRC_PLACES, SRC_PLACE_SELECTED, SRC_TRACK, SRC_TRACK_POINTS,
            SRC_NAV, SRC_ACCURACY, SRC_LOCATION, SRC_SELECTED, SRC_PRESET,
        ).forEach { id ->
            runCatching { style.addSource(GeoJsonSource(id, EMPTY_COLLECTION)) }
        }
    }

    private fun addLayers(style: org.maplibre.android.maps.Style, dark: Boolean) {
        val primary = Primary.toArgb()
        val secondary = Secondary.toArgb()
        val warning = Warning.toArgb()
        val white = android.graphics.Color.WHITE

        // Land layer (coastline from GeoJSON)
        style.addLayer(
            FillLayer(L_LAND, SRC_LAND).apply {
                setProperties(
                    PropertyFactory.fillColor(if (dark) 0xFF0A1D2E.toInt() else 0xFFE8F4FD.toInt()),
                    PropertyFactory.fillOpacity(1f),
                )
            }
        )

        // Download-area preview outline (amber, high visibility over any theme).
        style.addLayer(
            FillLayer(L_PRESET_FILL, SRC_PRESET).apply {
                setProperties(
                    PropertyFactory.fillColor(Warning.toArgb()),
                    PropertyFactory.fillOpacity(0.12f),
                )
            }
        )
        style.addLayer(
            LineLayer(L_PRESET_LINE, SRC_PRESET).apply {
                setProperties(
                    PropertyFactory.lineColor(Warning.toArgb()),
                    PropertyFactory.lineWidth(2.5f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
                )
            }
        )
        style.addLayer(
            LineLayer(L_TRACK, SRC_TRACK).apply {
                setProperties(
                    PropertyFactory.lineColor(secondary),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineOpacity(0.9f),
                )
            }
        )
        style.addLayer(
            CircleLayer(L_TRACK_POINTS, SRC_TRACK_POINTS).apply {
                setProperties(
                    PropertyFactory.circleRadius(2.2f),
                    PropertyFactory.circleColor(white),
                    PropertyFactory.circleOpacity(0.55f),
                )
            }
        )
        style.addLayer(
            LineLayer(L_NAV, SRC_NAV).apply {
                setProperties(
                    PropertyFactory.lineColor(warning),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineDasharray(arrayOf(2.2f, 1.6f)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineOpacity(0.95f),
                )
            }
        )
        style.addLayer(
            SymbolLayer(L_SELECTED, SRC_SELECTED).apply {
                setProperties(
                    PropertyFactory.iconImage(ICON_CROSSHAIR),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(1f),
                )
            }
        )
        style.addLayer(
            SymbolLayer(L_PLACES, SRC_PLACES).apply {
                setProperties(
                    PropertyFactory.iconImage(Expression.raw("[\"get\", \"icon\"]")),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(1f),
                )
            }
        )
        style.addLayer(
            CircleLayer(L_PLACE_SELECTED, SRC_PLACE_SELECTED).apply {
                setProperties(
                    PropertyFactory.circleRadius(15f),
                    PropertyFactory.circleColor(android.graphics.Color.TRANSPARENT),
                    PropertyFactory.circleStrokeColor(white),
                    PropertyFactory.circleStrokeWidth(2.4f),
                )
            }
        )
        style.addLayer(
            FillLayer(L_ACCURACY, SRC_ACCURACY).apply {
                setProperties(
                    PropertyFactory.fillColor(Primary.toArgb()),
                    PropertyFactory.fillOpacity(0.16f),
                )
            }
        )
        style.addLayer(
            CircleLayer(L_LOC_DOT, SRC_LOCATION).apply {
                setProperties(
                    PropertyFactory.circleRadius(5.5f),
                    PropertyFactory.circleColor(Primary.toArgb()),
                    PropertyFactory.circleStrokeColor(white),
                    PropertyFactory.circleStrokeWidth(2f),
                )
            }
        )
        style.addLayer(
            SymbolLayer(L_LOC_ARROW, SRC_LOCATION).apply {
                setProperties(
                    PropertyFactory.iconImage(ICON_ARROW),
                    PropertyFactory.iconRotate(Expression.raw("[\"get\", \"heading\"]")),
                    PropertyFactory.iconSize(1.05f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                )
                setFilter(Expression.has("heading"))
            }
        )
    }

    // ------------------------------------------------------------------ raster bathymetry

    /** Add real bathymetry RasterSource from downloaded MBTiles. */
    private fun addRealBathymetrySource(style: org.maplibre.android.maps.Style, regionId: String) {
        val path = File(context.filesDir, "bathymetry/mbtiles/$regionId.mbtiles")
        if (!path.exists()) {
            android.util.Log.w("XennMap", "GEBCO MBTiles not found at: ${path.absolutePath}")
            return
        }

        // Check if source already exists and is valid
        val existingSource = style.getSource(SRC_BATHY_RASTER)
        if (existingSource != null) {
            android.util.Log.i("XennMap", "GEBCO RasterSource already exists, skipping")
            return
        }

        val uri = "mbtiles://${path.absolutePath}"
        android.util.Log.i("XennMap", "Adding GEBCO RasterSource: $uri")
        val rasterSource = RasterSource(SRC_BATHY_RASTER, uri, 256)
        style.addSource(rasterSource)

        // Add RasterLayer with baked bathymetry colors (no color ramp needed - colors are baked into RGBA tiles)
        if (style.getLayer(L_BATHY_RASTER) == null) {
            val rasterLayer = RasterLayer(L_BATHY_RASTER, SRC_BATHY_RASTER).apply {
                setProperties(
                    PropertyFactory.rasterOpacity(0.55f),
                )
                minZoom = 0f
                maxZoom = 13f
            }
            style.addLayer(rasterLayer)
            android.util.Log.i("XennMap", "GEBCO RasterLayer added successfully")
        }
    }

    // ----------------------------------------------------------------- update

    private fun updateSources(style: org.maplibre.android.maps.Style, state: MapRenderState) {
        setGeoJson(style, SRC_LAND, state.coastlineJson)
        setGeoJson(style, SRC_PLACES, placesCollection(state.places))
        // The ringed indicator sits on the tap-selected place, or on the active
        // navigation destination when a steering session is running.
        val ringedPoint: Pair<Double, Double>? = when {
            state.navActive && state.navDestination != null ->
                state.navDestination.latitude to state.navDestination.longitude
            state.selectedPlaceId != null ->
                state.places.firstOrNull { it.id == state.selectedPlaceId }?.let { it.latitude to it.longitude }
            else -> null
        }
        setGeoJson(
            style, SRC_PLACE_SELECTED,
            ringedPoint?.let { (lat, lng) ->
                GeoJsonBuilder.collection(
                    listOf(GeoJsonBuilder.point(lng, lat, GeoJsonBuilder.props()))
                )
            },
        )
        setGeoJson(style, SRC_TRACK, trackLine(state.trackPoints))
        setGeoJson(style, SRC_TRACK_POINTS, trackPoints(state.trackPoints))
        setGeoJson(style, SRC_NAV, navLine(state))
        setGeoJson(style, SRC_ACCURACY, accuracyPolygon(state.fix))
        setGeoJson(style, SRC_LOCATION, locationPoint(state.fix))
        setGeoJson(
            style, SRC_SELECTED,
            state.selectedPoint?.let { (lat, lng) ->
                GeoJsonBuilder.collection(
                    listOf(GeoJsonBuilder.point(lng, lat, GeoJsonBuilder.props()))
                )
            },
        )
        setGeoJson(
            style, SRC_PRESET,
            state.presetOutline?.let { o ->
                GeoJsonBuilder.collection(
                    listOf(
                        GeoJsonBuilder.polygon(
                            listOf(
                                o.west to o.south, o.east to o.south,
                                o.east to o.north, o.west to o.north, o.west to o.south,
                            ),
                            GeoJsonBuilder.props(),
                        )
                    )
                )
            },
        )
    }

    private fun updateVisibility(style: org.maplibre.android.maps.Style, state: MapRenderState) {
        // Bathymetry raster visibility
        val hasRealBathy = state.bathymetryRegionId != null
        val bathyEnabled = state.layers.bathymetryEnabled

        setVisible(style, L_BATHY_RASTER, bathyEnabled && hasRealBathy)
        setVisible(style, L_LAND, true)
        setVisible(style, L_PLACES, state.layers.savedPlacesVisible)
        setVisible(style, L_PLACE_SELECTED, state.selectedPlaceId != null || state.navActive)
        setVisible(style, L_TRACK, state.trackPoints.isNotEmpty())
        setVisible(style, L_TRACK_POINTS, state.trackPoints.size > 1)
        setVisible(style, L_NAV, state.navActive && state.navDestination != null)
        setVisible(style, L_SELECTED, state.selectedPoint != null)
        setVisible(style, L_PRESET_FILL, state.presetOutline != null)
        setVisible(style, L_PRESET_LINE, state.presetOutline != null)
    }

    private fun setGeoJson(style: org.maplibre.android.maps.Style, sourceId: String, json: String?) {
        val source = style.getSource(sourceId) as? GeoJsonSource
        if (source == null) {
            android.util.Log.w("XennMap", "setGeoJson: source $sourceId missing from style")
            return
        }
        runCatching { source.setGeoJson(json ?: EMPTY_COLLECTION) }
            .onFailure { android.util.Log.e("XennMap", "setGeoJson $sourceId failed", it) }
    }

    private fun setVisible(style: org.maplibre.android.maps.Style, layerId: String, visible: Boolean) {
        val layer = style.getLayer(layerId) ?: return
        layer.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
        )
    }

    // ----------------------------------------------------------- geo builders

    private fun placesCollection(places: List<SavedPlace>): String =
        GeoJsonBuilder.collection(
            places.map { place ->
                GeoJsonBuilder.point(
                    place.longitude,
                    place.latitude,
                    GeoJsonBuilder.props(
                        "pid" to place.id.toString(),
                        "icon" to GeoJsonBuilder.str(iconNameFor(place.category)),
                    ),
                )
            }
        )

    private fun trackLine(points: List<TrackPoint>): String =
        if (points.size < 2) {
            EMPTY_COLLECTION
        } else {
            GeoJsonBuilder.collection(
                listOf(
                    GeoJsonBuilder.lineString(
                        points.map { it.longitude to it.latitude },
                        GeoJsonBuilder.props(),
                    )
                )
            )
        }

    private fun trackPoints(points: List<TrackPoint>): String =
        GeoJsonBuilder.collection(
            points.map { point ->
                GeoJsonBuilder.point(
                    point.longitude,
                    point.latitude,
                    GeoJsonBuilder.props(),
                )
            }
        )

    private fun navLine(state: MapRenderState): String {
        val fix = state.fix ?: return EMPTY_COLLECTION
        val destination = state.navDestination ?: return EMPTY_COLLECTION
        if (!state.navActive) return EMPTY_COLLECTION
        return GeoJsonBuilder.collection(
            listOf(
                GeoJsonBuilder.lineString(
                    listOf(fix.longitude to fix.latitude, destination.longitude to destination.latitude),
                    GeoJsonBuilder.props(),
                )
            )
        )
    }

    private fun locationPoint(fix: GpsFix?): String {
        fix ?: return EMPTY_COLLECTION
        val props = if (fix.bearingDeg != null && fix.speedMps >= 0.4f) {
            GeoJsonBuilder.props("heading" to GeoJsonBuilder.num(fix.bearingDeg.toDouble()))
        } else {
            GeoJsonBuilder.props()
        }
        return GeoJsonBuilder.collection(listOf(GeoJsonBuilder.point(fix.longitude, fix.latitude, props)))
    }

    private fun accuracyPolygon(fix: GpsFix?): String {
        fix ?: return EMPTY_COLLECTION
        val radiusMeters = fix.accuracyMeters.coerceAtLeast(3f).toDouble()
        val latDelta = radiusMeters / 111_320.0
        val lonDelta = radiusMeters / (111_320.0 * cos(Math.toRadians(fix.latitude)).coerceAtLeast(0.05))
        val ring = ArrayList<Pair<Double, Double>>(65)
        for (i in 0 until ACCURACY_SEGMENTS) {
            val angle = 2 * Math.PI * i / ACCURACY_SEGMENTS
            ring.add(
                fix.longitude + lonDelta * cos(angle) to fix.latitude + latDelta * sin(angle)
            )
        }
        ring.add(ring.first())
        return GeoJsonBuilder.collection(
            listOf(GeoJsonBuilder.polygon(ring, GeoJsonBuilder.props()))
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun iconNameFor(category: PlaceCategory): String = "pin-" + category.name.lowercase()

    private fun bandLabel(band: Int): String {
        val breaks = listOf(5, 10, 20, 50, 100, 200)
        return when {
            band == 0 -> "0–5 m"
            band < breaks.size -> "${breaks[band - 1]}–${breaks[band]} m"
            else -> "200 m+"
        }
    }

    private fun hex(color: androidx.compose.ui.graphics.Color): String {
        val argb = color.toArgb()
        return String.format("#%06X", argb and 0xFFFFFF)
    }

    companion object {
        const val SRC_LAND = "xenn-land"
        const val SRC_PLACES = "xenn-places"
        const val SRC_PLACE_SELECTED = "xenn-place-selected"
        const val SRC_TRACK = "xenn-track"
        const val SRC_TRACK_POINTS = "xenn-track-points"
        const val SRC_NAV = "xenn-nav"
        const val SRC_ACCURACY = "xenn-accuracy"
        const val SRC_LOCATION = "xenn-location"
        const val SRC_SELECTED = "xenn-selected"
        const val SRC_PRESET = "xenn-preset"
        const val SRC_BATHY_RASTER = "xenn-bathy-raster"

        const val L_LAND = "xenn-l-land"
        const val L_BATHY_RASTER = "xenn-l-bathy-raster"
        const val L_PRESET_FILL = "xenn-l-preset-fill"
        const val L_PRESET_LINE = "xenn-l-preset-line"
        const val L_TRACK = "xenn-l-track"
        const val L_TRACK_POINTS = "xenn-l-track-points"
        const val L_NAV = "xenn-l-nav"
        const val L_PLACES = "xenn-l-places"
        const val L_PLACE_SELECTED = "xenn-l-place-selected"
        const val L_ACCURACY = "xenn-l-accuracy"
        const val L_LOC_DOT = "xenn-l-loc-dot"
        const val L_LOC_ARROW = "xenn-l-loc-arrow"
        const val L_SELECTED = "xenn-l-selected"
        const val L_PRESET = "xenn-l-preset"

        const val ICON_ARROW = "xenn-arrow"
        const val ICON_CROSSHAIR = "xenn-crosshair"

        const val EMPTY_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
        private const val ACCURACY_SEGMENTS = 48
    }
}