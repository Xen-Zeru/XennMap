package com.xennmap.domain.repository

import com.xennmap.domain.model.GpsFix
import com.xennmap.domain.model.GpsState
import kotlinx.coroutines.flow.StateFlow

/**
 * Access to the device GPS. Works fully offline; network connectivity is never
 * required for position fixes.
 */
interface LocationRepository {

    /** Hot stream of the current GPS situation. */
    val gpsState: StateFlow<GpsState>

    /** Start producing fixes. Safe to call multiple times (ref-counted). */
    fun start()

    /** Signal that one consumer stopped. Stops the engine when the count reaches zero. */
    fun stop()
}
