package com.xennmap.data.maps.bathymetry.model

/**
 * Bathymetry download regions for the Philippines.
 * Each region has separate MBTiles and binary grid downloads.
 * 
 * URLs are built dynamically from BathymetryConfig.BASE_URL.
 * If BASE_URL is not configured for production, the URLs will be placeholder
 * and the app will show "Bathymetry package unavailable" instead of attempting download.
 */
enum class BathymetryRegion(
    val id: String,
    val displayName: String,
    val description: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    WHOLE_PHILIPPINES(
        displayName = "Philippines (Whole Country)",
        id = "gebco2024_ph_whole",
        description = "GEBCO 2024 bathymetry for entire Philippine archipelago",
        south = 4.0, west = 116.0, north = 21.5, east = 127.0,
    ),
    LUZON(
        displayName = "Luzon",
        id = "gebco2024_ph_luzon",
        description = "GEBCO 2024 bathymetry for Luzon and surrounding waters",
        south = 12.0, west = 116.0, north = 21.5, east = 127.0,
    ),
    VISAYAS(
        displayName = "Visayas",
        id = "gebco2024_ph_visayas",
        description = "GEBCO 2024 bathymetry for Visayas region",
        south = 8.0, west = 121.0, north = 12.5, east = 127.0,
    ),
    MINDANAO(
        displayName = "Mindanao",
        id = "gebco2024_ph_mindanao",
        description = "GEBCO 2024 bathymetry for Mindanao and surrounding waters",
        south = 4.0, west = 116.0, north = 9.0, east = 127.0,
    );

    /**
     * Get the MBTiles download URL for this region.
     * Returns null if production URLs are not configured.
     */
    val mbtilesUrl: String?
        get() = if (BathymetryConfig.isProductionUrl) BathymetryConfig.buildMbtilesUrl(id) else null

    /**
     * Get the binary grid ZIP download URL for this region.
     * Returns null if production URLs are not configured.
     */
    val gridUrl: String?
        get() = if (BathymetryConfig.isProductionUrl) BathymetryConfig.buildGridUrl(id) else null

    fun contains(lat: Double, lon: Double): Boolean =
        lat in south..north && lon in west..east
}