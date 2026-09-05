package com.xennmap.presentation.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xennmap.domain.model.BathymetryData
import com.xennmap.domain.model.ChartDepth
import com.xennmap.presentation.common.DownloadBounds
import com.xennmap.domain.model.DepthUnit
import com.xennmap.domain.model.DistanceUnit
import com.xennmap.domain.model.GpsState
import com.xennmap.domain.model.MapLayers
import com.xennmap.domain.model.NavPhase
import com.xennmap.domain.model.NavigationPlan
import com.xennmap.domain.model.SavedPlace
import com.xennmap.domain.model.SpeedUnit
import com.xennmap.domain.model.TerrainType
import com.xennmap.ui.components.GlassPanel
import com.xennmap.ui.components.StatusChip
import com.xennmap.ui.components.StatusTone
import com.xennmap.ui.components.categoryColor
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.FormatUtils
import com.xennmap.utils.GeoUtils
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToInt

// ------------------------------------------------------------------- top bar

@Composable
fun MapTopBar(
    online: Boolean,
    gps: GpsState,
    onSearch: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    GlassPanel(modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Anchor,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "XennMap",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(10.dp))
            StatusChip(
                label = if (online) "Online" else "Offline",
                tone = if (online) StatusTone.SAFE else StatusTone.WARNING,
            )
            Spacer(Modifier.width(6.dp))
            StatusChip(
                label = gpsLabel(gps),
                tone = gpsTone(gps),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Search locations")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Dashboard") },
                        onClick = { menuOpen = false; onOpenDashboard() },
                    )
                    DropdownMenuItem(
                        text = { Text("Offline maps") },
                        onClick = { menuOpen = false; onOpenOffline() },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { menuOpen = false; onOpenSettings() },
                    )
                }
            }
        }
    }
}

private fun gpsLabel(gps: GpsState): String = when {
    !gps.isStarted -> "GPS Off"
    else -> when (gps.signal) {
        com.xennmap.domain.model.GpsSignalState.NO_FIX -> "No Signal"
        com.xennmap.domain.model.GpsSignalState.ACQUIRING -> "Searching"
        com.xennmap.domain.model.GpsSignalState.GOOD -> "GPS Strong"
        com.xennmap.domain.model.GpsSignalState.FAIR -> "GPS Fair"
        com.xennmap.domain.model.GpsSignalState.POOR -> "GPS Weak"
    }
}

private fun gpsTone(gps: GpsState): StatusTone = when {
    !gps.isStarted -> StatusTone.NEUTRAL
    else -> when (gps.signal) {
        com.xennmap.domain.model.GpsSignalState.NO_FIX -> StatusTone.DANGER
        com.xennmap.domain.model.GpsSignalState.ACQUIRING -> StatusTone.WARNING
        com.xennmap.domain.model.GpsSignalState.GOOD -> StatusTone.SAFE
        com.xennmap.domain.model.GpsSignalState.FAIR -> StatusTone.WARNING
        com.xennmap.domain.model.GpsSignalState.POOR -> StatusTone.DANGER
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun MapControls(
    bearing: Double,
    onLayers: () -> Unit,
    onMyLocation: () -> Unit,
    onCompass: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundControlButton(icon = Icons.Rounded.Layers, description = "Map layers", onClick = onLayers)
        RoundControlButton(icon = Icons.Rounded.MyLocation, description = "Center on me", onClick = onMyLocation)
        RoundControlButton(
            icon = Icons.Rounded.Explore,
            description = "Compass — reset north",
            onClick = onCompass,
            badge = if (bearing % 360.0 == 0.0) null else "${bearing.roundToInt().mod(360)}°",
        )
        RoundControlButton(icon = Icons.Rounded.Add, description = "Zoom in", onClick = onZoomIn)
        RoundControlButton(icon = Icons.Rounded.Remove, description = "Zoom out", onClick = onZoomOut)
    }
}

@Composable
private fun RoundControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    badge: String? = null,
) {
    GlassPanel {
        Box(contentAlignment = Alignment.Center) {
            IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
            }
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------- location card

@Composable
fun LocationCard(
    gps: GpsState,
    distanceUnit: DistanceUnit,
    speedUnit: SpeedUnit,
    depthMeters: Double?,
    depthDownloaded: Boolean,
    depthUnit: DepthUnit,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onMarkLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CURRENT LOCATION",
                    style = MaterialTheme.typography.labelLarge,
                    color = XennThemeExtended.colors.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleCollapse, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        contentDescription = if (collapsed) "Expand location card" else "Collapse location card",
                        tint = XennThemeExtended.colors.textSecondary,
                    )
                }
            }
            val fix = gps.fix
            if (fix == null) {
                Text(
                    text = if (gps.isStarted) "Waiting for GPS fix…" else "Location is off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XennThemeExtended.colors.textSecondary,
                )
            } else {
                if (!collapsed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text(GeoUtils.formatLatitude(fix.latitude), style = MaterialTheme.typography.titleMedium)
                            Text("Latitude", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                        }
                        Column {
                            Text(GeoUtils.formatLongitude(fix.longitude), style = MaterialTheme.typography.titleMedium)
                            Text("Longitude", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column {
                            Text(
                                "${fix.accuracyMeters.roundToInt()} m",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text("Accuracy", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                        }
                        Column {
                            Text(FormatUtils.speed(fix.speedMps, speedUnit), style = MaterialTheme.typography.bodyLarge)
                            Text("Speed", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                        }
                        Column {
                            Text(
                                fix.bearingDeg?.let { GeoUtils.formatBearing(it.toDouble()) } ?: "—",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text("Heading", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                        }
                        gps.satelliteCount?.let { sats ->
                            Column {
                                Text("$sats", style = MaterialTheme.typography.bodyLarge)
                                Text("Satellites", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Live depth under the vessel — sampled from the depth dataset
                    // at the current position as the boat moves. Chart data, not sonar.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Water,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            depthMeters?.let { FormatUtils.depth(it, depthUnit) } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Depth", style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (depthDownloaded) "Downloaded area" else "Chart data",
                                style = MaterialTheme.typography.labelSmall,
                                color = XennThemeExtended.colors.textSecondary,
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${GeoUtils.formatLatitude(fix.latitude)}   ${GeoUtils.formatLongitude(fix.longitude)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        depthMeters?.let { depth ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "· ${FormatUtils.depth(depth, depthUnit)} (chart)",
                                style = MaterialTheme.typography.bodySmall,
                                color = XennThemeExtended.colors.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onMarkLocation, enabled = fix != null) {
                        Icon(Icons.Rounded.NearMe, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mark Location")
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------- selected point

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedPointSheet(
    point: MapViewModel.SelectedPoint,
    depthUnit: DepthUnit,
    onSave: () -> Unit,
    onNavigate: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Selected Location", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "${GeoUtils.formatLatitude(point.latitude)}, ${GeoUtils.formatLongitude(point.longitude)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            when (point.terrain) {
                TerrainType.SEA -> StatusChip(label = "Sea", tone = StatusTone.INFO)
                TerrainType.COASTAL -> StatusChip(label = "Coastal waters", tone = StatusTone.SAFE)
                TerrainType.LAND -> StatusChip(label = "Land", tone = StatusTone.WARNING)
                TerrainType.OUTSIDE -> StatusChip(label = "Outside chart coverage", tone = StatusTone.NEUTRAL)
                null -> StatusChip(label = "Checking chart…", tone = StatusTone.NEUTRAL)
            }
            when {
                point.terrain == TerrainType.LAND || point.terrain == TerrainType.OUTSIDE -> {
                    Text(
                        "Depth unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
                point.depthMeters != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Water,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Estimated Depth: ${FormatUtils.depth(point.depthMeters, depthUnit)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        if (point.inDownloaded) "Downloaded area — not a live measurement"
                        else "Charted depth — not a live measurement",
                        style = MaterialTheme.typography.bodySmall,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
                else -> {
                    Text(
                        "Sampling chart depth…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onSave, modifier = Modifier.weight(1.2f)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", maxLines = 1)
                }
                OutlinedButton(onClick = onNavigate, modifier = Modifier.weight(1.2f)) {
                    Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate", maxLines = 1)
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(0.6f)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = "You can save places anywhere — even far from your boat. Saved locations stay private on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
        }
    }
}

// ------------------------------------------------------ custom area picker

/** Fixed screen-space frame the user sizes by panning/zooming the map. */
@Composable
fun FrameReticle(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.tertiary
    val stroke = 3.dp
    val cornerLen = 26.dp
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cl = cornerLen.toPx()
        val sw = stroke.toPx()
        // top-left
        drawLine(color, Offset(0f, cl), Offset(0f, 0f), sw)
        drawLine(color, Offset(0f, 0f), Offset(cl, 0f), sw)
        // top-right
        drawLine(color, Offset(w - cl, 0f), Offset(w, 0f), sw)
        drawLine(color, Offset(w, 0f), Offset(w, cl), sw)
        // bottom-left
        drawLine(color, Offset(0f, h - cl), Offset(0f, h), sw)
        drawLine(color, Offset(0f, h), Offset(cl, h), sw)
        // bottom-right
        drawLine(color, Offset(w - cl, h), Offset(w, h), sw)
        drawLine(color, Offset(w, h - cl), Offset(w, h), sw)
    }
}

/** Live stats + actions for the framed custom download area. */
@Composable
fun AreaPickerOverlay(
    bounds: DownloadBounds,
    onCancel: () -> Unit,
    onDownload: (DownloadBounds) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estMb = (bounds.areaKm2 * 0.02).toInt().coerceIn(20, 4_000)
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Custom download area", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pan and zoom — the frame on screen is your selection.",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    Text("≈ $estMb MB", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Estimated download", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                }
                Column {
                    Text("${bounds.areaKm2.roundToInt()} km²", style = MaterialTheme.typography.titleMedium)
                    Text("Area", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                }
                Column {
                    Text("6–11", style = MaterialTheme.typography.titleMedium)
                    Text("Zoom range", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Downloading uses mobile data or Wi-Fi. Depth inside the frame will be marked as downloaded.",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onDownload(bounds) }, modifier = Modifier.weight(1.4f)) {
                    Text("Download area", maxLines = 1)
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(0.8f)) {
                    Text("Cancel", maxLines = 1)
                }
            }
        }
    }
}

// ------------------------------------------------------------------- nav card

@Composable
fun NavigationCard(
    plan: NavigationPlan?,
    phase: NavPhase,
    distanceUnit: DistanceUnit,
    speedUnit: SpeedUnit,
    depthUnit: DepthUnit,
    destinationDepth: ChartDepth?,
    liveDepth: ChartDepth?,
    onPauseResume: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = plan?.destination?.name?.uppercase() ?: "NAVIGATION",
                    style = MaterialTheme.typography.labelLarge,
                    color = XennThemeExtended.colors.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                StatusChip(
                    label = when (phase) {
                        NavPhase.ACTIVE -> "Navigating"
                        NavPhase.PAUSED -> "Paused"
                        NavPhase.IDLE -> "Ready"
                    },
                    tone = if (phase == NavPhase.ACTIVE) StatusTone.SAFE else StatusTone.WARNING,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (plan == null) {
                Text(
                    text = "Waiting for GPS fix…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XennThemeExtended.colors.textSecondary,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text(
                            FormatUtils.distance(plan.distanceMeters, distanceUnit),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("Distance", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                    }
                    Column {
                        Text(
                            "${plan.etaMinutes} min",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text("Est. travel time", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(GeoUtils.formatBearing(plan.bearingDeg), style = MaterialTheme.typography.titleMedium)
                        Text("Bearing", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column {
                        Text(
                            "${directionArrow(GeoUtils.compassLabel(plan.bearingDeg))} ${GeoUtils.compassLabel(plan.bearingDeg)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text("Direction", style = MaterialTheme.typography.labelSmall, color = XennThemeExtended.colors.textSecondary)
                    }
                    Column {
                        Text(
                            destinationDepth?.let { FormatUtils.depth(it.meters, depthUnit) } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            if (destinationDepth?.downloaded == true) "Destination (downloaded)" else "Destination (chart)",
                            style = MaterialTheme.typography.labelSmall,
                            color = XennThemeExtended.colors.textSecondary,
                        )
                    }
                    if (liveDepth != null) {
                        Column {
                            Text(
                                FormatUtils.depth(liveDepth.meters, depthUnit),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                if (liveDepth.downloaded) "Here (downloaded)" else "Here (chart)",
                                style = MaterialTheme.typography.labelSmall,
                                color = XennThemeExtended.colors.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onPauseResume) {
                        Icon(
                            imageVector = if (phase == NavPhase.ACTIVE) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (phase == NavPhase.ACTIVE) "Pause" else "Resume")
                    }
                    OutlinedButton(onClick = onEnd) {
                        Text("End")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = XennThemeExtended.colors.warning,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Assistance only — not a substitute for official charts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = XennThemeExtended.colors.textSecondary,
                )
            }
        }
    }
}

private fun directionArrow(label: String): String = when (label) {
    "N" -> "↑"
    "NNE", "NE", "ENE" -> "↗"
    "E" -> "→"
    "ESE", "SE", "SSE" -> "↘"
    "S" -> "↓"
    "SSW", "SW", "WSW" -> "↙"
    "W", "WNW" -> "←"
    else -> "↖"
}

// -------------------------------------------------------------- layers sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    layers: MapLayers,
    bathymetry: BathymetryData?,
    dark: Boolean,
    onToggleBathymetry: (Boolean) -> Unit,
    onToggleContours: (Boolean) -> Unit,
    onToggleDepthLabels: (Boolean) -> Unit,
    onTogglePlaces: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Map layers", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            LayerToggle("Bathymetry", "Depth zones from available chart data", layers.bathymetryEnabled, onToggleBathymetry)
            LayerToggle("Depth contours", "Lines between depth zones", layers.contoursEnabled && layers.bathymetryEnabled, onToggleContours, enabled = layers.bathymetryEnabled)
            LayerToggle("Depth labels", "Small depth markers on the water", layers.depthLabelsEnabled && layers.bathymetryEnabled, onToggleDepthLabels, enabled = layers.bathymetryEnabled)
            LayerToggle("Saved places", "Your pins, docks and fishing spots", layers.savedPlacesVisible, onTogglePlaces)

            Spacer(Modifier.height(8.dp))
            Text("DEPTH", style = MaterialTheme.typography.labelLarge, color = XennThemeExtended.colors.textSecondary)
            DepthLegend(bathymetry = bathymetry, dark = dark)
            Spacer(Modifier.height(4.dp))
            Text(
                text = bathymetry?.dataset?.let {
                    "Source: ${it.source}\nLicense: ${it.license}\nCoverage: ${it.coverageNote}"
                } ?: "Depth data loads shortly after opening the map.",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = XennThemeExtended.colors.warning,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Depth layer shows chart data, not live measurements from your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = XennThemeExtended.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun LayerToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun DepthLegend(
    bathymetry: BathymetryData?,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val ramp = if (dark) com.xennmap.ui.theme.BathyDarkRamp else com.xennmap.ui.theme.BathyLightRamp
    val labels = listOf("0–5 m", "5–10 m", "10–20 m", "20–50 m", "50–100 m", "100–200 m", "200 m+")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(ramp[index], RoundedCornerShape(4.dp))
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---------------------------------------------------------------- place sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(
    place: SavedPlace,
    onNavigate: () -> Unit,
    onOpenOnMap: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(categoryColor(place.category), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(place.category.label, style = MaterialTheme.typography.labelLarge, color = XennThemeExtended.colors.textSecondary)
            }
            Text(place.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${GeoUtils.formatLatitude(place.latitude)}  ${GeoUtils.formatLongitude(place.longitude)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (place.note.isNotBlank()) {
                Text(place.note, style = MaterialTheme.typography.bodyMedium, color = XennThemeExtended.colors.textSecondary)
            }
            Text(
                "Saved ${FormatUtils.clockTime(place.createdAt)} · ${FormatUtils.shortDate(place.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onNavigate) {
                    Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Navigate")
                }
                OutlinedButton(onClick = onOpenOnMap) {
                    Text("Open on Map")
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                OutlinedButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (place.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (place.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (place.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete place",
                        tint = XennThemeExtended.colors.danger,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

