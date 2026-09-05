package com.xennmap.presentation.profile

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xennmap.R
import com.xennmap.domain.model.DepthUnit
import com.xennmap.domain.model.DistanceUnit
import com.xennmap.domain.model.LocationAccuracyMode
import com.xennmap.domain.model.SpeedUnit
import com.xennmap.domain.model.ThemeMode
import com.xennmap.ui.components.SectionHeader
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.FormatUtils

@Composable
fun ProfileScreen(
    onOpenOffline: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Profile & Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))

        // ------------------------------------------------------------ appearance
        SectionHeader("APPEARANCE")
        SettingsCard {
            val theme = ui.settings.themeMode
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = theme == mode,
                        onClick = { vm.setThemeMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }

        // ---------------------------------------------------------------- units
        SectionHeader("UNITS")
        SettingsCard {
            Text("Distance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistanceUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = ui.settings.distanceUnit == unit,
                        onClick = { vm.setDistanceUnit(unit) },
                        label = { Text(unit.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Depth", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DepthUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = ui.settings.depthUnit == unit,
                        onClick = { vm.setDepthUnit(unit) },
                        label = { Text(unit.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Speed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = ui.settings.speedUnit == unit,
                        onClick = { vm.setSpeedUnit(unit) },
                        label = { Text(unit.label) },
                    )
                }
            }
        }

        // ------------------------------------------------------------------ map
        SectionHeader("MAP")
        SettingsCard {
            val layers = ui.settings.layers
            ToggleRow("Bathymetry", "Depth zones and contours", layers.bathymetryEnabled) {
                vm.setLayers(layers.copy(bathymetryEnabled = it))
            }
            ToggleRow("Depth contours", "Lines between depth zones", layers.contoursEnabled) {
                vm.setLayers(layers.copy(contoursEnabled = it))
            }
            ToggleRow("Depth labels", "Small depth markers on the water", layers.depthLabelsEnabled) {
                vm.setLayers(layers.copy(depthLabelsEnabled = it))
            }
            ToggleRow("Saved places", "Show your pins on the map", layers.savedPlacesVisible) {
                vm.setLayers(layers.copy(savedPlacesVisible = it))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Satellite imagery", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Available in a future update — needs a downloadable imagery package.",
                        style = MaterialTheme.typography.bodySmall,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
                Switch(checked = false, onCheckedChange = null, enabled = false)
            }
        }

        // ------------------------------------------------------------------ gps
        SectionHeader("GPS")
        SettingsCard {
            Text("Location accuracy", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocationAccuracyMode.entries.forEach { mode ->
                    FilterChip(
                        selected = ui.settings.accuracyMode == mode,
                        onClick = { vm.setAccuracyMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Tracking interval", style = MaterialTheme.typography.titleMedium)
            Text(
                "How often breadcrumb points are saved while tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10).forEach { seconds ->
                    FilterChip(
                        selected = ui.settings.trackingIntervalSec == seconds,
                        onClick = { vm.setTrackingInterval(seconds) },
                        label = { Text("${seconds}s") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = XennThemeExtended.colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "GPS works fully offline. XennMap pauses GPS when hidden to save battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = XennThemeExtended.colors.textSecondary,
                )
            }
        }

        // -------------------------------------------------------------- storage
        SectionHeader("STORAGE")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${FormatUtils.bytes(ui.offlineBytes)} used by offline maps",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${FormatUtils.bytes(ui.cacheBytes)} temporary cache",
                        style = MaterialTheme.typography.bodySmall,
                        color = XennThemeExtended.colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenOffline) { Text("Offline Maps") }
                OutlinedButton(onClick = { vm.clearCache() }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Clear cached data")
                }
            }
        }

        // ---------------------------------------------------------------- about
        SectionHeader("ABOUT")
        SettingsCard {
            Text("XennMap ${ui.versionName}", style = MaterialTheme.typography.titleMedium)
            Text(
                context.getString(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = XennThemeExtended.colors.warning,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Safety", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                context.getString(R.string.navigation_assistance_notice),
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text("Data sources", style = MaterialTheme.typography.titleMedium)
            Text(
                context.getString(R.string.map_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Text(
                "All locations, tracks and settings stay on this device. Nothing is uploaded — your fishing spots belong to you.",
                style = MaterialTheme.typography.bodySmall,
                color = XennThemeExtended.colors.textSecondary,
            )
        }

        Spacer(Modifier.height(28.dp))
    }

    SnackbarHost(hostState = snackbar)
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
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
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
