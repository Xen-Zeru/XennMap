package com.xennmap.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xennmap.domain.model.DistanceUnit
import com.xennmap.domain.model.GpsState
import com.xennmap.domain.model.Track
import com.xennmap.domain.repository.LocationRepository
import com.xennmap.domain.repository.OfflineRegionRepository
import com.xennmap.domain.repository.PreferencesRepository
import com.xennmap.domain.repository.SavedPlaceRepository
import com.xennmap.domain.repository.TrackRepository
import com.xennmap.presentation.common.MapCommand
import com.xennmap.presentation.common.MapCommandBus
import com.xennmap.utils.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val savedPlaceRepository: SavedPlaceRepository,
    offlineRegionRepository: OfflineRegionRepository,
    trackRepository: TrackRepository,
    preferencesRepository: PreferencesRepository,
    private val locationRepository: LocationRepository,
    private val commandBus: MapCommandBus,
) : ViewModel() {

    data class UiState(
        val gps: GpsState = GpsState(),
        val placeCount: Int = 0,
        val regionCount: Int = 0,
        val lastTrip: Track? = null,
        val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    )

    /** A notify-list entry with its live distance from the boat. */
    data class NotifyItem(
        val place: com.xennmap.domain.model.SavedPlace,
        val distanceMeters: Double?,
    )

    val uiState: StateFlow<UiState> = combine(
        locationRepository.gpsState,
        savedPlaceRepository.observeCount(),
        offlineRegionRepository.observeCompletedCount(),
        trackRepository.observeLatestTrack(),
        preferencesRepository.settings,
    ) { gps, places, regions, lastTrack, settings ->
        UiState(
            gps = gps,
            placeCount = places,
            regionCount = regions,
            lastTrip = lastTrack,
            distanceUnit = settings.distanceUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** Places pinned with Notify, with live distance from the current position. */
    val notifyItems: StateFlow<List<NotifyItem>> = combine(
        savedPlaceRepository.observeNotifyPlaces(),
        locationRepository.gpsState,
    ) { places, gps ->
        places.map { place ->
            NotifyItem(
                place = place,
                distanceMeters = gps.fix?.let { fix ->
                    GeoUtils.distanceMeters(fix.latitude, fix.longitude, place.latitude, place.longitude)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        locationRepository.start()
    }

    override fun onCleared() {
        locationRepository.stop()
        super.onCleared()
    }

    /** Un-pins a place from the notify list (the place itself is kept). */
    fun removeNotify(place: com.xennmap.domain.model.SavedPlace) {
        viewModelScope.launch { runCatching { savedPlaceRepository.setNotify(place.id, false) } }
    }

    /** Permanently deletes the saved place after the user confirms the dialog. */
    fun deletePlace(place: com.xennmap.domain.model.SavedPlace) {
        viewModelScope.launch {
            runCatching { savedPlaceRepository.delete(place.id) }
        }
    }

    /** Opens the map centered on the place. */
    fun viewOnMap(place: com.xennmap.domain.model.SavedPlace) {
        commandBus.send(MapCommand.FlyToPlace(place.id, place))
    }
}
