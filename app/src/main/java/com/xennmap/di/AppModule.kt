package com.xennmap.di

import android.content.Context
import androidx.room.Room
import com.xennmap.data.local.XennDatabase
import com.xennmap.data.local.dao.OfflineRegionDao
import com.xennmap.data.local.dao.SavedPlaceDao
import com.xennmap.data.local.dao.TrackDao
import com.xennmap.data.local.dao.UserLocationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): XennDatabase =
        Room.databaseBuilder(context, XennDatabase::class.java, "xennmap.db")
            .addMigrations(XennDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSavedPlaceDao(db: XennDatabase): SavedPlaceDao = db.savedPlaceDao()

    @Provides
    fun provideTrackDao(db: XennDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideOfflineRegionDao(db: XennDatabase): OfflineRegionDao = db.offlineRegionDao()

    @Provides
    fun provideUserLocationDao(db: XennDatabase): UserLocationDao = db.userLocationDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
