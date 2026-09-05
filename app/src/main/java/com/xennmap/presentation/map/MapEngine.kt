package com.xennmap.presentation.map

import android.content.Context
import android.graphics.PointF
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max

/** Camera snapshot used for the scale bar, compass and persistence. */
data class MapCamera(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val zoom: Double = 0.0,
    val bearing: Double = 0.0,
)

/**
 * Compose-facing wrapper around a MapLibre MapView.
 * Owns the lifecycle-dependent pieces and delegates rendering updates to [MapRenderer].
 */
class MapEngine(
    context: Context,
    private val onCameraChanged: (MapCamera) -> Unit,
) {

    val mapView: MapView = MapView(context)
    val renderer = MapRenderer(context)

    private var mapLibre: MapLibreMap? = null
    private var currentDark: Boolean? = null
    private var pendingCamera: MapCamera? = null
    private var bathyStyleRefreshed = false

    /** Set by the screen: resolves a tap at screen coordinates to a place id (or null). */
    var placeTapResolver: ((x: Float, y: Float) -> Long?)? = null

    var onPlaceTapped: ((placeId: Long) -> Unit)? = null
    var onMapTapped: ((latitude: Double, longitude: Double) -> Unit)? = null
    var onMapLongTapped: ((latitude: Double, longitude: Double) -> Unit)? = null

    init {
        mapView.onCreate(null)
        mapView.getMapAsync { map ->
            mapLibre = map
            map.uiSettings.isLogoEnabled = false
            // When the depth grid finishes generating (it arrives after the style
            // loaded), reload the style once so every zoom level tiles with data.
            renderer.onBathymetryFirstApplied = {
                if (!bathyStyleRefreshed) {
                    bathyStyleRefreshed = true
                    loadStyle(currentDark ?: true)
                }
            }
            // Jump straight to the saved camera before the style loads so the
            // map never shows a world-view flash on open.
            pendingCamera?.let { camera ->
                map.setCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(camera.latitude, camera.longitude))
                        .zoom(camera.zoom)
                        .bearing(camera.bearing)
                        .build()
                )
                pendingCamera = null
            }
            map.addOnCameraIdleListener {
                val position = map.cameraPosition
                val target = position.target
                onCameraChanged(
                    MapCamera(
                        latitude = target?.latitude ?: 0.0,
                        longitude = target?.longitude ?: 0.0,
                        zoom = position.zoom,
                        bearing = position.bearing,
                    )
                )
            }
            map.addOnMapClickListener { point ->
                val screen = map.projection.toScreenLocation(point)
                val placeId = placeTapResolver?.invoke(screen.x, screen.y)
                if (placeId != null) {
                    onPlaceTapped?.invoke(placeId)
                    true
                } else {
                    onMapTapped?.invoke(point.latitude, point.longitude)
                    false
                }
            }
            map.addOnMapLongClickListener { point ->
                onMapLongTapped?.invoke(point.latitude, point.longitude)
                true
            }
            // Load with the theme resolved so far; setDark() drives any reload
            // afterwards. Exactly ONE load starts here — a second racing this
            // one invalidates it mid-flight and breaks rendering.
            loadStyle(currentDark ?: true)
        }
    }

    /** Switches the whole style (dark ocean / light nautical). */
    fun setDark(dark: Boolean) {
        if (currentDark == dark) return
        currentDark = dark
        loadStyle(dark)
    }

    private fun loadStyle(dark: Boolean) {
        // The currently loaded style becomes invalid as soon as a newer load
        // starts — drop it so pending render updates never touch it.
        renderer.invalidateStyle()
        mapLibre?.setStyle(
            Style.Builder().fromJson(baseStyleJson(dark)),
            object : Style.OnStyleLoaded {
                override fun onStyleLoaded(style: Style) {
                    renderer.onStyleLoaded(style, dark)
                }
            },
        )
    }

    fun flyTo(latitude: Double, longitude: Double, zoom: Double? = null) {
        val map = mapLibre ?: run {
            pendingCamera = MapCamera(latitude, longitude, zoom ?: 12.0, 0.0)
            return
        }
        val currentZoom = map.cameraPosition?.zoom ?: 12.0
        val builder = CameraPosition.Builder().target(LatLng(latitude, longitude))
        builder.zoom(zoom ?: currentZoom)
        map.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()))
    }

    /** Zooms so the given bounding box fits the viewport. */
    fun fitBounds(south: Double, west: Double, north: Double, east: Double) {
        val map = mapLibre ?: run {
            pendingCamera = MapCamera((north + south) / 2.0, (east + west) / 2.0, 10.0, 0.0)
            return
        }
        val w = mapView.width.takeIf { it > 0 }
            ?: mapView.resources.displayMetrics.widthPixels
        val h = mapView.height.takeIf { it > 0 }
            ?: mapView.resources.displayMetrics.heightPixels
        val latSpan = max(north - south, 0.005)
        val lonSpan = max(east - west, 0.005)
        val centerLat = (north + south) / 2.0
        val mppLat = latSpan * 111_320.0 / h
        val mppLon = lonSpan * 111_320.0 * cos(Math.toRadians(centerLat)) / w
        val requiredMpp = max(mppLat, mppLon).coerceAtLeast(1e-6)
        val zoom = (log2(156_543.03392 * cos(Math.toRadians(centerLat)) / requiredMpp) - 0.4)
            .coerceIn(3.0, 16.0)
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(centerLat, (east + west) / 2.0))
                    .zoom(zoom)
                    .build()
            )
        )
    }

    fun zoomIn() {
        mapLibre?.animateCamera(CameraUpdateFactory.zoomIn())
    }

    fun zoomOut() {
        mapLibre?.animateCamera(CameraUpdateFactory.zoomOut())
    }

    fun resetNorth() {
        val map = mapLibre ?: return
        val target = map.cameraPosition.target ?: return
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).bearing(0.0).build()
            )
        )
    }

    fun screenFor(latitude: Double, longitude: Double): PointF? {
        val map = mapLibre ?: return null
        return map.projection.toScreenLocation(LatLng(latitude, longitude))
    }

    fun onDestroy() {
        mapView.onDestroy()
    }

    companion object {
        fun baseStyleJson(dark: Boolean): String {
            val background = if (dark) "#06131D" else "#A9D2E8"
            return """{"version":8,"name":"XennMap","sources":{},"layers":[""" +
                """{"id":"background","type":"background","paint":{"background-color":"$background"}}]}"""
        }
    }
}
