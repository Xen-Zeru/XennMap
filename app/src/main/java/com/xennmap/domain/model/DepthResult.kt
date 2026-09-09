package com.xennmap.domain.model

import com.xennmap.domain.model.TerrainType

/**
 * Result of a bathymetry depth query.
 * Domain layer version - no dependency on data layer.
 */
sealed interface DepthResult {
    data class Success(
        val meters: Double,
        val terrain: TerrainType,
        val metadata: BathymetryMetadata,
    ) : DepthResult

    data class NoCoverage(
        val reason: String = "Outside bathymetry dataset coverage",
    ) : DepthResult

    data class NoData(
        val reason: String = "Source cell contains no data",
    ) : DepthResult

    data class Land(
        val elevationMeters: Double,
    ) : DepthResult

    data class Error(
        val message: String,
    ) : DepthResult
}