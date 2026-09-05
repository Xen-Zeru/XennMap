package com.xennmap.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xennmap.data.navigation.NavigationSession
import com.xennmap.domain.model.AppSettings
import com.xennmap.domain.model.GpsState
import com.xennmap.domain.model.NavigationPlan
import com.xennmap.domain.model.NavigationSessionState
import com.xennmap.domain.model.SavedPlace
import com.xennmap.domain.repository.BathymetryRepository
import com.xennmap.domain.repository.LocationRepository
import com.xennmap.domain.repository.PreferencesRepository
import com.xennmap.domain.repository.SavedPlaceRepository
import com.xennmap.domain.usecase.PlanNavigationUseCase
import com.xennmap.presentation.common.MapCommand
import com.xennmap.presentation.common.MapCommandBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val savedPlaceRepository: SavedPlaceRepository,
    private val locationRepository: LocationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val bathymetryRepository: BathymetryRepository,
    private val navigationSession: NavigationSession,
    private val commandBus: MapCommandBus,
    private val planNavigation: PlanNavigationUseCase,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val places: List<SavedPlace> = emptyList(),
        val selected: SavedPlace? = null,
        val preview: NavigationPlan? = null,
        val gps: GpsState = GpsState(),
        val session: NavigationSessionState = NavigationSessionState(),
        val settings: AppSettings = AppSettings(),
    )

    private val query = MutableStateFlow("")
    private val selectedId = MutableStateFlow<Long?>(null)

    private data class SelectionState(
        val query: String,
        val selectedId: Long?,
        val gps: GpsState,
        val session: NavigationSessionState,
        val settings: AppSettings,
    )

    val uiState: StateFlow<UiState> = combine(
        savedPlaceRepository.observePlaces(),
        combine(
            query,
            selectedId,
            locationRepository.gpsState,
            navigationSession.state,
            preferencesRepository.settings,
        ) { q, selected, gps, session, settings ->
            SelectionState(q, selected, gps, session, settings)
        },
    ) { places, selection ->
        val filtered = if (selection.query.isBlank()) places else places.filter {
            it.name.contains(selection.query, ignoreCase = true) ||
                it.note.contains(selection.query, ignoreCase = true)
        }
        val chosen = places.firstOrNull { it.id == selection.selectedId }
        UiState(
            query = selection.query,
            places = filtered.sortedByDescending { it.isFavorite },
            selected = chosen,
            preview = chosen?.let { planNavigation(selection.gps.fix, it) },
            gps = selection.gps,
            session = selection.session,
            settings = selection.settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val sessionState: StateFlow<NavigationSessionState> = navigationSession.state

    /** Charted depth at the selected destination (null when unavailable/sampling). */
    val selectedDepth: StateFlow<Double?> = combine(selectedId, savedPlaceRepository.observePlaces()) { id, places ->
        id?.let { pid -> places.firstOrNull { it.id == pid } }
    }
        .distinctUntilChanged()
        .flatMapLatest { place: SavedPlace? ->
            flow {
                emit(
                    place?.let {
                        runCatching { bathymetryRepository.depthAt(it.latitude, it.longitude) }.getOrNull()
                    }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Charted depth at the active navigation destination. */
    val sessionDestinationDepth: StateFlow<Double?> = navigationSession.state
        .flatMapLatest { session ->
            flow {
                emit(
                    session.destination?.let {
                        runCatching { bathymetryRepository.depthAt(it.latitude, it.longitude) }.getOrNull()
                    }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ids of places pinned to the Dashboard notify list. */
    val notifyIds: StateFlow<Set<Long>> = savedPlaceRepository.observeNotifyPlaces()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Pins/unpins a place on the Dashboard notify list. */
    fun toggleNotify(place: SavedPlace) {
        viewModelScope.launch {
            savedPlaceRepository.setNotify(place.id, !place.isNotify)
        }
    }

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

    fun select(place: SavedPlace) {
        selectedId.value = place.id
    }

    fun startNavigation(place: SavedPlace) {
        navigationSession.start(place)
        commandBus.send(MapCommand.FlyToPlace(place.id, place))
    }

    fun pause() = navigationSession.pause()

    fun resume() = navigationSession.resume()

    fun end() = navigationSession.end()

    fun showOnMap() {
        val destination = navigationSession.state.value.destination ?: return
        commandBus.send(MapCommand.FlyToPlace(destination.id, destination))
    }
}
