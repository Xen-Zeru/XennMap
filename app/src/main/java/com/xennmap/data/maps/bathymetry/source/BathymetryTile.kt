package com.xennmap.data.maps.bathymetry.source

/**
 * Represents a single bathymetry tile containing depth data.
 * 
 * Tile format:
 * - 1° × 1° tiles (matching GEBCO native grid)
 * - 15 arc-second resolution = 240 cells per degree
 * - Each tile: 240 × 240 = 57,600 cells
 * - Each cell: int16 = depth in meters (native GEBCO precision)
 * - NoData value: -32768 (int16 min)
 * - File size per tile: 57,600 × 2 bytes = 115,200 bytes (~112 KB)
 * 
 * Naming convention: gebco_N{lat}_E{lon}.bin (e.g., gebco_N10_E123.bin)
 * Latitude: 0-90 (N/S), Longitude: 0-180 (E/W)
 * Philippine coverage: N4-N21, E116-E127
 */
class BathymetryTile(
    val key: String,
    val data: ByteArray,
) {
    fun readDepthMeters(cellX: Int, cellY: Int): Int {
        val index = (cellY * BathymetryBinaryGridReader.CELLS_PER_DEG + cellX) * 2
        return data[index].toInt() + (data[index + 1].toInt() shl 8) // little-endian int16
    }

    companion object {
        fun create(key: String, data: ByteArray): BathymetryTile? {
            return if (data.size == BathymetryBinaryGridReader.TILE_BYTES) {
                BathymetryTile(key, data)
            } else null
        }
    }
}