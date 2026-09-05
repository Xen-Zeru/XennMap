package com.xennmap.data.maps

import android.content.Context
import com.xennmap.domain.model.RegionPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin coroutine wrapper around MapLibre's OfflineManager.
 *
 * Offline packs are stored by MapLibre in its own SQLite tile store and are
 * served fully offline afterwards; this class only orchestrates creation,
 * progress, sizes and deletion. Kept in one file so any API adjustments for
 * newer MapLibre releases stay local.
 */
@Singleton
class MapLibreOfflineDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class PackInfo(
        val name: String,
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
        val minZoom: Double,
        val maxZoom: Double,
    )

    private val offlineManager: OfflineManager by lazy { OfflineManager.getInstance(context) }

    /**
     * Downloads an offline pack for [preset] using [styleUrl].
     * [onProgress] receives percentage 0..99 and bytes downloaded so far.
     * Suspends until the pack is complete; throws on failure.
     */
    suspend fun downloadRegion(
        preset: RegionPreset,
        styleUrl: String,
        onProgress: (Int, Long) -> Unit,
    ) = suspendCancellableCoroutine { cont ->
        val bounds = LatLngBounds.from(preset.north, preset.east, preset.south, preset.west)
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            bounds,
            preset.minZoom.toDouble(),
            preset.maxZoom.toDouble(),
            context.resources.displayMetrics.density,
        )
        val metadata = JSONObject()
            .put(METADATA_NAME, preset.name)
            .put(METADATA_REQUESTED_AT, System.currentTimeMillis())
            .toString()
            .toByteArray(Charsets.UTF_8)
        val resumed = AtomicBoolean(false)

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val required = status.requiredResourceCount
                            val percent = if (required > 0) {
                                ((status.completedResourceCount * 100) / required).toInt()
                            } else 0
                            onProgress(percent.coerceIn(0, 99), status.completedResourceSize)
                            if (status.isComplete) {
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                if (resumed.compareAndSet(false, true)) cont.resume(Unit)
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            if (resumed.compareAndSet(false, true)) {
                                cont.resumeWithException(IOException("Download failed: ${error.message}"))
                            }
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            if (resumed.compareAndSet(false, true)) {
                                cont.resumeWithException(
                                    IOException("Tile count limit exceeded ($limit). Try a smaller area."),
                                )
                            }
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    if (resumed.compareAndSet(false, true)) {
                        cont.resumeWithException(IOException(error))
                    }
                }
            },
        )
    }

    suspend fun listPacks(): List<PackInfo> = suspendCancellableCoroutine { cont ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                cont.resume(regions?.mapNotNull { region -> region.toPackInfo() } ?: emptyList())
            }

            override fun onError(error: String) {
                cont.resume(emptyList())
            }
        })
    }

    suspend fun packSizeBytes(name: String): Long? = statusSizeBytes(name)

    /** Queries MapLibre for the on-disk size of each pack, keyed by name. */
    suspend fun sizesByName(): Map<String, Long> {
        val packs = listPacks()
        val result = LinkedHashMap<String, Long>(packs.size)
        packs.forEach { pack ->
            statusSizeBytes(pack.name)?.let { result[pack.name] = it }
        }
        return result
    }

    private suspend fun statusSizeBytes(name: String): Long? = suspendCancellableCoroutine { cont ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val match = regions.orEmpty().firstOrNull { region ->
                    runCatching {
                        JSONObject(String(region.metadata, Charsets.UTF_8)).optString(METADATA_NAME) == name
                    }.getOrDefault(false)
                }
                if (match == null) {
                    cont.resume(null)
                    return
                }
                match.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                    override fun onStatus(status: OfflineRegionStatus?) {
                        cont.resume(status?.completedResourceSize)
                    }

                    override fun onError(error: String?) {
                        cont.resume(null)
                    }
                })
            }

            override fun onError(error: String) {
                cont.resume(null)
            }
        })
    }

    suspend fun deleteByName(name: String) = suspendCancellableCoroutine { cont ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val matches = regions.orEmpty().filter { region ->
                    runCatching {
                        JSONObject(String(region.metadata, Charsets.UTF_8)).optString(METADATA_NAME) == name
                    }.getOrDefault(false)
                }
                if (matches.isEmpty()) {
                    cont.resume(Unit)
                    return
                }
                var pending = matches.size
                matches.forEach { region ->
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            pending -= 1
                            if (pending == 0) cont.resume(Unit)
                        }

                        override fun onError(error: String) {
                            pending -= 1
                            if (pending == 0) cont.resume(Unit)
                        }
                    })
                }
            }

            override fun onError(error: String) {
                cont.resume(Unit)
            }
        })
    }

    private fun OfflineRegion.toPackInfo(): PackInfo? = runCatching {
        val meta = JSONObject(String(metadata, Charsets.UTF_8))
        val name = meta.optString(METADATA_NAME).ifEmpty { return null }
        val definition = this.definition
        val pyramid = definition as? OfflineTilePyramidRegionDefinition
        val bounds = pyramid?.bounds
        PackInfo(
            name = name,
            south = bounds?.latitudeSouth ?: 0.0,
            west = bounds?.longitudeWest ?: 0.0,
            north = bounds?.latitudeNorth ?: 0.0,
            east = bounds?.longitudeEast ?: 0.0,
            minZoom = pyramid?.minZoom ?: 0.0,
            maxZoom = pyramid?.maxZoom ?: 0.0,
        )
    }.getOrNull()

    companion object {
        private const val METADATA_NAME = "name"
        private const val METADATA_REQUESTED_AT = "requestedAt"
    }
}
