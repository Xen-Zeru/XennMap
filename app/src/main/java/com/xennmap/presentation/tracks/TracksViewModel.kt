package com.xennmap.presentation.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xennmap.data.tracking.TrackingController
import com.xennmap.domain.model.DistanceUnit
import com.xennmap.domain.model.Track
import com.xennmap.domain.repository.LocationRepository
import com.xennmap.domain.repository.PreferencesRepository
import com.xennmap.domain.repository.TrackRepository
import com.xennmap.presentation.common.MapCommand
import com.xennmap.presentation.common.MapCommandBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val trackingController: TrackingController,
    private val locationRepository: LocationRepository,
    preferencesRepository: PreferencesRepository,
    val commandBus: MapCommandBus,
) : ViewModel() {

    data class UiState(
        val tracks: List<Track> = emptyList(),
        val tracking: TrackingController.State = TrackingController.State.Idle,
        val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
        val message: String? = null,
    )

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(
        trackRepository.observeTracks(),
        trackingController.state,
        preferencesRepository.settings,
        message,
    ) { tracks, tracking, settings, msg ->
        UiState(
            tracks = tracks,
            tracking = tracking,
            distanceUnit = settings.distanceUnit,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        locationRepository.start()
    }

    override fun onCleared() {
        locationRepository.stop()
        super.onCleared()
    }

    fun startTracking() = trackingController.start()

    fun pauseTracking() = trackingController.pause()

    fun resumeTracking() = trackingController.resume()

    fun stopTracking() = trackingController.stop()

    fun viewOnMap(track: Track) {
        commandBus.send(MapCommand.ShowTrack(track.id))
    }

    fun clearTrackView() {
        commandBus.send(MapCommand.ShowTrack(null))
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            trackRepository.deleteTrack(track.id)
            message.value = "\"${track.name}\" deleted"
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
