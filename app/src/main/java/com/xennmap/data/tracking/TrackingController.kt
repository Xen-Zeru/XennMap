package com.xennmap.data.tracking

import com.xennmap.di.ApplicationScope
import com.xennmap.domain.model.TrackPoint
import com.xennmap.domain.repository.LocationRepository
import com.xennmap.domain.repository.PreferencesRepository
import com.xennmap.domain.repository.TrackRepository
import com.xennmap.utils.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records breadcrumb points into Room while active. Runs in the application
 * scope so tracking continues while the user browses other tabs.
 */
@Singleton
class TrackingController @Inject constructor(
    private val trackRepository: TrackRepository,
    private val locationRepository: LocationRepository,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    sealed interface State {
        data object Idle : State
        data class Recording(
            val trackId: Long,
            val startedAt: Long,
            val isPaused: Boolean,
            val pointCount: Int,
            val distanceMeters: Double,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var collectJob: Job? = null
    private val paused = AtomicBoolean(false)
    private var startedAt: Long = 0L

    fun start() {
        if (_state.value is State.Recording) return
        paused.set(false)
        scope.launch {
            val trackId = trackRepository.createTrack(defaultName())
            startedAt = System.currentTimeMillis()
            val intervalMs = preferencesRepository.settingsState.value.trackingIntervalSec * 1_000L
            locationRepository.start()

            var lastPoint: TrackPoint? = null
            var distance = 0.0
            var count = 0

            collectJob = scope.launch {
                locationRepository.gpsState.collect { gps ->
                    val fix = gps.fix ?: return@collect
                    if (paused.get()) return@collect
                    val now = System.currentTimeMillis()
                    val previous = lastPoint
                    val intervalOk = previous == null || now - previous.timestamp >= intervalMs
                    val moved = previous == null ||
                        GeoUtils.distanceMeters(
                            previous.latitude, previous.longitude, fix.latitude, fix.longitude,
                        ) >= MIN_MOVE_METERS
                    if (!intervalOk || !moved) return@collect

                    val point = TrackPoint(
                        trackId = trackId,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        speedMps = fix.speedMps,
                        bearingDeg = fix.bearingDeg ?: 0f,
                        accuracyMeters = fix.accuracyMeters,
                        timestamp = now,
                    )
                    trackRepository.addPoint(point)
                    if (previous != null) {
                        distance += GeoUtils.distanceMeters(
                            previous.latitude, previous.longitude, fix.latitude, fix.longitude,
                        )
                    }
                    lastPoint = point
                    count += 1
                    _state.value = State.Recording(trackId, startedAt, false, count, distance)
                }
            }

            startedAt = System.currentTimeMillis()
            _state.value = State.Recording(trackId, startedAt, false, 0, 0.0)
        }
    }
    fun pause() {
        val current = _state.value as? State.Recording ?: return
        paused.set(true)
        _state.value = current.copy(isPaused = true)
    }

    fun resume() {
        val current = _state.value as? State.Recording ?: return
        paused.set(false)
        _state.value = current.copy(isPaused = false)
    }

    fun stop() {
        val current = _state.value as? State.Recording ?: return
        collectJob?.cancel()
        collectJob = null
        _state.value = State.Idle
        scope.launch {
            trackRepository.finishTrack(
                trackId = current.trackId,
                endedAt = System.currentTimeMillis(),
                distanceMeters = current.distanceMeters,
                pointCount = current.pointCount,
            )
            locationRepository.stop()
        }
    }

    private fun defaultName(): String =
        "Trip " + SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date())

    companion object {
        private const val MIN_MOVE_METERS = 4.0
    }
}
