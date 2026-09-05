package com.xennmap.presentation.offline

import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xennmap.domain.model.AppSettings
import com.xennmap.domain.model.OfflineRegion
import com.xennmap.domain.model.RegionPreset
import com.xennmap.domain.repository.OfflineRegionRepository
import com.xennmap.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineViewModel @Inject constructor(
    private val offlineRegionRepository: OfflineRegionRepository,
    preferencesRepository: PreferencesRepository,
    private val areaPickerSession: com.xennmap.presentation.common.AreaPickerSession,
    private val commandBus: com.xennmap.presentation.common.MapCommandBus,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val regions: List<OfflineRegion> = emptyList(),
        val presets: List<RegionPreset> = emptyList(),
        val totalBytes: Long = 0,
        val freeBytes: Long = 0,
        val tileStyleUrl: String = AppSettings.DEFAULT_STYLE_URL,
        val deleteCandidate: OfflineRegion? = null,
        val presetsSheetVisible: Boolean = false,
        val message: String? = null,
    )

    private val deleteCandidate = MutableStateFlow<OfflineRegion?>(null)
    private val presetsSheetVisible = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val storageBytes = MutableStateFlow(0L)

    val uiState: StateFlow<UiState> = combine(
        offlineRegionRepository.observeRegions(),
        offlineRegionRepository.observeCompletedCount(),
        preferencesRepository.settings,
        combine(deleteCandidate, presetsSheetVisible, message, storageBytes) { d, p, m, s ->
            Local(d, p, m, s)
        },
    ) { regions, completed, settings, local ->
        UiState(
            regions = regions,
            presets = offlineRegionRepository.presets(),
            totalBytes = regions.sumOf { it.sizeBytes }.coerceAtLeast(0),
            freeBytes = local.storageBytes,
            tileStyleUrl = settings.tileStyleUrl,
            deleteCandidate = local.deleteCandidate,
            presetsSheetVisible = local.presetsSheetVisible,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private data class Local(
        val deleteCandidate: OfflineRegion?,
        val presetsSheetVisible: Boolean,
        val message: String?,
        val storageBytes: Long,
    )

    init {
        refreshStorage()
        viewModelScope.launch { runCatching { offlineRegionRepository.refreshSizes() } }
    }

    fun refreshStorage() {
        runCatching {
            val stat = StatFs(context.filesDir.path)
            storageBytes.value = stat.availableBytes
        }
    }

    fun download(preset: RegionPreset) {
        presetsSheetVisible.value = false
        message.value = "Downloading \"${preset.name}\"…"
        viewModelScope.launch {
            runCatching {
                offlineRegionRepository.download(preset, uiState.value.tileStyleUrl)
            }.onFailure { error ->
                message.value = error.message ?: "Download failed"
            }
            refreshStorage()
        }
    }

    fun retry(region: OfflineRegion) {
        val preset = offlineRegionRepository.presets().firstOrNull {
            it.name == region.name
        } ?: RegionPreset(
            id = region.name,
            name = region.name,
            description = "",
            south = region.south,
            west = region.west,
            north = region.north,
            east = region.east,
            estimatedSizeMb = 0,
            minZoom = region.minZoom,
            maxZoom = region.maxZoom,
        )
        download(preset)
    }

    fun requestDelete(region: OfflineRegion) {
        deleteCandidate.value = region
    }

    fun confirmDelete() {
        val region = deleteCandidate.value ?: return
        viewModelScope.launch {
            runCatching { offlineRegionRepository.delete(region) }
            deleteCandidate.value = null
            message.value = "\"${region.name}\" removed"
            refreshStorage()
        }
    }

    fun dismissDelete() {
        deleteCandidate.value = null
    }

    fun setPresetsSheet(visible: Boolean) {
        presetsSheetVisible.value = visible
    }

    /** Draws the preset outline on the map and flies to it. */
    fun showPresetArea(preset: RegionPreset) {
        commandBus.send(com.xennmap.presentation.common.MapCommand.ShowPresetArea(preset))
    }

    /** Opens the map in custom-area framing mode. */
    fun startCustomPicker() {
        areaPickerSession.start(
            initialBounds = null,
            regionName = "Custom area",
        )
    }

    /** Opens the map to re-frame an already downloaded area. */
    fun beginAdjust(region: OfflineRegion) {
        areaPickerSession.start(
            initialBounds = com.xennmap.presentation.common.DownloadBounds(
                region.south, region.west, region.north, region.east,
            ),
            regionName = region.name,
            editingRegionId = region.id,
        )
    }

    fun consumeMessage() {
        message.value = null
    }
}
