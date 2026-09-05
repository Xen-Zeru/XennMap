package com.xennmap.di

import android.content.Context
import android.location.LocationManager
import com.xennmap.data.location.AospLocationDataSource
import com.xennmap.data.location.FusedLocationDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationDataSource(@ApplicationContext context: Context): FusedLocationDataSource =
        FusedLocationDataSource(context)

    @Provides
    @Singleton
    fun provideAospLocationDataSource(@ApplicationContext context: Context): AospLocationDataSource =
        AospLocationDataSource(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
}
