package app.lumora.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lumora.AppContainer
import app.lumora.designsystem.icons.LumoraIcons
import app.lumora.ui.library.LibraryScreen
import app.lumora.ui.preview.PreviewScreen
import app.lumora.ui.settings.SettingsScreen
import app.lumora.ui.store.StoreScreen

object Routes {
    const val STORE = "store"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PREVIEW = "preview/{source}/{id}"

    fun preview(source: String, id: Any): String = "preview/$source/$id"
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    NavItem(Routes.STORE, "Tienda", LumoraIcons.Sparkle),
    NavItem(Routes.LIBRARY, "Biblioteca", LumoraIcons.Grid),
    NavItem(Routes.SETTINGS, "Ajustes", LumoraIcons.Sliders),
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LumoraApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute in bottomItems.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(120)),
            ) {
                LumoraBottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = Routes.STORE,
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(160)) },
                ) {
                    composable(Routes.STORE) {
                        StoreScreen(
                            container = container,
                            animatedVisibilityScope = this,
                            onOpenItem = { item ->
                                navController.navigate(Routes.preview(PreviewViewModelSource.STORE, item.id))
                            },
                        )
                    }
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            container = container,
                            animatedVisibilityScope = this,
                            onOpenLocal = { id ->
                                navController.navigate(Routes.preview(PreviewViewModelSource.LOCAL, id))
                            },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(container = container)
                    }
                    composable(Routes.PREVIEW) { entry ->
                        PreviewScreen(
                            container = container,
                            source = entry.arguments?.getString("source") ?: PreviewViewModelSource.STORE,
                            id = entry.arguments?.getString("id") ?: "",
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LumoraBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

object PreviewViewModelSource {
    const val LOCAL = "local"
    const val STORE = "store"
}
