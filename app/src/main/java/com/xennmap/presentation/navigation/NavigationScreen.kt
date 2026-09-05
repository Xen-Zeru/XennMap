package com.xennmap.presentation.navigation

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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xennmap.domain.model.NavPhase
import com.xennmap.domain.model.SavedPlace
import com.xennmap.ui.components.EmptyState
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.FormatUtils
import com.xennmap.utils.GeoUtils

@Composable
fun NavigationScreen(
    onGoToMap: () -> Unit,
    vm: NavigationViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val selectedDepth by vm.selectedDepth.collectAsStateWithLifecycle()
    val sessionDepth by vm.sessionDestinationDepth.collectAsStateWithLifecycle()
    val notifyIds by vm.notifyIds.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Navigation", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick a saved place and follow the bearing. Assistance only.",
            style = MaterialTheme.typography.bodyMedium,
            color = XennThemeExtended.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = XennThemeExtended.colors.warning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "This is steering assistance. It does not replace official nautical charts or safe-watch practices.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = ui.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search saved places") },
            leadingIcon = { Icon(Icons.Rounded.Place, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        val session = ui.session
        if (session.isRunning && session.destination != null) {
            ActiveSessionCard(
                viewModel = vm,
                destinationDepth = sessionDepth,
                isNotify = session.destination?.let { dest -> notifyIds.contains(dest.id) } == true,
                onGoToMap = onGoToMap,
            )
            Spacer(Modifier.height(12.dp))
        }

        Text("Choose destination", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (ui.places.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Place,
                title = "No saved places found",
                message = "Save places from the map or Locations tab, then navigate back to them here.",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(ui.places, key = { it.id }) { place ->
                    DestinationCard(
                        place = place,
                        selected = ui.selected?.id == place.id,
                        preview = if (ui.selected?.id == place.id) ui.preview else null,
                        depthMeters = if (ui.selected?.id == place.id) selectedDepth else null,
                        distanceUnit = ui.settings.distanceUnit,
                        isNotify = place.id in notifyIds,
                        onToggleNotify = { vm.toggleNotify(place) },
                        onSelect = { vm.select(place) },
                        onStart = {
                            vm.startNavigation(place)
                            onGoToMap()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(
    place: SavedPlace,
    selected: Boolean,
    preview: com.xennmap.domain.model.NavigationPlan?,
    distanceUnit: com.xennmap.domain.model.DistanceUnit,
    depthMeters: Double?,
    isNotify: Boolean,
    onToggleNotify: () -> Unit,
    onSelect: () -> Unit,
    onStart: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (place.isFavorite) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                "${GeoUtils.formatLatitude(place.latitude)}, ${GeoUtils.formatLongitude(place.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            if (selected) {
                Spacer(Modifier.height(8.dp))
                if (preview != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        InfoCell(FormatUtils.distance(preview.distanceMeters, distanceUnit), "Distance")
                        InfoCell("${preview.etaMinutes} min", "Est. time")
                        InfoCell(GeoUtils.formatBearing(preview.bearingDeg), "Bearing")
                    }
                } else {
                    Text(
                        "Waiting for GPS fix to compute distance…",
                        style = MaterialTheme.typography.bodySmall,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = depthMeters?.let { "Estimated depth: ${FormatUtils.depth(it, com.xennmap.domain.model.DepthUnit.METERS)} (chart)" }
                        ?: "Estimated depth: unavailable (no chart data)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (depthMeters != null) MaterialTheme.colorScheme.secondary else XennThemeExtended.colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onStart, enabled = preview != null, modifier = Modifier.weight(1.4f)) {
                        Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Start", maxLines = 1)
                    }
                    OutlinedButton(onClick = onToggleNotify, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = if (isNotify) MaterialTheme.colorScheme.secondary else LocalContentColor.current,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isNotify) "Notifying" else "Notify", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    viewModel: NavigationViewModel,
    destinationDepth: Double?,
    isNotify: Boolean,
    onGoToMap: () -> Unit,
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val destination = session.destination ?: return
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Heading to ${destination.name}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (session.phase) {
                        NavPhase.ACTIVE -> "Active"
                        NavPhase.PAUSED -> "Paused"
                        NavPhase.IDLE -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = XennThemeExtended.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = destinationDepth?.let { "Estimated depth at destination: ${FormatUtils.depth(it, com.xennmap.domain.model.DepthUnit.METERS)} (chart)" }
                    ?: "Estimated depth at destination: unavailable (no chart data)",
                style = MaterialTheme.typography.bodySmall,
                color = if (destinationDepth != null) MaterialTheme.colorScheme.secondary else XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { if (session.isActive) viewModel.pause() else viewModel.resume() }, modifier = Modifier.weight(1f)) {
                    Text(if (session.isActive) "Pause" else "Resume", maxLines = 1)
                }
                OutlinedButton(onClick = { viewModel.end() }, modifier = Modifier.weight(0.8f)) { Text("End", maxLines = 1) }
                OutlinedButton(onClick = onGoToMap, modifier = Modifier.weight(1f)) { Text("Map", maxLines = 1) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { session.destination?.let { viewModel.toggleNotify(it) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = if (isNotify) MaterialTheme.colorScheme.secondary else LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isNotify) "Notifying — shown on Dashboard" else "Notify me about this destination",
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun InfoCell(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
    }
}
