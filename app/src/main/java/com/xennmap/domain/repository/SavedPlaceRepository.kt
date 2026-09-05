package com.xennmap.domain.repository

import com.xennmap.domain.model.LocationFilter
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.domain.model.SavedPlace
import kotlinx.coroutines.flow.Flow

/** Local storage of user-saved places. Data never leaves the device. */
interface SavedPlaceRepository {

    fun observePlaces(): Flow<List<SavedPlace>>

    /** Places pinned to the Dashboard notify list. */
    fun observeNotifyPlaces(): Flow<List<SavedPlace>>

    fun observePlace(id: Long): Flow<SavedPlace?>

    fun observeCount(): Flow<Int>

    suspend fun save(place: SavedPlace): Long

    suspend fun delete(id: Long)

    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Pins/unpins a place on the Dashboard notify list. */
    suspend fun setNotify(id: Long, notify: Boolean)

    suspend fun seedDemoPlaces()

    fun filter(places: List<SavedPlace>, query: String, filter: LocationFilter): List<SavedPlace>

    fun categoryOf(place: SavedPlace): PlaceCategory
}
