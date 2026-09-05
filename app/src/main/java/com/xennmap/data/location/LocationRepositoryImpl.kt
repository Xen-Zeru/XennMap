package com.xennmap.data.location

import android.content.Context
import com.xennmap.data.local.dao.UserLocationDao
import com.xennmap.data.local.entity.UserLocationEntity
import com.xennmap.di.ApplicationScope
import com.xennmap.domain.model.GpsFix
import com.xennmap.domain.model.GpsSignalState
import com.xennmap.domain.model.GpsState
import com.xennmap.domain.model.LocationAccuracyMode
import com.xennmap.domain.repository.LocationRepository
import com.xennmap.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationDataSource: FusedLocationDataSource,
    private val aospLocationDataSource: AospLocationDataSource,
    private val compassDataSource: CompassDataSource,
    private val preferencesRepository: PreferencesRepository,
    private val userLocationDao: UserLocationDao,
    @ApplicationScope private val scope: CoroutineScope,
) : LocationRepository {

    private val _gpsState = MutableStateFlow(GpsState())
    override val gpsState: StateFlow<GpsState> = _gpsState.asStateFlow()

    private val consumers = AtomicInteger(0)
    private var collectJob: Job? = null
    private var lastHistoryWrite = 0L

    override fun start() {
        if (consumers.incrementAndGet() > 1) return
        if (collectJob?.isActive == true) return

        _gpsState.update { it.copy(isStarted = true, signal = GpsSignalState.ACQUIRING) }
        val source = if (playServicesAvailable(context)) fusedLocationDataSource else aospLocationDataSource

        collectJob = scope.launch {
            launch {
                source.fixes().collect { raw -> onRawFix(raw) }
            }
            launch {
                compassDataSource.headings().collect { heading ->
                    _gpsState.update { state ->
                        val fix = state.fix ?: return@update state
                        val useCompass = fix.speedMps < STATIONARY_SPEED_MPS
                        if (!useCompass) return@update state
                        state.copy(fix = fix.copy(bearingDeg = heading))
                    }
                }
            }
        }
    }

    override fun stop() {
        if (consumers.decrementAndGet() > 0) return
        collectJob?.cancel()
        collectJob = null
        _gpsState.update { it.copy(isStarted = false, signal = GpsSignalState.NO_FIX, satelliteCount = null) }
    }

    private fun onRawFix(raw: RawFix) {
        val fix = GpsFix(
            latitude = raw.latitude,
            longitude = raw.longitude,
            accuracyMeters = raw.accuracyMeters,
            speedMps = raw.speedMps,
            bearingDeg = raw.bearingDeg,
            timestamp = raw.timestamp,
        )
        val signal = when {
            raw.accuracyMeters <= 0f -> GpsSignalState.ACQUIRING
            raw.accuracyMeters <= 12f -> GpsSignalState.GOOD
            raw.accuracyMeters <= 35f -> GpsSignalState.FAIR
            else -> GpsSignalState.POOR
        }
        _gpsState.update {
            it.copy(
                fix = fix,
                signal = signal,
                satelliteCount = raw.satelliteCount ?: it.satelliteCount,
                isStarted = true,
            )
        }
        maybeRecordHistory(fix)
    }

    /** Keeps a small private position history on-device (never synced anywhere). */
    private fun maybeRecordHistory(fix: GpsFix) {
        val now = System.currentTimeMillis()
        if (now - lastHistoryWrite < HISTORY_INTERVAL_MS) return
        lastHistoryWrite = now
        scope.launch {
            runCatching {
                userLocationDao.insert(
                    UserLocationEntity(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        accuracyMeters = fix.accuracyMeters,
                        timestamp = now,
                    )
                )
            }
        }
    }

    companion object {
        private const val STATIONARY_SPEED_MPS = 0.6f
        private const val HISTORY_INTERVAL_MS = 60_000L
    }
}
