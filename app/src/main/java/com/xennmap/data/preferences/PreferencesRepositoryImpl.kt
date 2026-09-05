package com.xennmap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xennmap.di.ApplicationScope
import com.xennmap.domain.model.AppSettings
import com.xennmap.domain.model.DepthUnit
import com.xennmap.domain.model.DistanceUnit
import com.xennmap.domain.model.LocationAccuracyMode
import com.xennmap.domain.model.MapLayers
import com.xennmap.domain.model.SpeedUnit
import com.xennmap.domain.model.ThemeMode
import com.xennmap.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.xennDataStore by preferencesDataStore(name = "xenn_settings")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : PreferencesRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val DEPTH_UNIT = stringPreferencesKey("depth_unit")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val ACCURACY_MODE = stringPreferencesKey("accuracy_mode")
        val TRACKING_INTERVAL = intPreferencesKey("tracking_interval_sec")
        val TILE_STYLE_URL = stringPreferencesKey("tile_style_url")
        val BATHYMETRY = booleanPreferencesKey("layer_bathymetry")
        val CONTOURS = booleanPreferencesKey("layer_contours")
        val DEPTH_LABELS = booleanPreferencesKey("layer_depth_labels")
        val SAVED_PLACES = booleanPreferencesKey("layer_saved_places")
        val CAM_LAT = doublePreferencesKey("cam_lat")
        val CAM_LNG = doublePreferencesKey("cam_lng")
        val CAM_ZOOM = doublePreferencesKey("cam_zoom")
    }

    private val flow: Flow<AppSettings> = context.xennDataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            distanceUnit = p[Keys.DISTANCE_UNIT]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() }
                ?: DistanceUnit.KILOMETERS,
            depthUnit = p[Keys.DEPTH_UNIT]?.let { runCatching { DepthUnit.valueOf(it) }.getOrNull() }
                ?: DepthUnit.METERS,
            speedUnit = p[Keys.SPEED_UNIT]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
                ?: SpeedUnit.KNOTS,
            layers = MapLayers(
                bathymetryEnabled = p[Keys.BATHYMETRY] ?: true,
                contoursEnabled = p[Keys.CONTOURS] ?: true,
                depthLabelsEnabled = p[Keys.DEPTH_LABELS] ?: false,
                savedPlacesVisible = p[Keys.SAVED_PLACES] ?: true,
            ),
            accuracyMode = p[Keys.ACCURACY_MODE]?.let { runCatching { LocationAccuracyMode.valueOf(it) }.getOrNull() }
                ?: LocationAccuracyMode.HIGH,
            trackingIntervalSec = p[Keys.TRACKING_INTERVAL] ?: 3,
            tileStyleUrl = p[Keys.TILE_STYLE_URL] ?: AppSettings.DEFAULT_STYLE_URL,
            lastCameraLat = p[Keys.CAM_LAT] ?: AppSettings.DEFAULT_CAMERA_LAT,
            lastCameraLng = p[Keys.CAM_LNG] ?: AppSettings.DEFAULT_CAMERA_LNG,
            lastCameraZoom = p[Keys.CAM_ZOOM] ?: AppSettings.DEFAULT_CAMERA_ZOOM,
        )
    }

    override val settings: Flow<AppSettings> = flow

    override val settingsState: StateFlow<AppSettings> =
        flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    override suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    override suspend fun setDistanceUnit(unit: DistanceUnit) = edit { it[Keys.DISTANCE_UNIT] = unit.name }

    override suspend fun setDepthUnit(unit: DepthUnit) = edit { it[Keys.DEPTH_UNIT] = unit.name }

    override suspend fun setSpeedUnit(unit: SpeedUnit) = edit { it[Keys.SPEED_UNIT] = unit.name }

    override suspend fun setAccuracyMode(mode: LocationAccuracyMode) = edit { it[Keys.ACCURACY_MODE] = mode.name }

    override suspend fun setTrackingInterval(seconds: Int) = edit { it[Keys.TRACKING_INTERVAL] = seconds }

    override suspend fun setTileStyleUrl(url: String) = edit { it[Keys.TILE_STYLE_URL] = url }

    override suspend fun setLayers(layers: MapLayers) = edit {
        it[Keys.BATHYMETRY] = layers.bathymetryEnabled
        it[Keys.CONTOURS] = layers.contoursEnabled
        it[Keys.DEPTH_LABELS] = layers.depthLabelsEnabled
        it[Keys.SAVED_PLACES] = layers.savedPlacesVisible
    }

    override suspend fun saveCamera(lat: Double, lng: Double, zoom: Double) = edit {
        it[Keys.CAM_LAT] = lat
        it[Keys.CAM_LNG] = lng
        it[Keys.CAM_ZOOM] = zoom
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.xennDataStore.edit(block)
    }
}
