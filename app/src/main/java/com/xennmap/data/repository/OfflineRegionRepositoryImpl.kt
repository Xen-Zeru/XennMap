package com.xennmap.data.repository

import com.xennmap.data.local.dao.OfflineRegionDao
import com.xennmap.data.local.toDomain
import com.xennmap.data.maps.RegionDownloadController
import com.xennmap.data.maps.RegionPresets
import com.xennmap.domain.model.OfflineRegion
import com.xennmap.domain.model.RegionPreset
import com.xennmap.domain.repository.OfflineRegionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRegionRepositoryImpl @Inject constructor(
    private val dao: OfflineRegionDao,
    private val downloadController: RegionDownloadController,
) : OfflineRegionRepository {

    override fun observeRegions(): Flow<List<OfflineRegion>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override val downloadedBounds: StateFlow<List<OfflineRegion>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() }.filter { it.isAvailableOffline } }
            .stateIn(
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                SharingStarted.Eagerly,
                emptyList(),
            )

    override fun observeCompletedCount(): Flow<Int> = dao.observeCompletedCount()

    override suspend fun totalSizeBytes(): Long =
        dao.allOnce().sumOf { it.sizeBytes }.coerceAtLeast(0)

    override fun presets(): List<RegionPreset> = RegionPresets.all

    override suspend fun download(preset: RegionPreset, styleUrl: String) =
        downloadController.download(preset, styleUrl)

    override suspend fun delete(region: OfflineRegion) = downloadController.delete(region.id)

    override suspend fun refreshSizes() = downloadController.refreshSizes()
}
