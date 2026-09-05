package com.xennmap.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xennmap.data.local.dao.UserLocationDao
import com.xennmap.domain.repository.OfflineRegionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Light housekeeping: trims the private position history and refreshes
 * offline-pack sizes. Runs periodically via WorkManager.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val userLocationDao: UserLocationDao,
    private val offlineRegionRepository: OfflineRegionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching {
            userLocationDao.trimOlderThan(System.currentTimeMillis() - HISTORY_RETENTION_MS)
        }
        runCatching { offlineRegionRepository.refreshSizes() }
        return Result.success()
    }

    companion object {
        private const val HISTORY_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        const val UNIQUE_NAME = "xenn-maintenance"

        fun periodicRequest(): androidx.work.PeriodicWorkRequest =
            androidx.work.PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS)
                .build()
    }
}
