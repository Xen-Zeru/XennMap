package com.xennmap.presentation.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-screen state for framing a custom download area on the map.
 * The Offline screen starts a session; the Map tab renders the frame,
 * lets the user pan/zoom to size it, and confirms the download.
 */
data class DownloadBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val areaKm2: Double
        get() = (north - south) * 111.0 * (east - west) * 111.0
}

data class AreaPickerState(
    /** Initial frame to fit (editing an existing region); null = use current view. */
    val initialBounds: DownloadBounds?,
    /** When set, confirming replaces this downloaded region with the new frame. */
    val editingRegionId: Long? = null,
    val regionName: String,
)

@Singleton
class AreaPickerSession @Inject constructor() {

    private val _state = MutableStateFlow<AreaPickerState?>(null)
    val state: StateFlow<AreaPickerState?> = _state.asStateFlow()

    fun start(initialBounds: DownloadBounds?, regionName: String, editingRegionId: Long? = null) {
        _state.value = AreaPickerState(initialBounds, editingRegionId, regionName)
    }

    fun end() {
        _state.value = null
    }
}
