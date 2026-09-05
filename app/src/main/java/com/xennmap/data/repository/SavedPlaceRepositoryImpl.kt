package com.xennmap.data.repository

import com.xennmap.data.local.dao.SavedPlaceDao
import com.xennmap.data.local.toDomain
import com.xennmap.data.local.toEntity
import com.xennmap.domain.model.LocationFilter
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.domain.model.SavedPlace
import com.xennmap.domain.repository.SavedPlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedPlaceRepositoryImpl @Inject constructor(
    private val dao: SavedPlaceDao,
) : SavedPlaceRepository {

    override fun observePlaces(): Flow<List<SavedPlace>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeNotifyPlaces(): Flow<List<SavedPlace>> =
        dao.observeNotifyPlaces().map { rows -> rows.map { it.toDomain() } }

    override fun observePlace(id: Long): Flow<SavedPlace?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun save(place: SavedPlace): Long {
        val now = System.currentTimeMillis()
        return dao.upsert(
            place.copy(
                createdAt = if (place.createdAt == 0L) now else place.createdAt,
                updatedAt = now,
            ).toEntity()
        )
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        val current = dao.byId(id) ?: return
        if (current.isFavorite != favorite) dao.toggleFavorite(id, System.currentTimeMillis())
    }

    override suspend fun setNotify(id: Long, notify: Boolean) {
        val current = dao.byId(id) ?: return
        if (current.isNotify != notify) dao.setNotify(id, notify, System.currentTimeMillis())
    }

    override suspend fun seedDemoPlaces() {
        val existing = dao.observeAll().first()
        if (existing.isNotEmpty()) return
        val now = System.currentTimeMillis()
        listOf(
            SavedPlace(
                name = "My Fishing Spot",
                latitude = 11.1875,
                longitude = 123.9532,
                category = PlaceCategory.FISHING_SPOT,
                note = "Good fishing area",
                isFavorite = true,
                createdAt = now,
                updatedAt = now,
            ),
            SavedPlace(
                name = "Port of Bantayan",
                latitude = 11.1684,
                longitude = 123.7142,
                category = PlaceCategory.DOCK,
                note = "Public pier, fuel station nearby",
                createdAt = now,
                updatedAt = now,
            ),
            SavedPlace(
                name = "Home Dock",
                latitude = 11.2011,
                longitude = 123.9672,
                category = PlaceCategory.HOME_PORT,
                note = "",
                isFavorite = true,
                createdAt = now,
                updatedAt = now,
            ),
            SavedPlace(
                name = "Shallow Reef (caution)",
                latitude = 11.3150,
                longitude = 123.7480,
                category = PlaceCategory.DANGER,
                note = "Barely covered at low tide",
                createdAt = now,
                updatedAt = now,
            ),
        ).forEach { dao.upsert(it.toEntity()) }
    }

    override fun filter(places: List<SavedPlace>, query: String, filter: LocationFilter): List<SavedPlace> {
        val filtered = when (filter) {
            LocationFilter.ALL -> places
            LocationFilter.FAVORITES -> places.filter { it.isFavorite }
            LocationFilter.RECENT -> places.sortedByDescending { it.updatedAt }.take(10)
            LocationFilter.FISHING_SPOTS -> places.filter { it.category == PlaceCategory.FISHING_SPOT }
            LocationFilter.WAYPOINTS -> places.filter { it.category == PlaceCategory.WAYPOINT }
        }
        val q = query.trim()
        if (q.isEmpty()) return filtered
        return filtered.filter {
            it.name.contains(q, ignoreCase = true) || it.note.contains(q, ignoreCase = true)
        }
    }

    override fun categoryOf(place: SavedPlace): PlaceCategory = place.category
}
