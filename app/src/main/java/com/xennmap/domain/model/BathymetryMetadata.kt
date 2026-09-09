package com.xennmap.domain.model

/**
 * Metadata about the bathymetry dataset used for a depth query.
 * Provides traceability and context for charted depth values.
 */
data class BathymetryMetadata(
    val source: String,
    val version: String,
    val resolutionArcSec: Int,
    val resolutionMeters: Int,
    val verticalDatum: String,
    val horizontalDatum: String,
    val attribution: String,
    val tileSource: String,
)