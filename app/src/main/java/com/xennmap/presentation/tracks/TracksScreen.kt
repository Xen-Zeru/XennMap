package com.xennmap.presentation.tracks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xennmap.data.tracking.TrackingController
import com.xennmap.domain.model.Track
import com.xennmap.ui.components.EmptyState
import com.xennmap.ui.components.StatusChip
import com.xennmap.ui.components.StatusTone
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.FormatUtils

@Composable
fun TracksScreen(
    onGoToMap: () -> Unit,
    vm: TracksViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Tracks", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Record breadcrumb trails of your trips. Everything is stored offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = XennThemeExtended.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        TrackingControlCard(
            tracking = ui.tracking,
            distanceUnit = ui.distanceUnit,
            onStart = vm::startTracking,
            onPause = vm::pauseTracking,
            onResume = vm::resumeTracking,
            onStop = vm::stopTracking,
        )

        Spacer(Modifier.height(16.dp))
        Text("History", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (ui.tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Route,
                title = "No trips recorded yet",
                message = "Tap Start Tracking before you leave the dock — XennMap will draw your trail on the map.",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(ui.tracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        distanceUnit = ui.distanceUnit,
                        onViewOnMap = {
                            vm.viewOnMap(track)
                            onGoToMap()
                        },
                        onDelete = { vm.deleteTrack(track) },
                    )
                }
            }
        }
    }

    SnackbarHost(hostState = snackbar)
}

@Composable
private fun TrackingControlCard(
    tracking: TrackingController.State,
    distanceUnit: com.xennmap.domain.model.DistanceUnit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (val state = tracking) {
                is TrackingController.State.Recording -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recording trip", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusChip(
                            label = if (state.isPaused) "Paused" else "Recording",
                            tone = if (state.isPaused) StatusTone.WARNING else StatusTone.SAFE,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column {
                            Text(FormatUtils.distance(state.distanceMeters, distanceUnit), style = MaterialTheme.typography.titleMedium)
                            Text("Distance", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                        }
                        Column {
                            Text(FormatUtils.duration(System.currentTimeMillis() - state.startedAt), style = MaterialTheme.typography.titleMedium)
                            Text("Duration", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                        }
                        Column {
                            Text("${state.pointCount}", style = MaterialTheme.typography.titleMedium)
                            Text("Points", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (state.isPaused) onResume() else onPause() }) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isPaused) "Resume" else "Pause")
                        }
                        OutlinedButton(onClick = onStop) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                }
                TrackingController.State.Idle -> {
                    Text("Ready to record", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your trail is drawn behind your position marker while tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onStart) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start Tracking")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    distanceUnit: com.xennmap.domain.model.DistanceUnit,
    onViewOnMap: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        FormatUtils.shortDate(track.startedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete ${track.name}",
                        tint = XennThemeExtended.colors.danger,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    Text(FormatUtils.distance(track.distanceMeters, distanceUnit), style = MaterialTheme.typography.bodyLarge)
                    Text("Distance", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                }
                Column {
                    Text(FormatUtils.duration((track.endedAt - track.startedAt).coerceAtLeast(0)), style = MaterialTheme.typography.bodyLarge)
                    Text("Duration", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                }
                Column {
                    Text("${track.pointCount}", style = MaterialTheme.typography.bodyLarge)
                    Text("Points", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onViewOnMap) {
                Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("View on Map")
            }
        }
    }
}
