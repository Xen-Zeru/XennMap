package com.xennmap.domain.model

enum class DistanceUnit(val label: String) {
    KILOMETERS("Kilometers"),
    NAUTICAL_MILES("Nautical Miles"),
}

enum class DepthUnit(val label: String) {
    METERS("Meters"),
    FEET("Feet"),
    FATHOMS("Fathoms"),
}

enum class SpeedUnit(val label: String) {
    KILOMETERS_PER_HOUR("km/h"),
    KNOTS("Knots"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light"),
}

enum class LocationAccuracyMode(val label: String) {
    HIGH("High accuracy"),
    BALANCED("Battery saver"),
}

/** Toggles for optional map layers. */
data class MapLayers(
    val bathymetryEnabled: Boolean = true,
    val contoursEnabled: Boolean = true,
    val depthLabelsEnabled: Boolean = false,
    val savedPlacesVisible: Boolean = true,
)

/** All user preferences, persisted with DataStore. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val depthUnit: DepthUnit = DepthUnit.METERS,
    val speedUnit: SpeedUnit = SpeedUnit.KNOTS,
    val layers: MapLayers = MapLayers(),
    val accuracyMode: LocationAccuracyMode = LocationAccuracyMode.HIGH,
    /** How often a breadcrumb point is written while tracking (seconds). */
    val trackingIntervalSec: Int = 3,
    val tileStyleUrl: String = DEFAULT_STYLE_URL,
    val lastCameraLat: Double = DEFAULT_CAMERA_LAT,
    val lastCameraLng: Double = DEFAULT_CAMERA_LNG,
    val lastCameraZoom: Double = DEFAULT_CAMERA_ZOOM,
) {
    companion object {
        /**
         * Default vector tile style used for online downloads of offline areas.
         * OpenFreeMap serves OpenStreetMap-based vector tiles without an API key.
         * Operators must respect the tile provider's usage policy and OSM licensing (ODbL).
         */
        const val DEFAULT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

        // Cebu / Bantayan area: where the demo bathymetry and seeded places live.
        const val DEFAULT_CAMERA_LAT = 11.19
        const val DEFAULT_CAMERA_LNG = 123.86
        const val DEFAULT_CAMERA_ZOOM = 9.3
    }
}
