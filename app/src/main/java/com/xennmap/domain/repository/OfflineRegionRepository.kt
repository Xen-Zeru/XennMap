package com.xennmap.domain.repository

import com.xennmap.domain.model.OfflineRegion
import com.xennmap.domain.model.RegionPreset
import kotlinx.coroutines.flow.Flow

/** Offline map area metadata + download orchestration. */
interface OfflineRegionRepository {

    fun observeRegions(): Flow<List<OfflineRegion>>

    /** Regions that finished downloading and are available offline. */
    val downloadedBounds: kotlinx.coroutines.flow.StateFlow<List<OfflineRegion>>

    fun observeCompletedCount(): Flow<Int>

    suspend fun totalSizeBytes(): Long

    fun presets(): List<RegionPreset>

    suspend fun download(preset: RegionPreset, styleUrl: String)

    suspend fun delete(region: OfflineRegion)

    suspend fun refreshSizes()
}
