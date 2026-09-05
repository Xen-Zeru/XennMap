package com.xennmap.presentation.common

import com.xennmap.domain.model.RegionPreset
import com.xennmap.domain.model.SavedPlace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot commands that drive the map camera/layers from other screens. */
sealed interface MapCommand {
    data class FlyToPlace(val placeId: Long, val place: SavedPlace) : MapCommand
    data class FlyTo(val latitude: Double, val longitude: Double, val zoom: Double? = null) : MapCommand
    data class ShowTrack(val trackId: Long?) : MapCommand
    data class ShowPresetArea(val preset: RegionPreset) : MapCommand
    data object CenterOnMe : MapCommand
}

@Singleton
class MapCommandBus @Inject constructor() {

    // CONFLATED channel: commands sent while the Map tab is not collecting
    // (e.g. from Locations or Offline) are held and delivered when it returns.
    private val _commands = Channel<MapCommand>(Channel.CONFLATED)
    val commands: Flow<MapCommand> = _commands.receiveAsFlow()

    fun send(command: MapCommand) {
        _commands.trySend(command)
    }
}
