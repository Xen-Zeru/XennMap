package com.xennmap.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xennmap.presentation.dashboard.DashboardScreen
import com.xennmap.presentation.locations.LocationsScreen
import com.xennmap.presentation.map.MapScreen
import com.xennmap.presentation.navigation.NavigationScreen
import com.xennmap.presentation.offline.OfflineMapsScreen
import com.xennmap.presentation.profile.ProfileScreen
import com.xennmap.presentation.tracks.TracksScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val MAP = "map"
    const val LOCATIONS = "locations"
    const val NAVIGATION = "navigation"
    const val TRACKS = "tracks"
    const val PROFILE = "profile"
    const val OFFLINE = "offline"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.MAP, "Map", Icons.Rounded.Map),
    BottomTab(Routes.LOCATIONS, "Locations", Icons.Rounded.Place),
    BottomTab(Routes.NAVIGATION, "Navigate", Icons.Rounded.Navigation),
    BottomTab(Routes.TRACKS, "Tracks", Icons.Rounded.Route),
    BottomTab(Routes.PROFILE, "Profile", Icons.Rounded.Person),
)

@Composable
fun XennApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    launchSingleTop = true
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenMap = { navController.navigateTo(Routes.MAP) },
                    onOpenLocations = { navController.navigateTo(Routes.LOCATIONS) },
                    onOpenOffline = { navController.navigate(Routes.OFFLINE) },
                    onOpenTracks = { navController.navigateTo(Routes.TRACKS) },
                )
            }
            composable(Routes.MAP) {
                MapScreen(
                    onOpenLocations = { navController.navigateTo(Routes.LOCATIONS) },
                    onOpenDashboard = { navController.navigateTo(Routes.DASHBOARD) },
                    onOpenOffline = { navController.navigate(Routes.OFFLINE) },
                    onOpenProfile = { navController.navigateTo(Routes.PROFILE) },
                )
            }
            composable(Routes.LOCATIONS) {
                LocationsScreen(onGoToMap = { navController.navigateTo(Routes.MAP) })
            }
            composable(Routes.NAVIGATION) {
                NavigationScreen(onGoToMap = { navController.navigateTo(Routes.MAP) })
            }
            composable(Routes.TRACKS) {
                TracksScreen(onGoToMap = { navController.navigateTo(Routes.MAP) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenOffline = { navController.navigate(Routes.OFFLINE) })
            }
            composable(Routes.OFFLINE) {
                OfflineMapsScreen(
                    onClose = { navController.popBackStack() },
                    onOpenMap = { navController.navigateTo(Routes.MAP) },
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateTo(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(graph.findStartDestination().id) { saveState = true }
        restoreState = true
    }
}
