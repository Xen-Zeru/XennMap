package com.xennmap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xennmap.data.local.dao.OfflineRegionDao
import com.xennmap.data.local.dao.SavedPlaceDao
import com.xennmap.data.local.dao.TrackDao
import com.xennmap.data.local.dao.UserLocationDao
import com.xennmap.data.local.entity.OfflineRegionEntity
import com.xennmap.data.local.entity.SavedPlaceEntity
import com.xennmap.data.local.entity.TrackEntity
import com.xennmap.data.local.entity.TrackPointEntity
import com.xennmap.data.local.entity.UserLocationEntity

@Database(
    entities = [
        SavedPlaceEntity::class,
        TrackEntity::class,
        TrackPointEntity::class,
        OfflineRegionEntity::class,
        UserLocationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class XennDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun trackDao(): TrackDao
    abstract fun offlineRegionDao(): OfflineRegionDao
    abstract fun userLocationDao(): UserLocationDao

    companion object {
        /** v2: adds the Dashboard notify flag to saved places (existing data preserved). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_places ADD COLUMN isNotify INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
