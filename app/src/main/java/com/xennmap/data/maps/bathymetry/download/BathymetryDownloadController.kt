package com.xennmap.data.maps.bathymetry.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.xennmap.data.maps.bathymetry.model.BathymetryRegion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Controller for bathymetry downloads using WorkManager.
 * Provides progress updates and manages download lifecycle.
 */
@Singleton
class BathymetryDownloadController @Inject constructor(
    private val context: Context,
    @Named("bathymetry_download") private val scope: CoroutineScope,
) {

    private val downloadStates = mutableMapOf<String, MutableStateFlow<DownloadState>>()
    private val progressChannels = mutableMapOf<String, Channel<WorkProgress>>()

    /**
     * Start or resume download for a region.
     */
    fun download(region: BathymetryRegion, wifiOnly: Boolean = false) {
        val existingState = downloadStates[region.id]?.value
        if (existingState is DownloadState.Downloading || existingState is DownloadState.Processing) {
            return // Already in progress
        }

        val request = OneTimeWorkRequestBuilder<BathymetryDownloadWorker>()
            .setInputData(workDataOf(
                BathymetryDownloadWorker.KEY_REGION_ID to region.id as Any?,
                BathymetryDownloadWorker.KEY_DOWNLOAD_URL to region.mbtilesUrl as Any?,
                BathymetryDownloadWorker.KEY_GRID_DOWNLOAD_URL to (region.gridUrl ?: "") as Any?,
                BathymetryDownloadWorker.KEY_WIFI_ONLY to wifiOnly as Any?,
            ))
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build())
            .build()

        // Create state flow for this download
        val stateFlow = MutableStateFlow<DownloadState>(DownloadState.Queued)
        downloadStates[region.id] = stateFlow
        val channel = Channel<WorkProgress>(10)
        progressChannels[region.id] = channel

        // Observe WorkManager progress
        scope.launch {
            WorkManager.getInstance(context)
                .getWorkInfoByIdLiveData(request.id)
                .observeForever { workInfo ->
                    if (workInfo != null) {
                        val progress = workInfo.progress
                        val percent = progress.getInt("percent", 0)
                        val bytes = progress.getLong("bytes", 0)
                        val status = progress.getString("status") ?: ""
                        when (workInfo.state) {
                            androidx.work.WorkInfo.State.ENQUEUED -> {
                                stateFlow.value = DownloadState.Queued
                            }
                            androidx.work.WorkInfo.State.RUNNING -> {
                                stateFlow.value = DownloadState.Downloading(percent, bytes, status)
                            }
                            androidx.work.WorkInfo.State.SUCCEEDED -> {
                                val size = workInfo.outputData.getLong(BathymetryDownloadWorker.KEY_SIZE_BYTES, 0)
                                stateFlow.value = DownloadState.Completed(size)
                                channel.close()
                            }
                            androidx.work.WorkInfo.State.FAILED -> {
                                stateFlow.value = DownloadState.Failed(workInfo.outputData.getString("error") ?: "Download failed")
                                channel.close()
                            }
                            androidx.work.WorkInfo.State.CANCELLED -> {
                                stateFlow.value = DownloadState.Cancelled
                                channel.close()
                            }
                            androidx.work.WorkInfo.State.BLOCKED -> {
                                stateFlow.value = DownloadState.Blocked("Waiting for network...")
                            }
                        }
                    }
                }
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            "bathymetry_${region.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Cancel an in-progress download.
     */
    fun cancel(regionId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("bathymetry_$regionId")
        progressChannels[regionId]?.close()
        downloadStates[regionId]?.value = DownloadState.Cancelled
    }

    /**
     * Retry a failed download.
     */
    fun retry(region: BathymetryRegion, wifiOnly: Boolean = false) {
        cancel(region.id)
        download(region, wifiOnly)
    }

    /**
     * Get download state for a region.
     */
    fun getState(regionId: String): DownloadState {
        return downloadStates[regionId]?.value ?: DownloadState.NotDownloaded
    }

    /**
     * Observe download state for a region.
     */
    fun observeState(regionId: String) = downloadStates.getOrPut(regionId) {
        MutableStateFlow(DownloadState.NotDownloaded)
    }.asStateFlow()

    /**
     * Check if region is downloaded.
     */
    fun isDownloaded(regionId: String): Boolean {
        val state = getState(regionId)
        return state is DownloadState.Completed
    }

    /**
     * Delete downloaded data for a region.
     */
    fun delete(regionId: String) {
        cancel(regionId)
        val mbtilesDir = File(context.filesDir, "bathymetry/mbtiles")
        val gridDir = File(context.filesDir, "bathymetry/gebco2024_tiles")
        File(mbtilesDir, "$regionId.mbtiles").delete()
        File(gridDir, "$regionId.zip").delete()
        File(gridDir, regionId).deleteRecursively()
        downloadStates[regionId]?.value = DownloadState.NotDownloaded
    }

    sealed interface DownloadState {
        object Queued : DownloadState
        data class Downloading(val percent: Int, val bytesDownloaded: Long, val status: String) : DownloadState
        data class Processing(val percent: Int, val status: String) : DownloadState
        data class Completed(val sizeBytes: Long) : DownloadState
        data class Failed(val error: String) : DownloadState
        object Cancelled : DownloadState
        data class Blocked(val reason: String) : DownloadState
        object NotDownloaded : DownloadState
    }
}