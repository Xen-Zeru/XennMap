package com.xennmap.data.maps.bathymetry.source

import android.util.LruCache
import com.xennmap.data.maps.bathymetry.source.BathymetryTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LRU cache for bathymetry tiles.
 * Keeps recently accessed tiles in memory for fast repeated queries.
 * Default: ~10 MB (roughly 90 tiles at 112 KB each)
 */
class BathymetryTileCache(
    private val maxSizeBytes: Long = 10 * 1024 * 1024L,
) {
    private val cache = object : LruCache<String, BathymetryTile>(maxSizeBytes.toInt()) {
        override fun sizeOf(key: String, value: BathymetryTile): Int {
            return value.data.size
        }
    }

    fun get(key: String): BathymetryTile? = cache.get(key)

    fun put(key: String, tile: BathymetryTile) {
        cache.put(key, tile)
    }

    fun getOrLoad(key: String, loader: () -> BathymetryTile?): BathymetryTile? {
        return cache.get(key) ?: loader().also { tile ->
            tile?.let { cache.put(key, it) }
        }
    }

    suspend fun getOrLoadAsync(key: String, loader: suspend () -> BathymetryTile?): BathymetryTile? = withContext(Dispatchers.IO) {
        val cached = cache.get(key)
        if (cached != null) return@withContext cached
        val loaded = loader()
        loaded?.let { cache.put(key, it) }
        loaded
    }

    fun evict(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }

    fun snapshot(): List<BathymetryTile> = cache.snapshot().values.toList()

    val size: Int get() = cache.size()
    val maxSize: Int get() = cache.maxSize()
    fun hitCount(): Int = cache.hitCount()
    fun missCount(): Int = cache.missCount()
}