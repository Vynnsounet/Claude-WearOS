package com.radaroccaz.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.ui.alerts.AlertsScreen
import com.radaroccaz.app.ui.alerts.CreateAlertDialog
import com.radaroccaz.app.ui.components.SourceInfo
import com.radaroccaz.app.ui.components.specLine
import com.radaroccaz.app.ui.detail.HistoryEditorScreen
import com.radaroccaz.app.ui.detail.ListingDetailScreen
import com.radaroccaz.app.ui.favorites.FavoritesScreen
import com.radaroccaz.app.ui.results.ResultsScreen
import com.radaroccaz.app.ui.search.SearchScreen
import com.radaroccaz.app.ui.settings.SettingsScreen
import com.radaroccaz.app.ui.settings.SourceItem
import com.radaroccaz.app.ui.theme.RadarTheme
import com.radaroccaz.app.work.Notifications

private object Routes {
    const val SEARCH = "search"
    const val RESULTS = "results"
    const val DETAIL = "detail/{id}"
    const val ALERTS = "alerts"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val HISTORY = "history/{id}"
    fun detail(id: String) = "detail/${android.net.Uri.encode(id)}"
    fun history(id: String) = "history/${android.net.Uri.encode(id)}"
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Routes.SEARCH, "Recherche", Icons.Rounded.Search, Icons.Rounded.Search),
    Tab(Routes.ALERTS, "Alertes", Icons.Rounded.NotificationsNone, Icons.Rounded.Notifications),
    Tab(Routes.FAVORITES, "Favoris", Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
)

@Composable
fun RadarApp(vm: AppViewModel) {
    val app by vm.state.collectAsStateWithLifecycle()
    RadarTheme(app.settings.themeMode) {
        val nav = rememberNavController()
        val context = LocalContext.current
        val draft by vm.draft.collectAsStateWithLifecycle()
        val results by vm.results.collectAsStateWithLifecycle()
        val checking by vm.checkingAlerts.collectAsStateWithLifecycle()
        val pendingAlert by vm.pendingAlert.collectAsStateWithLifecycle()
        val deep by vm.deep.collectAsStateWithLifecycle()
        val favoriteIds = remember(app.favorites) { app.favorites.map { it.id }.toSet() }
        var showCreateAlert by remember { mutableStateOf(false) }
        var notificationsAllowed by remember { mutableStateOf(Notifications.canNotify(context)) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsAllowed = Notifications.canNotify(context)
        }
        fun askNotifications() {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else openUrl(context, "package:${context.packageName}", settings = true)
        }

        LaunchedEffect(pendingAlert) {
            val id = pendingAlert ?: return@LaunchedEffect
            vm.consumePendingAlert()
            vm.openAlert(id)
            nav.navigate(Routes.RESULTS) { launchSingleTop = true }
        }

        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val unread = app.alerts.sumOf { it.unread.size }

        Scaffold(
            bottomBar = {
                if (route != null && tabs.any { it.route == route }) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.switchTab(tab.route) },
                                icon = {
                                    BadgedBox(badge = {
                                        if (tab.route == Routes.ALERTS && unread > 0) Badge { Text("$unread") }
                                    }) { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) }
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                nav,
                startDestination = Routes.SEARCH,
                modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding),
            ) {
                composable(Routes.SEARCH) {
                    SearchScreen(
                        criteria = draft,
                        recent = app.recentSearches,
                        demoMode = app.settings.demoMode,
                        onChange = vm::updateDraft,
                        onSearch = { vm.search(); nav.navigate(Routes.RESULTS) },
                        onPickRecent = { c -> vm.updateDraft { c }; vm.search(c); nav.navigate(Routes.RESULTS) },
                        onClearRecent = vm::clearRecentSearches,
                        onReset = vm::resetDraft,
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.RESULTS) {
                    val hasAlert = results.alertId != null ||
                        app.alerts.any { it.criteria == results.criteria }
                    ResultsScreen(
                        state = results,
                        favorites = favoriteIds,
                        hasAlert = hasAlert,
                        onBack = {
                            results.criteria?.let { c -> vm.updateDraft { c } }
                            if (!nav.popBackStack(Routes.SEARCH, inclusive = false)) nav.switchTab(Routes.SEARCH)
                        },
                        onSort = vm::setSort,
                        onOpen = { nav.navigate(Routes.detail(it.id)) },
                        onToggleFavorite = vm::toggleFavorite,
                        onCreateAlert = { showCreateAlert = true },
                        onRetry = vm::retry,
                        onUseDemo = vm::useDemoAndRetry,
                        onOpenUrl = { openUrl(context, it) },
                    )
                    if (showCreateAlert && results.criteria != null) {
                        CreateAlertDialog(
                            defaultName = results.criteria!!.title,
                            summary = results.criteria!!.summary,
                            onDismiss = { showCreateAlert = false },
                            onCreate = { name, freq ->
                                vm.createAlert(name, freq)
                                showCreateAlert = false
                                if (!Notifications.canNotify(context)) askNotifications()
                            },
                        )
                    }
                }
                composable(Routes.DETAIL) { entry ->
                    val id = entry.arguments?.getString("id")?.let(android.net.Uri::decode)
                    val listing = id?.let(vm::findListing)
                    if (listing == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                        Box(Modifier.fillMaxSize())
                    } else {
                        // Reading the full page gives the whole description and every attribute.
                        LaunchedEffect(listing.id) { vm.analyzeDeep(listing.id) }
                        ListingDetailScreen(
                            listing = listing,
                            scored = vm.scored(listing.id),
                            analysis = remember(listing, app.userHistory[listing.id], results) { vm.analysisFor(listing) },
                            deep = deep[listing.id],
                            onRetryDeep = { vm.analyzeDeep(listing.id, force = true) },
                            onEditHistory = { nav.navigate(Routes.history(listing.id)) },
                            wantedOptions = results.criteria?.wantedOptions.orEmpty(),
                            favorite = listing.id in favoriteIds,
                            onBack = { nav.popBackStack() },
                            onToggleFavorite = { vm.toggleFavorite(listing) },
                            onShare = { share(context, listing) },
                            onOpenUrl = { openUrl(context, it) },
                        )
                    }
                }
                composable(Routes.HISTORY) { entry ->
                    val id = entry.arguments?.getString("id")?.let(android.net.Uri::decode)
                    val listing = id?.let(vm::findListing)
                    if (listing == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        HistoryEditorScreen(
                            listing = listing,
                            initial = app.userHistory[listing.id] ?: com.radaroccaz.app.data.VehicleHistory(),
                            onSave = { vm.saveUserHistory(listing.id, it); nav.popBackStack() },
                            onBack = { nav.popBackStack() },
                            onOpenUrl = { openUrl(context, it) },
                        )
                    }
                }
                composable(Routes.ALERTS) {
                    LaunchedEffect(Unit) { notificationsAllowed = Notifications.canNotify(context) }
                    AlertsScreen(
                        alerts = app.alerts,
                        checking = checking,
                        notificationsAllowed = notificationsAllowed,
                        onOpen = { vm.openAlert(it.id); nav.navigate(Routes.RESULTS) },
                        onToggle = { a, on -> vm.setAlertEnabled(a.id, on) },
                        onFrequency = { a, f -> vm.setAlertFrequency(a.id, f) },
                        onDelete = { vm.deleteAlert(it.id) },
                        onCheckNow = vm::checkAlertsNow,
                        onAllowNotifications = ::askNotifications,
                        onNewSearch = { nav.switchTab(Routes.SEARCH) },
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesScreen(
                        favorites = app.favorites,
                        onOpen = { nav.navigate(Routes.detail(it.id)) },
                        onToggleFavorite = vm::toggleFavorite,
                        onNewSearch = { nav.switchTab(Routes.SEARCH) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = app.settings,
                        sources = vm.sources.map { SourceItem(it.id, it.name, it.description) },
                        onChange = vm::updateSettings,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun openUrl(context: Context, url: String, settings: Boolean = false) {
    val intent = if (settings) Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, url.toUri())
    else Intent(Intent.ACTION_VIEW, url.toUri())
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun share(context: Context, l: Listing) {
    val text = "${l.title}\n${Format.price(l.price)} · ${l.specLine()}\n${l.url}\n(via Radar Occaz, annonce ${SourceInfo.name(l.source)})"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Partager l'annonce").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

