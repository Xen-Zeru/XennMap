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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val settings: AppSettings = AppSettings(),
        val versionName: String = "",
        val message: String? = null,
    )

    private val message = MutableStateFlow<String?>(null)
    private val version = MutableStateFlow("")

    val uiState: StateFlow<UiState> = combine(
        preferencesRepository.settings,
        version,
        message,
    ) { settings, v, msg ->
        UiState(
            settings = settings,
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
            message.value = "Cached data cleared."
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun launchPref(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
