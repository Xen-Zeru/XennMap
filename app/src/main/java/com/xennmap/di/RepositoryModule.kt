package com.xennmap.di

import com.xennmap.data.location.LocationRepositoryImpl
import com.xennmap.data.maps.BathymetryRepositoryImpl
import com.xennmap.data.preferences.PreferencesRepositoryImpl
import com.xennmap.data.repository.OfflineRegionRepositoryImpl
import com.xennmap.data.repository.SavedPlaceRepositoryImpl
import com.xennmap.data.repository.TrackRepositoryImpl
import com.xennmap.domain.repository.BathymetryRepository
import com.xennmap.domain.repository.LocationRepository
import com.xennmap.domain.repository.OfflineRegionRepository
import com.xennmap.domain.repository.PreferencesRepository
import com.xennmap.domain.repository.SavedPlaceRepository
import com.xennmap.domain.repository.TrackRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSavedPlaceRepository(impl: SavedPlaceRepositoryImpl): SavedPlaceRepository

    @Binds
    @Singleton
    abstract fun bindTrackRepository(impl: TrackRepositoryImpl): TrackRepository

    @Binds
    @Singleton
    abstract fun bindOfflineRegionRepository(impl: OfflineRegionRepositoryImpl): OfflineRegionRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindBathymetryRepository(impl: BathymetryRepositoryImpl): BathymetryRepository
}
