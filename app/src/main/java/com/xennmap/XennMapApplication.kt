package com.xennmap

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.xennmap.data.work.MaintenanceWorker
import com.xennmap.di.ApplicationScope
import com.xennmap.domain.repository.SavedPlaceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class XennMapApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var savedPlaceRepository: SavedPlaceRepository

    @ApplicationScope
    @Inject lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialised before any MapView is inflated.
        MapLibre.getInstance(this)

        appScope.launch { runCatching { savedPlaceRepository.seedDemoPlaces() } }

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MaintenanceWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            MaintenanceWorker.periodicRequest(),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
