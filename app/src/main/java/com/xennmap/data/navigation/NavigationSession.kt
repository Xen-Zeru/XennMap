package com.xennmap.data.navigation

import com.xennmap.domain.model.NavPhase
import com.xennmap.domain.model.NavigationSessionState
import com.xennmap.domain.model.SavedPlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the active navigation-assistance session (destination + phase).
 * Shared between the Navigation tab and the map overlay. This is deliberately
 * simple steering assistance — never an autonomous navigation system.
 */
@Singleton
class NavigationSession @Inject constructor() {

    private val _state = MutableStateFlow(NavigationSessionState())
    val state: StateFlow<NavigationSessionState> = _state.asStateFlow()

    fun start(place: SavedPlace) {
        _state.value = NavigationSessionState(destination = place, phase = NavPhase.ACTIVE)
    }

    fun pause() {
        val current = _state.value
        if (current.isRunning) _state.value = current.copy(phase = NavPhase.PAUSED)
    }

    fun resume() {
        val current = _state.value
        if (current.isRunning) _state.value = current.copy(phase = NavPhase.ACTIVE)
    }

    fun end() {
        _state.value = NavigationSessionState()
    }
}
