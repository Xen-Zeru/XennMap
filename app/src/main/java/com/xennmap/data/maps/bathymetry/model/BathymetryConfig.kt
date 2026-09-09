package com.xennmap.data.maps.bathymetry.model

/**
 * Configuration for bathymetry download URLs.
 * 
 * In production, these URLs should point to a real CDN hosting the GEBCO 2024
 * bathymetry packages. The URLs are built from a base URL that can be configured
 * at build time via BuildConfig or at runtime via remote config.
 * 
 * If no valid URLs are configured, the app will show "Bathymetry package unavailable"
 * instead of attempting a fake download.
 */
object BathymetryConfig {

    /**
     * Base URL for bathymetry packages.
     * 
     * In debug builds, this can point to a local/test server.
     * In release builds, this should point to the production CDN.
     * 
     * Can be overridden at build time via:
     * - build.gradle.kts: buildConfigField("String", "BATHYMETRY_BASE_URL", "\"https://cdn.example.com/bathymetry\"")
     * - Or via remote config (Firebase Remote Config, etc.)
     */
    const val BASE_URL = "https://cdn.xennmap.com/bathymetry"

    /** Whether to use production URLs (when BASE_URL is configured) */
    val isProductionUrl: Boolean
        get() = BASE_URL != "https://cdn.xennmap.com/bathymetry" && BASE_URL.isNotBlank()

    /** Build the full MBTiles URL for a region */
    fun buildMbtilesUrl(regionId: String): String {
        return "$BASE_URL/$regionId.mbtiles"
    }

    /** Build the full grid ZIP URL for a region */
    fun buildGridUrl(regionId: String): String {
        return "$BASE_URL/${regionId}_grid.zip"
    }
}