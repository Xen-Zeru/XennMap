package com.xennmap.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xennmap.data.connectivity.ConnectivityObserver
import com.xennmap.data.navigation.NavigationSession
import com.xennmap.data.tracking.TrackingController
import com.xennmap.domain.model.AppSettings
import com.xennmap.domain.model.BathymetryData
import com.xennmap.domain.model.GpsState
import com.xennmap.domain.model.MapLayers
import com.xennmap.domain.model.NavigationPlan
import com.xennmap.domain.model.NavigationSessionState
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.domain.model.SavedPlace
import com.xennmap.domain.model.TerrainType
import com.xennmap.domain.model.ChartDepth
import com.xennmap.domain.model.OfflineRegion
import com.xennmap.domain.model.RegionPreset
import com.xennmap.domain.model.TrackPoint
import com.xennmap.domain.repository.BathymetryRepository
import com.xennmap.domain.repository.LocationRepository
import com.xennmap.domain.repository.PreferencesRepository
import com.xennmap.domain.repository.SavedPlaceRepository
import com.xennmap.domain.repository.TrackRepository
import com.xennmap.domain.usecase.GetDepthAtPositionUseCase
import com.xennmap.domain.usecase.PlanNavigationUseCase
import com.xennmap.domain.repository.OfflineRegionRepository
import com.xennmap.presentation.common.AreaPickerSession
import com.xennmap.presentation.common.AreaPickerState
import com.xennmap.presentation.common.DownloadBounds
import com.xennmap.presentation.common.MapCommand
import com.xennmap.presentation.common.MapCommandBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.xennmap.utils.GeoUtils
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val savedPlaceRepository: SavedPlaceRepository,
    private val trackRepository: TrackRepository,
    private val locationRepository: LocationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val bathymetryRepository: BathymetryRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val navigationSession: NavigationSession,
    private val trackingController: TrackingController,
    val commandBus: MapCommandBus,
    private val planNavigation: PlanNavigationUseCase,
    private val getDepthAtPosition: GetDepthAtPositionUseCase,
    private val offlineRegionRepository: OfflineRegionRepository,
    private val areaPickerSession: AreaPickerSession,
) : ViewModel() {

    /** Framing session for the custom download area (drives the map overlay). */
    val areaPickerState: StateFlow<AreaPickerState?> = areaPickerSession.state

    data class MarkTarget(val latitude: Double, val longitude: Double)

    /**
     * A point the user picked by tapping/long-pressing the map — it does NOT
     * need to be anywhere near the boat. Terrain and chart depth are sampled
     * for the preview sheet.
     */
    data class SelectedPoint(
        val latitude: Double,
        val longitude: Double,
        val terrain: TerrainType? = null,
        val depthMeters: Double? = null,
        val inDownloaded: Boolean = false,
    )

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val gps: GpsState = GpsState(),
        val places: List<SavedPlace> = emptyList(),
        val selectedPlace: SavedPlace? = null,
        val placeSheetVisible: Boolean = false,
        val layersSheetVisible: Boolean = false,
        val markTarget: MarkTarget? = null,
        val editingPlace: SavedPlace? = null,
        val deleteCandidate: SavedPlace? = null,
        val cardCollapsed: Boolean = false,
        val camera: MapCamera = MapCamera(),
        val online: Boolean = true,
        val bathymetry: BathymetryData? = null,
        val coastline: String? = null,
        /** Chart-model depth (meters) at the vessel's current position, live while sailing. */
        val depthMeters: Double? = null,
        /** Point picked by tapping/long-pressing the map, with sampled terrain + depth. */
        val selectedPoint: SelectedPoint? = null,
        /** Chart depth under the open mark-location editor (current or picked point). */
        val markDepth: ChartDepth? = null,
        /** Chart depth at the active navigation destination. */
        val navDestinationDepth: ChartDepth? = null,
        /** True when the sampled position sits inside a downloaded offline area. */
        val depthDownloaded: Boolean = false,
        /** Preset outline drawn on the map when previewing a download area. */
        val presetOutline: PresetOutline? = null,
        /** Custom-area framing mode is active. */
        val areaPickerActive: Boolean = false,
        val areaPickerName: String = "",
        val areaPickerEditingId: Long? = null,
        val navState: NavigationSessionState = NavigationSessionState(),
        val navPlan: NavigationPlan? = null,
        val tracking: TrackingController.State = TrackingController.State.Idle,
        val hasLocationPermission: Boolean = false,
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val viewedTrackId = MutableStateFlow<Long?>(null)

    /** Breadcrumbs of the live recording, or of the track selected for viewing. */
    val trackPoints: StateFlow<List<TrackPoint>> = trackingController.state
        .flatMapLatest { tracking ->
            when (tracking) {
                is TrackingController.State.Recording ->
                    trackRepository.observeTrackPoints(tracking.trackId)
                TrackingController.State.Idle ->
                    viewedTrackId.flatMapLatest { id ->
                        if (id == null) flowOf(emptyList())
                        else trackRepository.observeTrackPoints(id)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var cameraSaveJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.settings,
                locationRepository.gpsState,
                savedPlaceRepository.observePlaces(),
                navigationSession.state,
                connectivityObserver.isOnline,
            ) { settings, gps, places, nav, online ->
                Inputs(settings, gps, places, nav, online)
            }.collect { input ->
                _uiState.update {
                    it.copy(
                        settings = input.settings,
                        gps = input.gps,
                        places = input.places,
                        navState = input.nav,
                        online = input.online,
                    )
                }
            }
        }
        viewModelScope.launch {
            trackingController.state.collect { tracking ->
                _uiState.update { it.copy(tracking = tracking) }
            }
        }
        viewModelScope.launch {
            runCatching {
                val data = bathymetryRepository.data()
                val coastline = bathymetryRepository.coastlineGeoJson()
                _uiState.update { it.copy(bathymetry = data, coastline = coastline) }
                android.util.Log.i(
                    "XennMap",
                    "bathy ready: fill=${data.fillGeoJson.length} contour=${data.contourGeoJson.length} coast=${coastline.length}",
                )
            }.onFailure { error ->
                android.util.Log.e("XennMap", "bathymetry load failed", error)
            }
        }
        viewModelScope.launch {
            combine(locationRepository.gpsState, navigationSession.state) { gps, nav -> gps to nav }
                .collect { (gps, nav) ->
                    val plan = if (nav.isActive && nav.destination != null) {
                        planNavigation(gps.fix, nav.destination)
                    } else null
                    val destination = nav.destination
                    val destDepth = destination?.let {
                        chartDepthAt(it.latitude, it.longitude)
                    }
                    _uiState.update { it.copy(navPlan = plan, navDestinationDepth = destDepth) }
                }
        }
        // Live depth under the vessel: re-sample the chart model as the boat moves.
        viewModelScope.launch {
            var lastSampleAt = 0L
            locationRepository.gpsState.collect { gps ->
                val now = System.currentTimeMillis()
                if (gps.fix != null && now - lastSampleAt >= DEPTH_SAMPLE_INTERVAL_MS) {
                    lastSampleAt = now
                    val depth = runCatching { getDepthAtPosition(gps.fix) }.getOrNull()
                    val downloaded = isInsideDownloaded(gps.fix.latitude, gps.fix.longitude)
                    _uiState.update { it.copy(depthMeters = depth, depthDownloaded = downloaded) }
                }
            }
        }
        // Chart depth for the point being edited (current GPS or picked point).
        viewModelScope.launch {
            _uiState.map { it.markTarget }.distinctUntilChanged().collect { target ->
                val depth = target?.let { t -> chartDepthAt(t.latitude, t.longitude) }
                _uiState.update { it.copy(markDepth = depth) }
            }
        }
        // Restore last camera position once.
        viewModelScope.launch {
            val settings = preferencesRepository.settingsState.value
            _uiState.update {
                if (it.camera.latitude == 0.0 && it.camera.longitude == 0.0) {
                    it.copy(
                        camera = MapCamera(
                            latitude = settings.lastCameraLat,
                            longitude = settings.lastCameraLng,
                            zoom = settings.lastCameraZoom,
                        )
                    )
                } else it
            }
        }
    }

    // ------------------------------------------------------------ lifecycle

    fun onScreenStart() {
        if (_uiState.value.hasLocationPermission) locationRepository.start()
    }

    fun onScreenStop() {
        locationRepository.stop()
    }

    fun onPermissionGranted() {
        _uiState.update { it.copy(hasLocationPermission = true) }
        locationRepository.start()
    }

    // --------------------------------------------------------------- camera

    fun initialCamera(): MapCamera = _uiState.value.camera

    fun onCameraChanged(camera: MapCamera) {
        _uiState.update { it.copy(camera = camera) }
        cameraSaveJob?.cancel()
        cameraSaveJob = viewModelScope.launch {
            delay(1_200)
            runCatching {
                preferencesRepository.saveCamera(camera.latitude, camera.longitude, camera.zoom)
            }
        }
    }

    // --------------------------------------------------------------- places

    fun selectPlace(placeId: Long) {
        val place = _uiState.value.places.firstOrNull { it.id == placeId } ?: return
        _uiState.update { it.copy(selectedPlace = place, placeSheetVisible = true) }
    }

    fun dismissPlaceSheet() {
        _uiState.update { it.copy(placeSheetVisible = false, selectedPlace = null) }
    }

    fun toggleFavorite(place: SavedPlace) {
        viewModelScope.launch {
            savedPlaceRepository.setFavorite(place.id, !place.isFavorite)
        }
    }

    fun requestDelete(place: SavedPlace) {
        _uiState.update { it.copy(deleteCandidate = place) }
    }

    fun confirmDelete() {
        val place = _uiState.value.deleteCandidate ?: return
        viewModelScope.launch {
            savedPlaceRepository.delete(place.id)
            _uiState.update {
                it.copy(
                    deleteCandidate = null,
                    placeSheetVisible = false,
                    selectedPlace = null,
                    message = "\"${place.name}\" deleted",
                )
            }
        }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteCandidate = null) }
    }

    // ----------------------------------------------------------- mark place

    fun openMarkSheet() {
        val fix = _uiState.value.gps.fix ?: run {
            _uiState.update { it.copy(message = "No GPS fix yet — tap the map to pick a point instead") }
            return
        }
        _uiState.update { it.copy(markTarget = MarkTarget(fix.latitude, fix.longitude)) }
    }

    fun openMarkSheetAt(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(markTarget = MarkTarget(latitude, longitude)) }
    }

    fun dismissMarkSheet() {
        _uiState.update { it.copy(markTarget = null, editingPlace = null, markDepth = null) }
    }

    // ------------------------------------------------------- selected point

    /** Tap / long-press on open water or land: preview the point before saving. */
    fun selectPoint(latitude: Double, longitude: Double) {
        _uiState.update {
            it.copy(
                selectedPoint = MapViewModel.SelectedPoint(latitude, longitude),
                placeSheetVisible = false,
                selectedPlace = null,
            )
        }
        viewModelScope.launch {
            val terrain = runCatching { bathymetryRepository.classifyAt(latitude, longitude) }
                .getOrNull() ?: TerrainType.OUTSIDE
            val depth = if (terrain == TerrainType.SEA || terrain == TerrainType.COASTAL) {
                runCatching { getDepthAtPosition(latitude, longitude) }.getOrNull()
            } else null
            val downloaded = isInsideDownloaded(latitude, longitude)
            _uiState.update { state ->
                val point = state.selectedPoint
                if (point != null && point.latitude == latitude && point.longitude == longitude) {
                    state.copy(
                        selectedPoint = point.copy(
                            terrain = terrain,
                            depthMeters = depth,
                            inDownloaded = downloaded,
                        )
                    )
                } else state
            }
        }
    }

    fun dismissSelectedPoint() {
        _uiState.update { it.copy(selectedPoint = null) }
    }

    /** Opens the save form for the currently selected map point. */
    fun saveSelectedPoint() {
        val point = _uiState.value.selectedPoint ?: return
        _uiState.update {
            it.copy(markTarget = MarkTarget(point.latitude, point.longitude), selectedPoint = null)
        }
    }

    /** Starts steering assistance toward the selected point without saving it first. */
    fun navigateToPoint() {
        val point = _uiState.value.selectedPoint ?: return
        val now = System.currentTimeMillis()
        startNavigation(
            SavedPlace(
                id = -1,
                name = "Selected location",
                latitude = point.latitude,
                longitude = point.longitude,
                category = PlaceCategory.WAYPOINT,
                createdAt = now,
                updatedAt = now,
            )
        )
        _uiState.update { it.copy(selectedPoint = null) }
    }

    /** Tap on empty map: dismiss a place sheet if open, otherwise select the point. */
    fun onMapTapped(latitude: Double, longitude: Double) {
        if (_uiState.value.placeSheetVisible) dismissPlaceSheet()
        selectPoint(latitude, longitude)
    }

    /** Chart depth + whether the coordinate sits inside a downloaded offline area. */
    private suspend fun chartDepthAt(latitude: Double, longitude: Double): ChartDepth? {
        val meters = runCatching { getDepthAtPosition(latitude, longitude) }.getOrNull() ?: return null
        return ChartDepth(meters, isInsideDownloaded(latitude, longitude))
    }

    private fun isInsideDownloaded(latitude: Double, longitude: Double): Boolean =
        offlineRegionRepository.downloadedBounds.value.any { region ->
            latitude in region.south..region.north && longitude in region.west..region.east
        }

    // ------------------------------------------------- custom download area

    fun startAreaPicker() {
        areaPickerSession.start(initialBounds = null, regionName = "Custom area")
    }

    fun startAreaPickerFor(region: OfflineRegion) {
        areaPickerSession.start(
            initialBounds = DownloadBounds(region.south, region.west, region.north, region.east),
            regionName = region.name,
            editingRegionId = region.id,
        )
    }

    fun cancelAreaPicker() {
        areaPickerSession.end()
    }

    /** Confirms the framed custom area and starts the (online) download. */
    fun confirmAreaDownload(bounds: DownloadBounds) {
        val session = areaPickerSession.state.value ?: return
        val areaKm2 = ((bounds.east - bounds.west) * 111.0 * (bounds.north - bounds.south) * 111.0)
        val preset = RegionPreset(
            id = "custom-${System.currentTimeMillis()}",
            name = session.regionName.ifBlank { "Custom area" },
            description = "Custom downloaded area",
            south = bounds.south,
            west = bounds.west,
            north = bounds.north,
            east = bounds.east,
            estimatedSizeMb = (areaKm2 * 0.02).toInt().coerceIn(20, 4_000),
            minZoom = 6,
            maxZoom = 11,
        )
        session.editingRegionId?.let { regionId ->
            offlineRegionRepository.downloadedBounds.value.firstOrNull { it.id == regionId }?.let { old ->
                viewModelScope.launch { runCatching { offlineRegionRepository.delete(old) } }
            }
        }
        viewModelScope.launch {
            runCatching {
                offlineRegionRepository.download(preset, _uiState.value.settings.tileStyleUrl)
            }
        }
        areaPickerSession.end()
        _uiState.update {
            it.copy(
                message = "Downloading \"${preset.name}\" — needs mobile data or Wi-Fi",
                presetOutline = null,
            )
        }
    }

    /** Draws the outline of a preset download area on the map. */
    fun showPresetOutline(south: Double, west: Double, north: Double, east: Double, name: String) {
        _uiState.update { it.copy(presetOutline = PresetOutline(south, west, north, east, name)) }
    }

    fun startEdit(place: SavedPlace) {
        _uiState.update { it.copy(editingPlace = place, placeSheetVisible = false) }
    }

    fun savePlace(name: String, category: PlaceCategory, note: String) {
        val editing = _uiState.value.editingPlace
        val target = _uiState.value.markTarget
        viewModelScope.launch {
            if (editing != null) {
                savedPlaceRepository.save(
                    editing.copy(name = name, category = category, note = note)
                )
                _uiState.update { it.copy(message = "\"$name\" updated", editingPlace = null) }
            } else if (target != null) {
                val now = System.currentTimeMillis()
                savedPlaceRepository.save(
                    SavedPlace(
                        name = name,
                        latitude = target.latitude,
                        longitude = target.longitude,
                        category = category,
                        note = note,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                // Duplicate-coordinate edge case: saving is allowed, but the
                // user is told that something already exists at this spot.
                val nearDuplicate = _uiState.value.places.any { existing ->
                    GeoUtils.distanceMeters(
                        target.latitude, target.longitude,
                        existing.latitude, existing.longitude,
                    ) <= DUPLICATE_RADIUS_METERS
                }
                _uiState.update {
                    it.copy(
                        message = "\"$name\" saved" +
                            if (nearDuplicate) " — another saved place is within 10 m of this point" else "",
                        markTarget = null,
                    )
                }
            }
        }
    }

    // --------------------------------------------------------------- layers

    fun setLayers(layers: MapLayers) {
        viewModelScope.launch { preferencesRepository.setLayers(layers) }
    }

    fun setLayersSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(layersSheetVisible = visible) }
    }

    fun setCardCollapsed(collapsed: Boolean) {
        _uiState.update { it.copy(cardCollapsed = collapsed) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ----------------------------------------------------------- navigation

    fun startNavigation(place: SavedPlace) {
        navigationSession.start(place)
        commandBus.send(MapCommand.FlyToPlace(place.id, place))
        _uiState.update { it.copy(placeSheetVisible = false, selectedPlace = null) }
    }

    fun pauseNavigation() = navigationSession.pause()

    fun resumeNavigation() = navigationSession.resume()

    fun endNavigation() = navigationSession.end()

    // --------------------------------------------------------------- tracks

    fun showTrack(trackId: Long?) {
        viewedTrackId.value = trackId
        if (trackId != null) {
            viewModelScope.launch {
                val points = runCatching { trackRepository.points(trackId) }.getOrDefault(emptyList())
                points.lastOrNull()?.let { last ->
                    commandBus.send(MapCommand.FlyTo(last.latitude, last.longitude, 13.0))
                }
            }
        }
    }

    private data class Inputs(
        val settings: AppSettings,
        val gps: GpsState,
        val places: List<SavedPlace>,
        val nav: NavigationSessionState,
        val online: Boolean,
    )

    companion object {
        /** Re-sample the depth model every 2 s while a fix is available. */
        private const val DEPTH_SAMPLE_INTERVAL_MS = 2_000L

        /** Two saved places closer than this count as duplicates (informational). */
        private const val DUPLICATE_RADIUS_METERS = 10.0
    }
}
