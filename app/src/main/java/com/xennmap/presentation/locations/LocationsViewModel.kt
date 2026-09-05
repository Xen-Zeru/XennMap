package com.xennmap.presentation.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xennmap.data.navigation.NavigationSession
import com.xennmap.domain.model.ChartDepth
import com.xennmap.domain.model.DepthUnit
import com.xennmap.domain.model.DistanceUnit
import com.xennmap.domain.model.GpsState
import com.xennmap.domain.model.LocationFilter
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.domain.model.SavedPlace
import com.xennmap.domain.repository.BathymetryRepository
import com.xennmap.domain.repository.OfflineRegionRepository
import com.xennmap.domain.repository.LocationRepository
import com.xennmap.domain.repository.PreferencesRepository
import com.xennmap.domain.repository.SavedPlaceRepository
import com.xennmap.domain.usecase.GetDepthAtPositionUseCase
import com.xennmap.presentation.common.MapCommand
import com.xennmap.presentation.common.MapCommandBus
import com.xennmap.utils.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val savedPlaceRepository: SavedPlaceRepository,
    private val locationRepository: LocationRepository,
    private val bathymetryRepository: BathymetryRepository,
    preferencesRepository: PreferencesRepository,
    private val offlineRegionRepository: OfflineRegionRepository,
    private val getDepth: GetDepthAtPositionUseCase,
    private val navigationSession: NavigationSession,
    val commandBus: MapCommandBus,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val filter: LocationFilter = LocationFilter.ALL,
        val places: List<SavedPlace> = emptyList(),
        val gps: GpsState = GpsState(),
        val markTarget: MarkTarget? = null,
        val editingPlace: SavedPlace? = null,
        val deleteCandidate: SavedPlace? = null,
        val message: String? = null,
        /** Charted depth (meters) per place id; absent = no chart data there. */
        val depths: Map<Long, ChartDepth> = emptyMap(),
        /** Live distance (meters) from the boat's current position per place id. */
        val distances: Map<Long, Double> = emptyMap(),
        val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
        val depthUnit: DepthUnit = DepthUnit.METERS,
    )

    data class MarkTarget(val latitude: Double, val longitude: Double)

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LocationFilter.ALL)
    private val markTarget = MutableStateFlow<MarkTarget?>(null)
    private val editingPlace = MutableStateFlow<SavedPlace?>(null)
    private val deleteCandidate = MutableStateFlow<SavedPlace?>(null)
    private val message = MutableStateFlow<String?>(null)

    private data class Local(
        val markTarget: MarkTarget?,
        val editingPlace: SavedPlace?,
        val deleteCandidate: SavedPlace?,
        val message: String?,
    )

    /** Chart depth per saved place, re-sampled only when the place list changes. */
    private val placeDepths: StateFlow<Map<Long, ChartDepth>> = combine(
        savedPlaceRepository.observePlaces().distinctUntilChanged { old, new ->
            old.map { Triple(it.id, it.latitude, it.longitude) } ==
                new.map { Triple(it.id, it.latitude, it.longitude) }
        },
        offlineRegionRepository.downloadedBounds,
    ) { list, bounds ->
        buildMap {
            for (place in list) {
                runCatching {
                    getDepth(place.latitude, place.longitude)
                }.getOrNull()?.let {
                    val downloaded = bounds.any { b ->
                        place.latitude in b.south..b.north && place.longitude in b.west..b.east
                    }
                    put(place.id, ChartDepth(it, downloaded))
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private data class Scene(
        val query: String,
        val filter: LocationFilter,
        val gps: GpsState,
        val local: Local,
    )

    val uiState: StateFlow<UiState> = combine(
        savedPlaceRepository.observePlaces(),
        placeDepths,
        preferencesRepository.settings,
        combine(
            query,
            filter,
            locationRepository.gpsState,
            combine(markTarget, editingPlace, deleteCandidate, message) { m, e, d, msg ->
                Local(m, e, d, msg)
            },
        ) { q, f, gps, local ->
            Scene(q, f, gps, local)
        },
    ) { places, depths, settings, scene ->
        UiState(
            query = scene.query,
            filter = scene.filter,
            places = savedPlaceRepository.filter(places, scene.query, scene.filter),
            gps = scene.gps,
            markTarget = scene.local.markTarget,
            editingPlace = scene.local.editingPlace,
            deleteCandidate = scene.local.deleteCandidate,
            message = scene.local.message,
            depths = depths,
            distances = scene.gps.fix?.let { fix ->
                places.associate {
                    it.id to GeoUtils.distanceMeters(
                        fix.latitude, fix.longitude, it.latitude, it.longitude,
                    )
                }
            } ?: emptyMap(),
            distanceUnit = settings.distanceUnit,
            depthUnit = settings.depthUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        locationRepository.start()
    }

    override fun onCleared() {
        locationRepository.stop()
        super.onCleared()
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: LocationFilter) {
        filter.value = value
    }

    fun openOnMap(place: SavedPlace) {
        commandBus.send(MapCommand.FlyToPlace(place.id, place))
    }

    fun startNavigation(place: SavedPlace) {
        navigationSession.start(place)
    }

    fun toggleFavorite(place: SavedPlace) {
        viewModelScope.launch { savedPlaceRepository.setFavorite(place.id, !place.isFavorite) }
    }

    fun requestDelete(place: SavedPlace) {
        deleteCandidate.value = place
    }

    fun confirmDelete() {
        val place = deleteCandidate.value ?: return
        viewModelScope.launch {
            savedPlaceRepository.delete(place.id)
            deleteCandidate.value = null
            this@LocationsViewModel.message.value = "\"${place.name}\" deleted"
        }
    }

    fun dismissDelete() {
        deleteCandidate.value = null
    }

    fun startEdit(place: SavedPlace) {
        editingPlace.value = place
    }

    fun openMarkSheet() {
        val fix = uiState.value.gps.fix
        if (fix == null) {
            message.value = "No GPS fix yet — try again once location is ready"
            return
        }
        markTarget.value = MarkTarget(fix.latitude, fix.longitude)
    }

    fun dismissSheets() {
        markTarget.value = null
        editingPlace.value = null
    }

    fun savePlace(name: String, category: PlaceCategory, note: String) {
        val editing = editingPlace.value
        val target = markTarget.value
        viewModelScope.launch {
            if (editing != null) {
                savedPlaceRepository.save(editing.copy(name = name, category = category, note = note))
                message.value = "\"$name\" updated"
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
                message.value = "\"$name\" saved"
            }
            markTarget.value = null
            editingPlace.value = null
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
