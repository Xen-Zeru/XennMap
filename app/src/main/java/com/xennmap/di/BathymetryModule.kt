package com.xennmap.di

import android.content.Context
import com.xennmap.data.maps.bathymetry.download.BathymetryDownloadController
import com.xennmap.data.maps.bathymetry.source.BathymetryAssetProvisioner
import com.xennmap.data.maps.bathymetry.source.BathymetryBinaryGridReader
import com.xennmap.data.maps.bathymetry.source.BathymetryMbtilesProvider
import com.xennmap.data.maps.bathymetry.source.BathymetryTileCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BathymetryModule {

    @Provides
    @Singleton
    fun provideBathymetryTileCache(): BathymetryTileCache = BathymetryTileCache()

    @Provides
    @Singleton
    fun provideBathymetryBinaryGridReader(
        @ApplicationContext context: Context,
        tileCache: BathymetryTileCache,
    ): BathymetryBinaryGridReader = BathymetryBinaryGridReader(context)

    @Provides
    @Singleton
    fun provideBathymetryMbtilesProvider(
        @ApplicationContext context: Context,
    ): BathymetryMbtilesProvider = BathymetryMbtilesProvider(context)

    @Provides
    @Singleton
    fun provideBathymetryAssetProvisioner(
        @ApplicationContext context: Context,
    ): BathymetryAssetProvisioner = BathymetryAssetProvisioner(context)

    @Provides
    @Singleton
    @Named("bathymetry_download")
    fun provideBathymetryDownloadScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideBathymetryDownloadController(
        @ApplicationContext context: Context,
        @Named("bathymetry_download") scope: CoroutineScope,
    ): BathymetryDownloadController = BathymetryDownloadController(context, scope)
}