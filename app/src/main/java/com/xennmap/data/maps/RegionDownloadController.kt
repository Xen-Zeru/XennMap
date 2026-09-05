package com.xennmap.data.maps

import com.xennmap.data.local.dao.OfflineRegionDao
import com.xennmap.data.local.entity.OfflineRegionEntity
import com.xennmap.di.ApplicationScope
import com.xennmap.domain.model.RegionPreset
import com.xennmap.domain.model.RegionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates offline map downloads: creates Room metadata, drives the
 * MapLibre offline pack download, and keeps statuses/sizes in sync.
 */
@Singleton
class RegionDownloadController @Inject constructor(
    private val dao: OfflineRegionDao,
    private val offlineSource: MapLibreOfflineDataSource,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val activeNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        scope.launch { runCatching { reconcile() } }
    }

    fun download(preset: RegionPreset, styleUrl: String) {
        if (!activeNames.add(preset.name)) return
        scope.launch {
            val id = runCatching {
                val existing = dao.byName(preset.name)
                if (existing != null) {
                    dao.updateProgress(existing.id, RegionStatus.QUEUED.name, 0, 0)
                    existing.id
                } else {
                    dao.insert(
                        OfflineRegionEntity(
                            name = preset.name,
                            south = preset.south,
                            west = preset.west,
                            north = preset.north,
                            east = preset.east,
                            minZoom = preset.minZoom,
                            maxZoom = preset.maxZoom,
                            status = RegionStatus.QUEUED.name,
                            styleUrl = styleUrl,
                            requestedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }.getOrNull()

            if (id == null) {
                activeNames.remove(preset.name)
                return@launch
            }

            runCatching {
                dao.updateProgress(id, RegionStatus.DOWNLOADING.name, 0, 0)
                offlineSource.downloadRegion(preset, styleUrl) { percent, bytesSoFar ->
                    scope.launch {
                        runCatching {
                            dao.updateProgress(id, RegionStatus.DOWNLOADING.name, percent, bytesSoFar)
                        }
                    }
                }
                val size = offlineSource.packSizeBytes(preset.name) ?: 0L
                dao.updateCompleted(id, RegionStatus.COMPLETED.name, size, System.currentTimeMillis())
            }.onFailure { error ->
                dao.updateFailed(id, error.message ?: "Download failed")
            }
            activeNames.remove(preset.name)
        }
    }

    suspend fun delete(regionId: Long) {
        val region = dao.byId(regionId) ?: return
        runCatching { offlineSource.deleteByName(region.name) }
        dao.delete(region.id)
    }

    /** Reads actual pack sizes from MapLibre and refreshes the Room rows. */
    suspend fun refreshSizes() {
        val sizes = runCatching { offlineSource.sizesByName() }.getOrDefault(emptyMap())
        if (sizes.isEmpty()) return
        val rows = dao.allOnce()
        rows.forEach { row ->
            val size = sizes[row.name]
            if (size != null && size > 0 && row.sizeBytes != size) {
                runCatching { dao.updateSize(row.id, size) }
            }
        }
    }

    /**
     * Aligns Room rows with the actual MapLibre packs on startup:
     * - packs without a row (e.g. restored backup) become COMPLETED rows
     * - rows whose pack vanished are marked FAILED
     */
    private suspend fun reconcile() {
        val packs = runCatching { offlineSource.listPacks() }.getOrDefault(emptyList())
        val rows = dao.allOnce()
        val rowNames = rows.map { it.name }.toSet()

        packs.forEach { pack ->
            if (pack.name !in rowNames) {
                dao.insert(
                    OfflineRegionEntity(
                        name = pack.name,
                        south = pack.south,
                        west = pack.west,
                        north = pack.north,
                        east = pack.east,
                        minZoom = pack.minZoom.toInt(),
                        maxZoom = pack.maxZoom.toInt(),
                        status = RegionStatus.COMPLETED.name,
                        styleUrl = "",
                        requestedAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis(),
                    )
                )
            }
        }

        rows.forEach { row ->
            val packExists = packs.any { it.name == row.name }
            val failed = row.status == RegionStatus.FAILED.name
            when {
                !packExists && row.status == RegionStatus.DOWNLOADING.name ->
                    dao.updateFailed(row.id, "Download was interrupted")
                !packExists && row.status == RegionStatus.COMPLETED.name ->
                    dao.updateFailed(row.id, "Offline pack missing on this device")
                packExists && failed ->
                    dao.updateCompleted(row.id, RegionStatus.COMPLETED.name, row.sizeBytes, row.completedAt)
                else -> Unit
            }
        }
        refreshSizes()
    }
}
