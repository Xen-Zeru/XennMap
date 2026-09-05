package com.xennmap.presentation.locations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xennmap.domain.model.LocationFilter
import com.xennmap.domain.model.SavedPlace
import com.xennmap.ui.components.ConfirmDialog
import com.xennmap.ui.components.EmptyState
import com.xennmap.ui.components.PlaceEditorSheet
import com.xennmap.ui.components.categoryColor
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.FormatUtils
import com.xennmap.utils.GeoUtils

@Composable
fun LocationsScreen(
    onGoToMap: () -> Unit,
    vm: LocationsViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.openMarkSheet() },
                icon = { Icon(Icons.Rounded.PinDrop, contentDescription = null) },
                text = { Text("Mark Location") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("Locations", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${ui.places.size} saved on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XennThemeExtended.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search places and notes") },
                    leadingIcon = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(LocationFilter.entries) { candidate ->
                    FilterChip(
                        selected = ui.filter == candidate,
                        onClick = { vm.setFilter(candidate) },
                        label = { Text(candidate.label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (ui.places.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Place,
                    title = "No places here yet",
                    message = "Mark your fishing spots, docks and hazards — they are stored offline on this phone.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 96.dp, top = 6.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ui.places, key = { it.id }) { place ->
                        PlaceCard(
                            place = place,
                            depth = ui.depths[place.id],
                            distanceMeters = ui.distances[place.id],
                            depthUnit = ui.depthUnit,
                            distanceUnit = ui.distanceUnit,
                            onOpenOnMap = {
                                vm.openOnMap(place)
                                onGoToMap()
                            },
                            onNavigate = {
                                vm.startNavigation(place)
                                onGoToMap()
                            },
                            onEdit = { vm.startEdit(place) },
                            onToggleFavorite = { vm.toggleFavorite(place) },
                            onDelete = { vm.requestDelete(place) },
                        )
                    }
                }
            }
        }
    }

    ui.markTarget?.let { target ->
        PlaceEditorSheet(
            latitude = target.latitude,
            longitude = target.longitude,
            title = "Mark Location",
            onSave = { name, category, note -> vm.savePlace(name, category, note) },
            onDismiss = { vm.dismissSheets() },
        )
    }

    ui.editingPlace?.let { editing ->
        PlaceEditorSheet(
            latitude = editing.latitude,
            longitude = editing.longitude,
            initialName = editing.name,
            initialCategory = editing.category,
            initialNote = editing.note,
            title = "Edit Place",
            onSave = { name, category, note -> vm.savePlace(name, category, note) },
            onDismiss = { vm.dismissSheets() },
        )
    }

    ui.deleteCandidate?.let { candidate ->
        ConfirmDialog(
            title = "Delete place?",
            message = "\"${candidate.name}\" will be removed from this device. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { vm.confirmDelete() },
            onDismiss = { vm.dismissDelete() },
        )
    }
}

@Composable
private fun PlaceCard(
    place: SavedPlace,
    depth: com.xennmap.domain.model.ChartDepth?,
    distanceMeters: Double?,
    depthUnit: com.xennmap.domain.model.DepthUnit,
    distanceUnit: com.xennmap.domain.model.DistanceUnit,
    onOpenOnMap: () -> Unit,
    onNavigate: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(categoryColor(place.category), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (place.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (place.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (place.isFavorite) MaterialTheme.colorScheme.secondary else XennThemeExtended.colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${GeoUtils.formatLatitude(place.latitude)}, ${GeoUtils.formatLongitude(place.longitude)}",
                style = MaterialTheme.typography.bodyMedium,
                color = XennThemeExtended.colors.textSecondary,
            )
            depth?.let { d ->
                Text(
                    "Depth: ~" + FormatUtils.depth(d.meters, depthUnit) +
                        if (d.downloaded) " (downloaded)" else " (chart)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            distanceMeters?.let { distance ->
                Text(
                    "${FormatUtils.distance(distance, distanceUnit)} from you",
                    style = MaterialTheme.typography.bodySmall,
                    color = XennThemeExtended.colors.textSecondary,
                )
            }
            if (place.note.isNotBlank()) {
                Text(
                    place.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = XennThemeExtended.colors.textSecondary,
                    maxLines = 2,
                )
            }
            Text(
                "${place.category.label} · Saved ${FormatUtils.clockTime(place.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onOpenOnMap) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Map")
                }
                OutlinedButton(onClick = onNavigate) {
                    Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate")
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${place.name}", tint = XennThemeExtended.colors.textSecondary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete ${place.name}",
                        tint = XennThemeExtended.colors.danger,
                    )
                }
            }
        }
    }
}
