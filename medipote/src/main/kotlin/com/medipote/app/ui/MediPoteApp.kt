package com.medipote.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.TakeStatus
import com.medipote.app.domain.Rules
import com.medipote.app.notifications.Notifications
import com.medipote.app.notifications.ReminderScheduler
import com.medipote.app.ui.cycle.CycleScreen
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.onboarding.PetNamingScreen
import com.medipote.app.ui.onboarding.ProfileScreen
import com.medipote.app.ui.onboarding.ProfileSetupScreen
import com.medipote.app.ui.onboarding.WelcomeScreen
import com.medipote.app.ui.settings.SettingsScreen
import com.medipote.app.ui.shop.ShopScreen
import com.medipote.app.ui.stats.StatsScreen
import com.medipote.app.ui.theme.AppTheme
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.treatments.DrugSearchScreen
import com.medipote.app.ui.treatments.TreatmentEditorScreen
import com.medipote.app.ui.treatments.TreatmentsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab("home", "Accueil", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab("treatments", "Traitements", Icons.Outlined.Medication, Icons.Rounded.Medication),
    Tab("stats", "Stats", Icons.Outlined.BarChart, Icons.Rounded.BarChart),
    Tab("shop", "Boutique", Icons.Outlined.Storefront, Icons.Rounded.Storefront),
    Tab("cycle", "Cycle", Icons.Outlined.WaterDrop, Icons.Rounded.WaterDrop),
)

@Composable
fun MediPoteApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    MediPoteTheme(state.settings.themeMode, state.settings.dynamicColor) {
        val context = LocalContext.current
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        var feedSignal by remember { mutableIntStateOf(0) }
        var lastPoints by remember { mutableIntStateOf(0) }
        var checkIn by remember { mutableStateOf<UiEvent.CheckedIn?>(null) }
        var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
        var exactAllowed by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }
        val today = now.toLocalDate()
        val pet = state.game.petName

        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsAllowed = granted
            vm.reschedule()
        }
        fun askNotifications() {
            if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            notificationsAllowed = Notifications.canPost(context)
            exactAllowed = ReminderScheduler.canScheduleExact(context)
            vm.onResume()
        }

        LaunchedEffect(Unit) {
            vm.events.collect { e ->
                when (e) {
                    is UiEvent.Fed -> {
                        feedSignal++
                        lastPoints = e.points + e.perfectDayBonus
                        val base = when (e.status) {
                            TakeStatus.ON_TIME -> "Bravo ! $pet a pris ${e.treatmentName} à l'heure : +${e.points} pts"
                            TakeStatus.LATE -> "${e.treatmentName} donné en retard : +${e.points} pts (malus −${Rules.POINTS_ON_TIME - e.points})"
                            TakeStatus.VERY_LATE -> "${e.treatmentName} donné très en retard : +${e.points} pts (malus −${Rules.POINTS_ON_TIME - e.points})"
                        }
                        val bonus = if (e.perfectDayBonus > 0) " · Journée parfaite : +${e.perfectDayBonus} !" else ""
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(base + bonus)
                    }
                    is UiEvent.CheckedIn -> checkIn = e
                    is UiEvent.Bought -> {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("${e.item.name} acheté et équipé !")
                    }
                }
            }
        }

        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val tabs = if (state.profile?.menstruating == true) TABS else TABS.dropLast(1)
        val showBar = tabs.any { it.route == route }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                                label = { Text(tab.label, maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = if (state.onboarded) "home" else "welcome",
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                // ── First run ──
                composable("welcome") { WelcomeScreen(onStart = { nav.navigate("setup_profile") }) }
                composable("setup_profile") {
                    ProfileSetupScreen(state.profile, today, onBack = { nav.popBackStack() }) {
                        vm.saveProfile(it)
                        nav.navigate("setup_pet")
                    }
                }
                composable("setup_pet") {
                    PetNamingScreen(pet, onBack = { nav.popBackStack() }) {
                        vm.namePet(it)
                        nav.navigate("setup_treatments")
                    }
                }
                composable("setup_treatments") {
                    TreatmentsScreen(
                        state.treatments, today,
                        onAdd = { nav.navigate("search") },
                        onEdit = { nav.navigate("editor?treatment=${it.id}") },
                        onboarding = true,
                        onFinishOnboarding = {
                            vm.finishOnboarding()
                            askNotifications()
                            nav.navigate("home") { popUpTo(0) { inclusive = true } }
                        },
                    )
                }

                // ── Tabs ──
                composable("home") {
                    HomeScreen(
                        state, now, feedSignal, lastPoints,
                        onGive = vm::give,
                        onAddTreatment = { nav.navigate("search") },
                        onOpenProfile = { nav.navigate("profile") },
                        onOpenSettings = { nav.navigate("settings") },
                    )
                }
                composable("treatments") {
                    TreatmentsScreen(
                        state.treatments, today,
                        onAdd = { nav.navigate("search") },
                        onEdit = { nav.navigate("editor?treatment=${it.id}") },
                    )
                }
                composable("stats") { StatsScreen(state, now) }
                composable("shop") { ShopScreen(state.game, vm::buy, vm::equip) }
                composable("cycle") {
                    CycleScreen(state.profile?.cycle, today, vm::logPeriod, vm::updateCycle)
                }

                // ── Treatments flow ──
                composable("search") {
                    DrugSearchScreen(
                        onBack = { nav.popBackStack() },
                        onPick = { nav.navigate("editor?drug=${it.id}") { popUpTo("search") { inclusive = true } } },
                        onCustom = { nav.navigate("editor?custom=${Uri.encode(it)}") { popUpTo("search") { inclusive = true } } },
                    )
                }
                composable(
                    "editor?drug={drug}&custom={custom}&treatment={treatment}",
                    arguments = listOf(
                        navArgument("drug") { type = NavType.StringType; nullable = true },
                        navArgument("custom") { type = NavType.StringType; nullable = true },
                        navArgument("treatment") { type = NavType.StringType; nullable = true },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    val existing = args?.getString("treatment")?.let { id -> state.treatments.firstOrNull { it.id == id } }
                    val drug = (existing?.drugId ?: args?.getString("drug"))?.let(DrugGuide::byId)
                    TreatmentEditorScreen(
                        existing = existing,
                        drug = drug,
                        customName = args?.getString("custom").orEmpty(),
                        allergies = state.profile?.allergies.orEmpty(),
                        today = today,
                        onSave = {
                            vm.saveTreatment(it)
                            nav.popBackStack()
                        },
                        onDelete = {
                            existing?.let { t -> vm.deleteTreatment(t.id) }
                            nav.popBackStack()
                        },
                        onBack = { nav.popBackStack() },
                    )
                }

                // ── Settings ──
                composable("settings") {
                    SettingsScreen(
                        settings = state.settings,
                        petName = pet,
                        notificationsAllowed = notificationsAllowed,
                        exactAlarmsAllowed = exactAllowed,
                        dynamicColorAvailable = Build.VERSION.SDK_INT >= 31,
                        onBack = { nav.popBackStack() },
                        onChange = vm::updateSettings,
                        onRequestNotifications = ::askNotifications,
                        onRequestExactAlarms = {
                            if (Build.VERSION.SDK_INT >= 31) {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        },
                        onRenamePet = vm::namePet,
                        onOpenProfile = { nav.navigate("profile") },
                        onReset = {
                            vm.resetAll()
                            nav.navigate("welcome") { popUpTo(0) { inclusive = true } }
                        },
                    )
                }
                composable("profile") {
                    state.profile?.let { p -> ProfileScreen(p, today, onBack = { nav.popBackStack() }, onSave = vm::saveProfile) }
                }
            }
        }

        checkIn?.let { event -> CheckInDialog(event, pet) { checkIn = null } }
        LeaveCycleTabIfDisabled(nav, route, state.profile?.menstruating == true)
    }
}

@Composable
private fun CheckInDialog(event: UiEvent.CheckedIn, pet: String, onDismiss: () -> Unit) {
    val r = event.reward
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LocalFireDepartment, null, tint = AppTheme.status.streak) },
        title = {
            Text(
                if (r.streak == 1) "Bienvenue !" else "Série de ${r.streak} jours !",
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                buildString {
                    append("$pet est content de te voir : +${r.points} points.")
                    if (r.milestoneBonus > 0) append("\nPalier atteint : +${r.milestoneBonus} points bonus !")
                    if (r.points < Rules.STREAK_POINTS_CAP) append("\nReviens demain pour gagner encore plus.")
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Super !") } },
    )
}

@Composable
private fun LeaveCycleTabIfDisabled(nav: NavHostController, route: String?, enabled: Boolean) {
    LaunchedEffect(route, enabled) {
        if (route == "cycle" && !enabled) nav.navigate("home") { popUpTo("home") }
    }
}
