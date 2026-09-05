package com.xennmap.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.cos
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xennmap.domain.model.ChartDepth
import com.xennmap.domain.model.ThemeMode
import com.xennmap.presentation.common.AreaPickerSession
import com.xennmap.presentation.common.DownloadBounds
import com.xennmap.presentation.common.MapCommand
import com.xennmap.ui.components.ConfirmDialog
import com.xennmap.ui.components.GlassPanel
import com.xennmap.ui.components.PlaceEditorSheet
import com.xennmap.ui.theme.XennTheme
import com.xennmap.ui.theme.XennThemeExtended
import com.xennmap.utils.GeoUtils
import kotlin.math.hypot
import kotlinx.coroutines.launch

@Composable
fun MapScreen(
    onOpenLocations: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenProfile: () -> Unit,
    vm: MapViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val trackPoints by vm.trackPoints.collectAsStateWithLifecycle()
    val areaPicker by vm.areaPickerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val darkMap = when (ui.settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val engine = remember { MapEngine(context) { camera -> vm.onCameraChanged(camera) } }

    // ------------------------------------------------------------- lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> engine.mapView.onStart()
                Lifecycle.Event.ON_RESUME -> engine.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> engine.mapView.onPause()
                Lifecycle.Event.ON_STOP -> engine.mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose { engine.onDestroy() }
    }

    // ------------------------------------------------------------ permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) vm.onPermissionGranted()
    }
    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            vm.onPermissionGranted()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
    DisposableEffect(ui.hasLocationPermission) {
        if (ui.hasLocationPermission) vm.onScreenStart()
        onDispose { vm.onScreenStop() }
    }

    // ----------------------------------------------------------------- engine
    LaunchedEffect(darkMap) { engine.setDark(darkMap) }

    LaunchedEffect(Unit) {
        val camera = vm.initialCamera()
        if (camera.latitude != 0.0 || camera.longitude != 0.0) {
            engine.flyTo(camera.latitude, camera.longitude, camera.zoom)
        }
    }

    val places by rememberUpdatedState(ui.places)
    LaunchedEffect(Unit) {
        engine.placeTapResolver = { x, y ->
            var best: Long? = null
            var bestDistance = 52f
            places.forEach { place ->
                val screen = engine.screenFor(place.latitude, place.longitude) ?: return@forEach
                val distance = hypot(screen.x - x, screen.y - y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = place.id
                }
            }
            best
        }
        engine.onPlaceTapped = { id -> vm.selectPlace(id) }
        engine.onMapTapped = { lat, lng -> vm.onMapTapped(lat, lng) }
        engine.onMapLongTapped = { lat, lng -> vm.selectPoint(lat, lng) }
    }

    LaunchedEffect(Unit) {
        vm.commandBus.commands.collect { command ->
            when (command) {
                is MapCommand.FlyToPlace -> {
                    engine.flyTo(command.place.latitude, command.place.longitude, 13.0)
                    vm.selectPlace(command.place.id)
                }
                is MapCommand.FlyTo -> engine.flyTo(command.latitude, command.longitude, command.zoom)
                is MapCommand.CenterOnMe -> {
                    val fix = ui.gps.fix
                    if (fix != null) engine.flyTo(fix.latitude, fix.longitude, 14.5)
                }
                is MapCommand.ShowTrack -> vm.showTrack(command.trackId)
                is MapCommand.ShowPresetArea -> {
                    vm.showPresetOutline(
                        command.preset.south, command.preset.west,
                        command.preset.north, command.preset.east,
                        command.preset.name,
                    )
                    engine.fitBounds(
                        command.preset.south, command.preset.west,
                        command.preset.north, command.preset.east,
                    )
                }
            }
        }
    }

    LaunchedEffect(ui, trackPoints, darkMap) {
        engine.renderer.apply(
            MapRenderState(
                dark = darkMap,
                coastlineJson = ui.coastline,
                bathymetry = ui.bathymetry,
                layers = ui.settings.layers,
                places = ui.places,
                selectedPlaceId = ui.selectedPlace?.id,
                fix = ui.gps.fix,
                trackPoints = trackPoints,
                navDestination = ui.navState.destination,
                navActive = ui.navState.isActive,
                selectedPoint = ui.selectedPoint?.let { it.latitude to it.longitude },
                presetOutline = ui.presetOutline,
            )
        )
    }

    var fittedPickerBounds by remember { mutableStateOf(false) }
    LaunchedEffect(areaPicker) {
        fittedPickerBounds = false
    }
    LaunchedEffect(areaPicker?.initialBounds) {
        val bounds = areaPicker?.initialBounds
        if (bounds != null && !fittedPickerBounds) {
            fittedPickerBounds = true
            engine.fitBounds(bounds.south, bounds.west, bounds.north, bounds.east)
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // ------------------------------------------------------------------ layout
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { engine.mapView }, modifier = Modifier.fillMaxSize())

        if (areaPicker != null) {
            // Fixed screen-space frame: the user pans/zooms the map inside it.
            FrameReticle(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .fillMaxHeight(0.62f),
            )
        }

        if (!ui.hasLocationPermission) {
            PermissionCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                onEnable = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                },
            )
        }

        MapTopBar(
            online = ui.online,
            gps = ui.gps,
            onSearch = onOpenLocations,
            onOpenDashboard = onOpenDashboard,
            onOpenOffline = onOpenOffline,
            onOpenSettings = onOpenProfile,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
        )

        MapControls(
            bearing = ui.camera.bearing,
            onLayers = { vm.setLayersSheetVisible(true) },
            onMyLocation = {
                val fix = ui.gps.fix
                if (fix != null) {
                    engine.flyTo(fix.latitude, fix.longitude, 14.5)
                } else {
                    scope.launch { snackbar.showSnackbar("Waiting for a GPS fix…") }
                }
            },
            onCompass = { engine.resetNorth() },
            onZoomIn = { engine.zoomIn() },
            onZoomOut = { engine.zoomOut() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 240.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp),
        ) {
            if (ui.navState.isRunning) {
                NavigationCard(
                    plan = ui.navPlan,
                    phase = ui.navState.phase,
                    distanceUnit = ui.settings.distanceUnit,
                    speedUnit = ui.settings.speedUnit,
                    depthUnit = ui.settings.depthUnit,
                    destinationDepth = ui.navDestinationDepth,
                    liveDepth = ui.depthMeters?.let { ChartDepth(it, ui.depthDownloaded) },
                    onPauseResume = {
                        if (ui.navState.isActive) vm.pauseNavigation() else vm.resumeNavigation()
                    },
                    onEnd = { vm.endNavigation() },
                )
            } else {
                LocationCard(
                    gps = ui.gps,
                    distanceUnit = ui.settings.distanceUnit,
                    speedUnit = ui.settings.speedUnit,
                    depthMeters = ui.depthMeters,
                    depthDownloaded = ui.depthDownloaded,
                    depthUnit = ui.settings.depthUnit,
                    collapsed = ui.cardCollapsed,
                    onToggleCollapse = { vm.setCardCollapsed(!ui.cardCollapsed) },
                    onMarkLocation = { vm.openMarkSheet() },
                )
            }
        }

        if (areaPicker != null) {
            AreaPickerOverlay(
                bounds = viewportBounds(ui.camera, context),
                onCancel = { vm.cancelAreaPicker() },
                onDownload = { vm.confirmAreaDownload(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 190.dp),
        )
    }

    // ----------------------------------------------------------------- sheets
    if (ui.layersSheetVisible) {
        LayersSheet(
            layers = ui.settings.layers,
            bathymetry = ui.bathymetry,
            dark = darkMap,
            onToggleBathymetry = { vm.setLayers(ui.settings.layers.copy(bathymetryEnabled = it)) },
            onToggleContours = { vm.setLayers(ui.settings.layers.copy(contoursEnabled = it)) },
            onToggleDepthLabels = { vm.setLayers(ui.settings.layers.copy(depthLabelsEnabled = it)) },
            onTogglePlaces = { vm.setLayers(ui.settings.layers.copy(savedPlacesVisible = it)) },
            onDismiss = { vm.setLayersSheetVisible(false) },
        )
    }

    val selected = ui.selectedPlace
    if (ui.placeSheetVisible && selected != null) {
        PlaceSheet(
            place = selected,
            onNavigate = { vm.startNavigation(selected) },
            onOpenOnMap = {
                vm.commandBus.send(MapCommand.FlyTo(selected.latitude, selected.longitude, 14.0))
                vm.dismissPlaceSheet()
            },
            onEdit = { vm.startEdit(selected) },
            onToggleFavorite = { vm.toggleFavorite(selected) },
            onDelete = { vm.requestDelete(selected) },
            onDismiss = { vm.dismissPlaceSheet() },
        )
    }

    ui.selectedPoint?.let { point ->
        SelectedPointSheet(
            point = point,
            depthUnit = ui.settings.depthUnit,
            onSave = { vm.saveSelectedPoint() },
            onNavigate = { vm.navigateToPoint() },
            onCancel = { vm.dismissSelectedPoint() },
        )
    }

    ui.markTarget?.let { target ->
        PlaceEditorSheet(
            latitude = target.latitude,
            longitude = target.longitude,
            title = "Save Location",
            estimatedDepth = ui.markDepth,
            depthUnit = ui.settings.depthUnit,
            onSave = { name, category, note -> vm.savePlace(name, category, note) },
            onDismiss = { vm.dismissMarkSheet() },
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
            estimatedDepth = ui.markDepth,
            depthUnit = ui.settings.depthUnit,
            onSave = { name, category, note -> vm.savePlace(name, category, note) },
            onDismiss = { vm.dismissMarkSheet() },
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
private fun PermissionCard(
    modifier: Modifier = Modifier,
    onEnable: () -> Unit,
) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Location permission needed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "XennMap uses GPS to show your position on the water. Location is only used on this device — it is never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = XennThemeExtended.colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onEnable) { Text("Enable location") }
        }
    }
}

private data class ViewportQuad(val south: Double, val west: Double, val north: Double, val east: Double)

private fun viewportBounds(camera: MapCamera, context: Context): DownloadBounds {
    val metrics = Resources.getSystem().displayMetrics
    val mpp = GeoUtils.metersPerPixel(camera.latitude, camera.zoom).coerceAtLeast(0.5)
    val halfWM = metrics.widthPixels / 2.0 * mpp
    val halfHM = metrics.heightPixels / 2.0 * mpp
    val dLat = halfHM / 111_320.0
    val dLng = halfWM / (111_320.0 * cos(Math.toRadians(camera.latitude)).coerceAtLeast(0.05))
    return DownloadBounds(
        south = (camera.latitude - dLat).coerceIn(-85.0, 85.0),
        west = camera.longitude - dLng,
        north = (camera.latitude + dLat).coerceIn(-85.0, 85.0),
        east = camera.longitude + dLng,
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
