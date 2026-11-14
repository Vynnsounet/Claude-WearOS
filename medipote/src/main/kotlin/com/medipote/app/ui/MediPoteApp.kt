package com.medipote.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.net.toUri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medipote.app.data.DocCategory
import com.medipote.app.data.DocumentStore
import com.medipote.app.data.DrugGuide
import com.medipote.app.data.HealthDocument
import com.medipote.app.data.TakeStatus
import com.medipote.app.domain.Rules
import com.medipote.app.notifications.Notifications
import com.medipote.app.notifications.ReminderScheduler
import com.medipote.app.ui.agenda.AgendaScreen
import com.medipote.app.ui.agenda.AppointmentEditorScreen
import com.medipote.app.ui.cycle.CycleScreen
import com.medipote.app.ui.documents.DocumentInfoScreen
import com.medipote.app.ui.documents.DocumentViewerScreen
import com.medipote.app.ui.documents.DocumentsScreen
import com.medipote.app.ui.home.HomeScreen
import com.medipote.app.ui.journal.DayEntryScreen
import com.medipote.app.ui.journal.JournalScreen
import com.medipote.app.ui.onboarding.PetNamingScreen
import com.medipote.app.ui.onboarding.ProfileScreen
import com.medipote.app.ui.onboarding.ProfileSetupScreen
import com.medipote.app.ui.onboarding.WelcomeScreen
import com.medipote.app.ui.pill.PillScreen
import com.medipote.app.ui.prn.PrnLogScreen
import com.medipote.app.ui.sleep.SleepEditorScreen
import com.medipote.app.ui.sleep.SleepScreen
import com.medipote.app.ui.prn.PrnStatsScreen
import com.medipote.app.ui.pill.PillSetupScreen
import com.medipote.app.ui.settings.SettingsScreen
import com.medipote.app.ui.shop.ShopScreen
import com.medipote.app.ui.stats.StatsScreen
import com.medipote.app.ui.theme.AppTheme
import com.medipote.app.ui.theme.MediPoteTheme
import com.medipote.app.ui.treatments.DrugSearchScreen
import com.medipote.app.ui.treatments.TreatmentEditorScreen
import com.medipote.app.ui.treatments.TreatmentsScreen
import java.io.File
import java.time.LocalDate
import java.util.UUID

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab("home", "Accueil", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab("treatments", "Traitements", Icons.Outlined.Medication, Icons.Rounded.Medication),
    Tab("suivi", "Suivi", Icons.Outlined.Insights, Icons.Rounded.Insights),
    Tab("sante", "Santé", Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite),
    Tab("shop", "Boutique", Icons.Outlined.Storefront, Icons.Rounded.Storefront),
)

/** A top-level tab split into sub-tabs ("Journal | Cycle | Stats"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hub(
    title: String,
    tabs: List<String>,
    initial: Int,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable (Int) -> Unit,
) {
    var selected by rememberSaveable(tabs.size) { mutableIntStateOf(initial.coerceIn(0, tabs.lastIndex)) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    actions = {
                        IconButton(onClick = onOpenProfile) { Icon(Icons.Outlined.AccountCircle, "Profil médical") }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Paramètres") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (tabs.size > 1) {
                    PrimaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.background) {
                        tabs.forEachIndexed { i, label ->
                            Tab(selected = selected == i, onClick = { selected = i }, text = { Text(label, maxLines = 1) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content(selected) }
    }
}

@Composable
fun MediPoteApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    MediPoteTheme(state.settings.themeMode, state.settings.dynamicColor, state.settings.accent) {
        val context = LocalContext.current
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        var feedSignal by remember { mutableIntStateOf(0) }
        var lastPoints by remember { mutableIntStateOf(0) }
        var pointsSignal by remember { mutableIntStateOf(0) }
        var askedThisSession by rememberSaveable { mutableStateOf(false) }
        var batteryRestricted by remember { mutableStateOf(isBatteryRestricted(context)) }
        var checkIn by remember { mutableStateOf<UiEvent.CheckedIn?>(null) }
        var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
        var exactAllowed by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }
        var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
        val today = now.toLocalDate()
        val pet = state.game.petName
        val menstruating = state.profile?.menstruating == true
        val hasPill = state.treatments.any { it.pill != null }

        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsAllowed = granted
            vm.reschedule()
        }
        fun askNotifications() {
            if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val name = pendingPhoto
            pendingPhoto = null
            if (name == null) return@rememberLauncherForActivityResult
            if (ok) nav.navigate("doc_info?file=$name&mime=${Uri.encode("image/jpeg")}")
            else File(DocumentStore.dir(context), name).delete()
        }
        val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val imported = uri?.let { DocumentStore.import(context, it) }
            if (imported != null) nav.navigate("doc_info?file=${imported.first}&mime=${Uri.encode(imported.second)}")
            else if (uri != null) vm.message("Impossible d'importer ce fichier")
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            notificationsAllowed = Notifications.canPost(context)
            exactAllowed = ReminderScheduler.canScheduleExact(context)
            batteryRestricted = isBatteryRestricted(context)
            vm.onResume()
            // Ask again once per session if reminders are on but the permission is missing.
            if (state.onboarded && state.settings.notificationsEnabled && !notificationsAllowed && !askedThisSession) {
                askedThisSession = true
                askNotifications()
            }
        }
        fun fixNotifications() {
            if (Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED && !askedThisSession
            ) {
                askedThisSession = true
                askNotifications()
            } else {
                context.startActivity(Notifications.settingsIntent(context))
            }
        }

        LaunchedEffect(Unit) {
            vm.events.collect { e ->
                when (e) {
                    is UiEvent.Points -> {
                        lastPoints = e.amount
                        pointsSignal++
                    }
                    is UiEvent.Fed -> {
                        feedSignal++
                        pointsSignal++
                        lastPoints = e.points + e.perfectDayBonus
                        val base = when (e.status) {
                            TakeStatus.ON_TIME -> "Bravo ! $pet a pris ${e.treatmentName} à l'heure : +${e.points} pts"
                            TakeStatus.LATE -> "${e.treatmentName} donné en retard : +${e.points} pts (malus −${Rules.POINTS_ON_TIME - e.points})"
                            TakeStatus.VERY_LATE -> "${e.treatmentName} donné très en retard : +${e.points} pts (malus −${Rules.POINTS_ON_TIME - e.points})"
                            TakeStatus.EARLY -> "${e.treatmentName} noté comme pris en avance : +${e.points} pts"
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
                    is UiEvent.Message -> {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(e.text)
                    }
                }
            }
        }

        val lastIntakes = remember(state.prnIntakes, now) {
            state.prnIntakes.filter { !it.takenAt.isAfter(now) }.groupBy { it.treatmentId }.mapValues { (_, l) -> l.maxOf { it.takenAt } }
        }
        // Screens asked from outside the UI (a tapped notification), once the app is set up.
        val requestedRoute by vm.routes.collectAsStateWithLifecycle()
        LaunchedEffect(requestedRoute, state.onboarded) {
            val r = requestedRoute ?: return@LaunchedEffect
            if (!state.onboarded) return@LaunchedEffect
            nav.navigate(r) { launchSingleTop = true }
            vm.routeHandled()
        }

        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route?.substringBefore('?')
        val showBar = TABS.any { it.route == route }
        val openProfile = { nav.navigate("profile") }
        val openSettings = { nav.navigate("settings") }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        TABS.forEach { tab ->
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
                        onEdit = { nav.navigate(if (it.pill != null) "pill_setup" else "editor?treatment=${it.id}") },
                        onboarding = true,
                        onFinishOnboarding = {
                            vm.finishOnboarding()
                            askNotifications()
                            nav.navigate("home") { popUpTo(0) { inclusive = true } }
                        },
                        onRefill = { vm.refill(it.id) },
                        lastIntakes = lastIntakes,
                        now = now,
                        onLogPrn = { nav.navigate("prn_log/${it.id}") },
                        onPrnStats = { nav.navigate("prn_stats/${it.id}") },
                    )
                }

                // ── Tabs ──
                composable("home") {
                    HomeScreen(
                        state, now, feedSignal, lastPoints,
                        onGive = vm::give,
                        onAddTreatment = { nav.navigate("search") },
                        onOpenProfile = openProfile,
                        onOpenSettings = openSettings,
                        onAddWater = vm::addWater,
                        onOpenJournal = { nav.navigate("day/$today") },
                        onOpenAppointment = { nav.navigate("appointment?id=${it.id}") },
                        onOpenTreatments = { nav.navigate("treatments") { launchSingleTop = true } },
                        pointsSignal = pointsSignal,
                        notificationsBlocked = !notificationsAllowed,
                        onFixNotifications = ::fixNotifications,
                        onCuddle = vm::cuddle,
                        onGiveEarly = vm::giveEarly,
                        onLogPrn = { nav.navigate("prn_log/${it.id}") },
                        onOpenPrnIntake = { nav.navigate("prn_log/${it.treatmentId}?intake=${it.id}") },
                        onOpenSleep = { nav.navigate("sleep_log") },
                    )
                }
                composable("treatments") {
                    val tabs = if (menstruating || hasPill) listOf("Médicaments", "Pilule") else listOf("Médicaments")
                    Hub("Traitements", tabs, 0, openProfile, openSettings) { i ->
                        when (i) {
                            0 -> TreatmentsScreen(
                                state.treatments, today,
                                onAdd = { nav.navigate("search") },
                                onEdit = { nav.navigate(if (it.pill != null) "pill_setup" else "editor?treatment=${it.id}") },
                                embedded = true,
                                onRefill = { vm.refill(it.id) },
                                lastIntakes = lastIntakes,
                                now = now,
                                onLogPrn = { nav.navigate("prn_log/${it.id}") },
                                onPrnStats = { nav.navigate("prn_stats/${it.id}") },
                            )
                            else -> PillScreen(
                                state, now,
                                onSetup = { nav.navigate("pill_setup") },
                                onTake = vm::give,
                                onNewPack = { date -> state.treatments.firstOrNull { it.pill != null }?.let { vm.startNewPillPack(it.id, date) } },
                                onTakeEarly = vm::giveEarly,
                            )
                        }
                    }
                }
                composable("suivi") {
                    val tabs = if (menstruating) listOf("Journal", "Sommeil", "Cycle", "Stats") else listOf("Journal", "Sommeil", "Stats")
                    Hub("Suivi", tabs, 0, openProfile, openSettings) { i ->
                        when (tabs[i]) {
                            "Journal" -> JournalScreen(state.journal, today) { nav.navigate("day/$it") }
                            "Sommeil" -> SleepScreen(
                                state, now,
                                onLog = { nav.navigate(if (state.sleep.any { it.date == today }) "sleep_log?new=true" else "sleep_log") },
                                onOpen = { nav.navigate("sleep_log?date=${it.date}") },
                            )
                            "Cycle" -> CycleScreen(
                                state.profile?.cycle, state.journal, today, vm::logPeriod, vm::updateCycle,
                                onOpenDay = { nav.navigate("day/$it") }, embedded = true,
                            )
                            else -> StatsScreen(state, now, embedded = true, onOpenPrn = { nav.navigate("prn_stats/$it") })
                        }
                    }
                }
                composable("sante") {
                    Hub("Santé", listOf("Agenda", "Documents"), 0, openProfile, openSettings) { i ->
                        if (i == 0) {
                            AgendaScreen(
                                state.appointments, now,
                                onNew = { date -> nav.navigate("appointment?date=${date ?: today}") },
                                onOpen = { nav.navigate("appointment?id=${it.id}") },
                            )
                        } else {
                            DocumentsScreen(
                                state.documents,
                                onTakePhoto = {
                                    val file = DocumentStore.newPhotoFile(context)
                                    pendingPhoto = file.name
                                    takePicture.launch(DocumentStore.uriFor(context, file))
                                },
                                onImport = { importFile.launch(arrayOf("application/pdf", "image/*")) },
                                onOpen = { nav.navigate("document/${it.id}") },
                            )
                        }
                    }
                }
                composable("shop") { ShopScreen(state.game, vm::buy, vm::equip) }

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
                composable(
                    "prn_log/{treatment}?intake={intake}",
                    arguments = listOf(
                        navArgument("treatment") { type = NavType.StringType },
                        navArgument("intake") { type = NavType.StringType; nullable = true },
                    ),
                ) { entry ->
                    val t = state.treatments.firstOrNull { it.id == entry.arguments?.getString("treatment") }
                    val intakeId = entry.arguments?.getString("intake")
                    val intake = intakeId?.let { id -> state.prnIntakes.firstOrNull { it.id == id } }
                    if (t == null || (intakeId != null && intake == null)) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        PrnLogScreen(
                            state, t, intake, now,
                            onSave = {
                                vm.savePrnIntake(it)
                                vm.message(if (intake == null) "Prise de ${t.name} notée" else "Prise mise à jour")
                                nav.popBackStack()
                            },
                            onDelete = {
                                intake?.let { vm.deletePrnIntake(it.id) }
                                nav.popBackStack()
                            },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("prn_stats/{treatment}") { entry ->
                    val t = state.treatments.firstOrNull { it.id == entry.arguments?.getString("treatment") }
                    if (t == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        PrnStatsScreen(
                            state, t, now,
                            onBack = { nav.popBackStack() },
                            onLog = { nav.navigate("prn_log/${t.id}") },
                            onEditIntake = { nav.navigate("prn_log/${t.id}?intake=${it.id}") },
                            onEditTreatment = { nav.navigate("editor?treatment=${t.id}") },
                        )
                    }
                }
                composable(
                    "sleep_log?date={date}&new={new}",
                    arguments = listOf(
                        navArgument("date") { type = NavType.StringType; nullable = true },
                        navArgument("new") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { entry ->
                    val asked = entry.arguments?.getString("date")?.let(LocalDate::parse)
                    // "Another night": the most recent day without one.
                    val date = asked ?: if (entry.arguments?.getBoolean("new") == true) {
                        generateSequence(today) { it.minusDays(1) }.take(60).firstOrNull { d -> state.sleep.none { it.date == d } } ?: today
                    } else today
                    val existing = remember(date) { state.sleep.firstOrNull { it.date == date } }
                    SleepEditorScreen(
                        state, existing, date, today,
                        onSave = { night ->
                            if (existing != null && existing.date != night.date) vm.deleteSleep(existing.date)
                            vm.saveSleep(night)
                            nav.popBackStack()
                        },
                        onDelete = {
                            existing?.let { vm.deleteSleep(it.date) }
                            nav.popBackStack()
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("pill_setup") {
                    val existing = state.treatments.firstOrNull { it.pill != null }
                    PillSetupScreen(
                        existing, today,
                        onSave = { vm.saveTreatment(it); nav.popBackStack() },
                        onDelete = { existing?.let { vm.deleteTreatment(it.id) }; nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    )
                }

                // ── Journal ──
                composable("day/{date}") { entry ->
                    val date = entry.arguments?.getString("date")?.let(LocalDate::parse) ?: today
                    DayEntryScreen(
                        entry = state.entryFor(date),
                        showCycle = menstruating,
                        onSave = { vm.saveEntry(it); nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    )
                }

                // ── Appointments ──
                composable(
                    "appointment?id={id}&date={date}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; nullable = true },
                        navArgument("date") { type = NavType.StringType; nullable = true },
                    ),
                ) { entry ->
                    val existing = entry.arguments?.getString("id")?.let { id -> state.appointments.firstOrNull { it.id == id } }
                    val date = entry.arguments?.getString("date")?.let(LocalDate::parse) ?: today
                    AppointmentEditorScreen(
                        existing, date,
                        onSave = { vm.saveAppointment(it); nav.popBackStack() },
                        onDelete = { existing?.let { vm.deleteAppointment(it.id) }; nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    )
                }

                // ── Health record ──
                composable(
                    "doc_info?file={file}&mime={mime}&id={id}",
                    arguments = listOf(
                        navArgument("file") { type = NavType.StringType; nullable = true },
                        navArgument("mime") { type = NavType.StringType; nullable = true },
                        navArgument("id") { type = NavType.StringType; nullable = true },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    val existing = args?.getString("id")?.let { id -> state.documents.firstOrNull { it.id == id } }
                    val doc = existing ?: HealthDocument(
                        id = UUID.randomUUID().toString(),
                        title = DocCategory.PRESCRIPTION.label,
                        category = DocCategory.PRESCRIPTION,
                        date = today,
                        fileName = args?.getString("file").orEmpty(),
                        mimeType = args?.getString("mime") ?: "image/jpeg",
                    )
                    val newDoc = remember { doc }
                    DocumentInfoScreen(
                        existing ?: newDoc, isNew = existing == null,
                        onSave = { vm.saveDocument(it); nav.popBackStack() },
                        onCancel = {
                            if (existing == null) DocumentStore.delete(context, newDoc)
                            nav.popBackStack()
                        },
                    )
                }
                composable("document/{id}") { entry ->
                    val doc = state.documents.firstOrNull { it.id == entry.arguments?.getString("id") }
                    if (doc != null) {
                        DocumentViewerScreen(
                            doc,
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate("doc_info?id=${doc.id}") },
                            onShare = { context.startActivity(DocumentStore.shareIntent(context, doc)) },
                            onDelete = { vm.deleteDocument(doc); nav.popBackStack() },
                        )
                    }
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
                                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                                )
                            }
                        },
                        onRenamePet = vm::namePet,
                        onOpenProfile = openProfile,
                        onReset = {
                            vm.resetAll()
                            nav.navigate("welcome") { popUpTo(0) { inclusive = true } }
                        },
                        onTestNotifications = {
                            if (!Notifications.canPost(context)) {
                                fixNotifications()
                            } else {
                                Notifications.showTest(context, state)
                                ReminderScheduler.scheduleTest(context)
                                vm.message("Notification envoyée. Une autre arrivera dans 1 minute.")
                            }
                        },
                        onOpenNotificationSettings = ::fixNotifications,
                        batteryRestricted = batteryRestricted,
                        onOpenBatterySettings = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                    )
                }
                composable("profile") {
                    state.profile?.let { p -> ProfileScreen(p, today, onBack = { nav.popBackStack() }, onSave = vm::saveProfile) }
                }
            }
        }

        checkIn?.let { event -> CheckInDialog(event, pet) { checkIn = null } }
    }
}

private fun isBatteryRestricted(context: android.content.Context): Boolean =
    !context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

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
                    append("Quel plaisir pour $pet de te revoir : +${r.points} points.")
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
