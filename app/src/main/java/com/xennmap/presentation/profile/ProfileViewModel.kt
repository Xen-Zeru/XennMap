package com.xennmap.presentation.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xennmap.domain.model.AppSettings
import com.xennmap.domain.model.DepthUnit
import com.xennmap.domain.model.DistanceUnit
import com.xennmap.domain.model.LocationAccuracyMode
import com.xennmap.domain.model.MapLayers
import com.xennmap.domain.model.SpeedUnit
import com.xennmap.domain.model.ThemeMode
import com.xennmap.domain.repository.OfflineRegionRepository
import com.xennmap.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val offlineRegionRepository: OfflineRegionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val offlineBytes: Long = 0,
        val cacheBytes: Long = 0,
        val versionName: String = "",
        val message: String? = null,
    )

    private val storage = MutableStateFlow(Pair(0L, 0L))
    private val message = MutableStateFlow<String?>(null)
    private val version = MutableStateFlow("")

    val uiState: StateFlow<UiState> = combine(
        preferencesRepository.settings,
        storage,
        version,
        message,
    ) { settings, sizes, v, msg ->
        UiState(
            settings = settings,
            offlineBytes = sizes.first,
            cacheBytes = sizes.second,
            versionName = v,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            version.value = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            }.getOrDefault("1.0")
        }
        viewModelScope.launch { refreshStorage() }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val offline = runCatching { offlineRegionRepository.totalSizeBytes() }.getOrDefault(0)
            val cache = withContext(Dispatchers.IO) { dirSize(context.cacheDir) }
            storage.value = offline to cache
        }
    }

    fun setThemeMode(mode: ThemeMode) = launchPref { preferencesRepository.setThemeMode(mode) }

    fun setDistanceUnit(unit: DistanceUnit) = launchPref { preferencesRepository.setDistanceUnit(unit) }

    fun setDepthUnit(unit: DepthUnit) = launchPref { preferencesRepository.setDepthUnit(unit) }

    fun setSpeedUnit(unit: SpeedUnit) = launchPref { preferencesRepository.setSpeedUnit(unit) }

    fun setAccuracyMode(mode: LocationAccuracyMode) = launchPref { preferencesRepository.setAccuracyMode(mode) }

    fun setTrackingInterval(seconds: Int) = launchPref { preferencesRepository.setTrackingInterval(seconds) }

    fun setLayers(layers: MapLayers) = launchPref { preferencesRepository.setLayers(layers) }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }
            refreshStorage()
            message.value = "Cached data cleared — offline areas kept."
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun launchPref(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    private fun dirSize(dir: File?): Long {
        dir ?: return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
