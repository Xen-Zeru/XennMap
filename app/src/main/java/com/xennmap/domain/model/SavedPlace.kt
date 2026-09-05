package com.xennmap.domain.model

/** A place the user saved locally: fishing spot, dock, danger, etc. */
data class SavedPlace(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory = PlaceCategory.OTHER,
    val note: String = "",
    val isFavorite: Boolean = false,
    /** Pinned to the Dashboard's notify list (planned destination to keep an eye on). */
    val isNotify: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class PlaceCategory(val label: String) {
    FISHING_SPOT("Fishing Spot"),
    HOME_PORT("Home Port"),
    DOCK("Dock"),
    DANGER("Danger"),
    WAYPOINT("Waypoint"),
    FAVORITE("Favorite"),
    OTHER("Other"),
}

/** Filter chips on the Locations screen. */
enum class LocationFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    FISHING_SPOTS("Fishing Spots"),
    WAYPOINTS("Waypoints"),
}
