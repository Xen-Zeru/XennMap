package com.xennmap.domain.usecase

import com.xennmap.domain.model.GpsFix
import com.xennmap.domain.model.NavigationPlan
import com.xennmap.domain.model.SavedPlace
import com.xennmap.utils.FormatUtils
import com.xennmap.utils.GeoUtils
import javax.inject.Inject

/**
 * Computes the live navigation assistance values toward a saved place.
 * Pure domain math — no Android dependencies.
 */
class PlanNavigationUseCase @Inject constructor() {

    operator fun invoke(fix: GpsFix?, destination: SavedPlace): NavigationPlan? {
        fix ?: return null
        val distance = GeoUtils.distanceMeters(
            fix.latitude, fix.longitude, destination.latitude, destination.longitude
        )
        val bearing = GeoUtils.initialBearing(
            fix.latitude, fix.longitude, destination.latitude, destination.longitude
        )
        val eta = FormatUtils.etaMinutes(distance, fix.speedMps)
        val speedUsed = if (fix.speedMps > 0.5f) fix.speedMps else (8f / FormatUtils.MPS_TO_KNOTS).toFloat()
        return NavigationPlan(
            destination = destination,
            distanceMeters = distance,
            bearingDeg = bearing,
            etaMinutes = eta,
            speedUsedMps = speedUsed,
        )
    }
}
