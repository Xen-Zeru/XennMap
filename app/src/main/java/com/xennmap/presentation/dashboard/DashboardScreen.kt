package com.xennmap.presentation.dashboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xennmap.ui.components.ConfirmDialog
import com.xennmap.ui.components.StatusChip
import com.xennmap.ui.components.StatusTone
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.FormatUtils
import com.xennmap.utils.GeoUtils

@Composable
fun DashboardScreen(
    onOpenMap: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenTracks: () -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val notifyItems by vm.notifyItems.collectAsStateWithLifecycle()
    var deleteCandidate by remember { mutableStateOf<com.xennmap.domain.model.SavedPlace?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Anchor,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(FormatUtils.greeting(), style = MaterialTheme.typography.bodyMedium, color = XennThemeExtended.colors.textSecondary)
                Text("XennMap", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text(
            "Your Offline Sea Map Companion",
            style = MaterialTheme.typography.bodyMedium,
            color = XennThemeExtended.colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Current GPS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusChip(
                        label = when {
                            ui.gps.fix == null -> "Searching"
                            else -> "Connected"
                        },
                        tone = if (ui.gps.fix != null) StatusTone.SAFE else StatusTone.WARNING,
                    )
                }
                val fix = ui.gps.fix
                if (fix != null) {
                    Text(
                        "${GeoUtils.formatLatitude(fix.latitude)}  ${GeoUtils.formatLongitude(fix.longitude)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text(
                        "Waiting for a position fix…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = "${ui.regionCount}",
                label = "Offline regions",
                icon = Icons.Rounded.Download,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${ui.placeCount}",
                label = "Saved locations",
                icon = Icons.Rounded.Place,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = ui.lastTrip?.let { FormatUtils.distance(it.distanceMeters, ui.distanceUnit) } ?: "—",
                label = "Last trip",
                icon = Icons.Rounded.Route,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenMap,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("OPEN MAP", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenLocations, modifier = Modifier.weight(1f)) {
                Text("Locations")
            }
            OutlinedButton(onClick = onOpenOffline, modifier = Modifier.weight(1f)) {
                Text("Offline Maps")
            }
            OutlinedButton(onClick = onOpenTracks, modifier = Modifier.weight(1f)) {
                Text("Tracks")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "XennMap is navigation assistance only. It does not replace official nautical charts or safety equipment.",
            style = MaterialTheme.typography.bodySmall,
            color = XennThemeExtended.colors.textSecondary,
        )

        // ------------------------------------------------- notify locations
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("NOTIFY LOCATIONS", style = MaterialTheme.typography.labelLarge, color = XennThemeExtended.colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                "${notifyItems.size}",
                style = MaterialTheme.typography.labelLarge,
                color = XennThemeExtended.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (notifyItems.isEmpty()) {
            Text(
                "Nothing to notify yet. Open the Navigate tab and tap Notify on a saved place to pin it here.",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
        } else {
            notifyItems.forEach { item ->
                NotifyCard(
                    item = item,
                    distanceUnit = ui.distanceUnit,
                    onView = {
                        vm.viewOnMap(item.place)
                        onOpenMap()
                    },
                    onRemoveNotify = { vm.removeNotify(item.place) },
                    onDelete = { deleteCandidate = item.place },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    deleteCandidate?.let { candidate ->
        ConfirmDialog(
            title = "Delete saved place?",
            message = "\"${candidate.name}\" will be permanently removed from this device, including from the notify list. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { vm.deletePlace(candidate) },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@Composable
private fun NotifyCard(
    item: com.xennmap.presentation.dashboard.DashboardViewModel.NotifyItem,
    distanceUnit: com.xennmap.domain.model.DistanceUnit,
    onView: () -> Unit,
    onRemoveNotify: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(item.place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemoveNotify, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.NotificationsOff,
                        contentDescription = "Remove ${item.place.name} from notify list",
                        tint = XennThemeExtended.colors.textSecondary,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete ${item.place.name}",
                        tint = XennThemeExtended.colors.danger,
                    )
                }
            }
            Text(
                "${item.place.category.label} · ${GeoUtils.formatLatitude(item.place.latitude)}, ${GeoUtils.formatLongitude(item.place.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            item.distanceMeters?.let { distance ->
                Text(
                    FormatUtils.distance(distance, distanceUnit) + " from you",
                    style = MaterialTheme.typography.bodySmall,
                    color = XennThemeExtended.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onView) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("View")
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
        }
    }
}
