package com.xennmap.domain.model

/** Live navigation assistance toward a saved place. */
data class NavigationPlan(
    val destination: SavedPlace,
    val distanceMeters: Double,
    /** Initial bearing from the current position to the destination, degrees true. */
    val bearingDeg: Double,
    val etaMinutes: Int,
    /** Speed used for the ETA estimate, m/s. */
    val speedUsedMps: Float,
)

enum class NavPhase { IDLE, ACTIVE, PAUSED }

data class NavigationSessionState(
    val destination: SavedPlace? = null,
    val phase: NavPhase = NavPhase.IDLE,
) {
    val isActive: Boolean get() = phase == NavPhase.ACTIVE
    val isPaused: Boolean get() = phase == NavPhase.PAUSED
    val isRunning: Boolean get() = phase != NavPhase.IDLE
}
