package com.xennmap.domain.model

/**
 * Describes where the bathymetry layer's data comes from.
 *
 * SAFETY: bathymetry shows static chart/depth data from a dataset — it is never
 * a live measurement from the phone. The UI must always surface the source
 * and vintage so the user knows what they are looking at.
 */
data class BathymetryDataset(
    val id: String,
    val name: String,
    val source: String,
    val license: String,
    val vintage: String,
    val coverageNote: String,
    val isSample: Boolean,
)

/** Generated GeoJSON payloads (fill bands, contour segments, label points) for the bathymetry layer. */
data class BathymetryData(
    val dataset: BathymetryDataset,
    val fillGeoJson: String,
    val contourGeoJson: String,
    val labelGeoJson: String,
    /** Depth band breakpoints in meters, e.g. [5, 10, 20, 50, 100, 200]. */
    val bandBreaks: List<Int>,
)

/** Chart depth sampled at a coordinate, plus how to label its source. */
data class ChartDepth(val meters: Double, val downloaded: Boolean)

/** What the chart model knows about a selected coordinate. */
enum class TerrainType {
    /** Open water with charted depth. */
    SEA,

    /** Water within the shoreline band of the model. */
    COASTAL,

    /** The selected point falls on land per the coastline data. */
    LAND,

    /** Outside the coverage of the depth dataset. */
    OUTSIDE,
}
