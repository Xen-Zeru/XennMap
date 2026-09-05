package com.xennmap.domain.usecase

import com.xennmap.domain.model.GpsFix
import com.xennmap.domain.repository.BathymetryRepository
import javax.inject.Inject

/**
 * Depth under the vessel at its CURRENT GPS position, sampled live from the
 * bathymetry dataset as the boat moves.
 *
 * SAFETY: the phone has no sonar — this value comes from the depth dataset
 * (see [BathymetryRepository]) and must always be presented as chart data,
 * never as a live measurement.
 */
class GetDepthAtPositionUseCase @Inject constructor(
    private val bathymetryRepository: BathymetryRepository,
) {

    /** Depth in meters at the fix position, or null when unavailable. */
    suspend operator fun invoke(fix: GpsFix?): Double? =
        fix?.let { bathymetryRepository.depthAt(it.latitude, it.longitude) }

    /** Depth in meters at an arbitrary coordinate (e.g. a tapped map point). */
    suspend operator fun invoke(latitude: Double, longitude: Double): Double? =
        bathymetryRepository.depthAt(latitude, longitude)
}
