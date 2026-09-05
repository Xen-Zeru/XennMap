package com.xennmap.presentation.offline

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.xennmap.domain.model.OfflineRegion
import com.xennmap.domain.model.RegionPreset
import com.xennmap.domain.model.RegionStatus
import com.xennmap.ui.components.ConfirmDialog
import com.xennmap.ui.components.EmptyState
import com.xennmap.ui.components.StatusChip
import com.xennmap.ui.components.StatusTone
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.FormatUtils

@Composable
fun OfflineMapsScreen(
    onClose: () -> Unit,
    onOpenMap: () -> Unit,
    vm: OfflineViewModel = hiltViewModel(),
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
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Offline Maps", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
        }
        Text(
            "Downloaded areas work with zero signal at sea.",
            style = MaterialTheme.typography.bodyMedium,
            color = XennThemeExtended.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.OfflineBolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${FormatUtils.bytes(ui.totalBytes)} used by offline maps",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Free storage: ${FormatUtils.bytes(ui.freeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.setPresetsSheet(true) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Download New Area")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            vm.startCustomPicker()
            onOpenMap()
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Custom Area — frame it on the map")
        }
        Spacer(Modifier.height(14.dp))

        Text("Downloaded areas", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (ui.regions.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Download,
                title = "No offline areas yet",
                message = "Before you sail out, download your home waters so the map works without signal.",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(ui.regions, key = { it.id }) { region ->
                    RegionCard(
                        region = region,
                        onAdjust = {
                            vm.beginAdjust(region)
                            onOpenMap()
                        },
                        onDelete = { vm.requestDelete(region) },
                        onRetry = { vm.retry(region) },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Offline areas contain OpenStreetMap-based tiles. Data © OpenStreetMap contributors (ODbL). Downloads need internet once — viewing them never does.",
            style = MaterialTheme.typography.bodySmall,
            color = XennThemeExtended.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
    }

    SnackbarHost(hostState = snackbar)

    if (ui.presetsSheetVisible) {
        PresetsSheet(
            presets = ui.presets,
            onDownload = { vm.download(it) },
            onShowOnMap = { preset ->
                vm.showPresetArea(preset)
                vm.setPresetsSheet(false)
                onOpenMap()
            },
            onDismiss = { vm.setPresetsSheet(false) },
        )
    }

    ui.deleteCandidate?.let { candidate ->
        ConfirmDialog(
            title = "Delete offline area?",
            message = "\"${candidate.name}\" (${FormatUtils.bytes(candidate.sizeBytes)}) will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = { vm.confirmDelete() },
            onDismiss = { vm.dismissDelete() },
        )
    }
}

@Composable
private fun RegionCard(
    region: OfflineRegion,
    onAdjust: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(region.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (region.status) {
                            RegionStatus.COMPLETED -> FormatUtils.bytes(region.sizeBytes)
                            RegionStatus.DOWNLOADING -> "${region.progress}% · ${FormatUtils.bytes(region.sizeBytes)}"
                            RegionStatus.QUEUED -> "Queued"
                            RegionStatus.FAILED -> region.lastError ?: "Download failed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                when (region.status) {
                    RegionStatus.COMPLETED -> StatusChip(label = "Available Offline", tone = StatusTone.SAFE)
                    RegionStatus.DOWNLOADING -> StatusChip(label = "Downloading ${region.progress}%", tone = StatusTone.WARNING)
                    RegionStatus.QUEUED -> StatusChip(label = "Queued", tone = StatusTone.NEUTRAL)
                    RegionStatus.FAILED -> StatusChip(label = "Failed", tone = StatusTone.DANGER)
                }
                if (region.status == RegionStatus.COMPLETED) {
                    IconButton(onClick = onAdjust) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Adjust ${region.name}",
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete ${region.name}",
                            tint = XennThemeExtended.colors.danger,
                        )
                    }
                }
            }
            if (region.status == RegionStatus.DOWNLOADING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { region.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (region.status == RegionStatus.FAILED) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Try again")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsSheet(
    presets: List<RegionPreset>,
    onDownload: (RegionPreset) -> Unit,
    onShowOnMap: (RegionPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedId by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Choose an area", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tap a coastal area to see where it is. Downloading uses mobile data or Wi-Fi — viewing it later never does.",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 520.dp),
            ) {
                items(presets, key = { it.id }) { preset ->
                    val expanded = expandedId == preset.id
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (expanded) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedId = if (expanded) null else preset.id },
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                    if (!expanded) {
                                        Text(
                                            "≈ ${preset.estimatedSizeMb} MB · zoom ${preset.minZoom}–${preset.maxZoom}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = XennThemeExtended.colors.textSecondary,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { onDownload(preset) }) {
                                    Text("Download")
                                }
                            }
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = XennThemeExtended.colors.textSecondary,
                                )
                                Text(
                                    "Located: " +
                                        com.xennmap.utils.GeoUtils.formatLatitude(preset.south) + " to " +
                                        com.xennmap.utils.GeoUtils.formatLatitude(preset.north) + " · " +
                                        com.xennmap.utils.GeoUtils.formatLongitude(preset.west) + " to " +
                                        com.xennmap.utils.GeoUtils.formatLongitude(preset.east),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = XennThemeExtended.colors.textSecondary,
                                )
                                Text(
                                    "≈ ${preset.estimatedSizeMb} MB · zoom ${preset.minZoom}–${preset.maxZoom}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = XennThemeExtended.colors.textSecondary,
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onShowOnMap(preset) }) {
                                        Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Show on map")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = XennThemeExtended.colors.warning,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Keep downloads under your device's free storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = XennThemeExtended.colors.textSecondary,
                )
            }
        }
    }
}
