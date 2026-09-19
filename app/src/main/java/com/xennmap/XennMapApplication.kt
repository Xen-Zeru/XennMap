package com.xennmap

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.xennmap.data.maps.bathymetry.source.BathymetryAssetProvisioner
import com.xennmap.data.work.MaintenanceWorker
import com.xennmap.di.ApplicationScope
import com.xennmap.domain.repository.SavedPlaceRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

        // Provision GEBCO bathymetry assets on first launch (asynchronous)
        appScope.launch(Dispatchers.IO) {
            Log.i("XennMap", "Starting GEBCO asset provisioning...")
            val provisioner = EntryPointAccessors.fromApplication(
                this@XennMapApplication,
                ProvisionerEntryPoint::class.java
            ).provisioner()
            val provisioned = provisioner.provision()
            Log.i("XennMap", "GEBCO asset provisioning: $provisioned")
        }

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

    @dagger.hilt.EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProvisionerEntryPoint {
        fun provisioner(): BathymetryAssetProvisioner
    }
}
