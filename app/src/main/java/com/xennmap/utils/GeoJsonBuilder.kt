package com.xennmap.utils

/**
 * Minimal GeoJSON string builder. Hand-rolled to avoid pulling a JSON-mapping
 * dependency for large generated payloads (bathymetry grids, tracks).
 * Property values must already be valid JSON fragments (see helpers below).
 */
object GeoJsonBuilder {

    fun num(value: Double): String = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }

    fun str(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun props(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(",", "{", "}") { (k, v) -> "${str(k)}:$v" }

    fun point(lon: Double, lat: Double, propertiesJson: String): String =
        """{"type":"Feature","properties":$propertiesJson,"geometry":{"type":"Point","coordinates":[${num(lon)},${num(lat)}]}}"""

    fun lineString(coords: List<Pair<Double, Double>>, propertiesJson: String): String {
        val c = coords.joinToString(",") { "[${num(it.first)},${num(it.second)}]" }
        return """{"type":"Feature","properties":$propertiesJson,"geometry":{"type":"LineString","coordinates":[$c]}}"""
    }

    /** Single-ring polygon feature. */
    fun polygon(ring: List<Pair<Double, Double>>, propertiesJson: String): String {
        val c = ring.joinToString(",") { "[${num(it.first)},${num(it.second)}]" }
        return """{"type":"Feature","properties":$propertiesJson,"geometry":{"type":"Polygon","coordinates":[[$c]]}}"""
    }

    fun collection(features: List<String>): String =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}
