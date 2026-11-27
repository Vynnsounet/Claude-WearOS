package com.medipote.app.ui

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.net.toUri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
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
import com.medipote.app.ui.sleep.NapEditorScreen
import java.time.LocalDateTime
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab("home", "Accueil", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab("treatments", "Soins", Icons.Outlined.Medication, Icons.Rounded.Medication),
    Tab("suivi", "Suivi", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    Tab("mieux", "Mieux-être", Icons.Outlined.SelfImprovement, Icons.Rounded.SelfImprovement),
    Tab("moi", "Moi", Icons.Outlined.Face, Icons.Rounded.Face),
)

@Composable
fun MediPoteApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    MediPoteTheme(
        state.settings.themeMode, state.settings.dynamicColor, state.settings.accent, state.settings.look,
        season = state.settings.seasons.takeIf { it.enabled && it.tint }?.let { com.medipote.app.domain.Seasons.of(now.toLocalDate(), it.southern) },
    ) {
        val context = LocalContext.current
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        var feedSignal by remember { mutableIntStateOf(0) }
        var lastPoints by remember { mutableIntStateOf(0) }
        var pointsSignal by remember { mutableIntStateOf(0) }
        var askedThisSession by rememberSaveable { mutableStateOf(false) }
        var batteryRestricted by remember { mutableStateOf(isBatteryRestricted(context)) }
        val checkIn by vm.checkIn.collectAsStateWithLifecycle()
        val badgeQueue = remember { androidx.compose.runtime.mutableStateListOf<com.medipote.app.domain.Badge>() }
        var notificationsAllowed by remember { mutableStateOf(Notifications.canPost(context)) }
        var quietChannels by remember { mutableStateOf(Notifications.quietChannels(context)) }
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
            quietChannels = Notifications.quietChannels(context)
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

        val sober by androidx.compose.runtime.rememberUpdatedState(state.settings.sober)
        LaunchedEffect(Unit) {
            vm.events.collect { e ->
                when (e) {
                    is UiEvent.Points -> {
                        lastPoints = e.amount
                        if (!sober) pointsSignal++
                    }
                    is UiEvent.Fed -> {
                        feedSignal++
                        if (!sober) pointsSignal++
                        lastPoints = e.points + e.perfectDayBonus
                        val base = when (e.status) {
                            TakeStatus.ON_TIME -> "Bravo ! $pet a pris ${e.treatmentName} à l'heure : +${e.points} pts"
                            TakeStatus.LATE -> "${e.treatmentName} donné en retard : +${e.points} pts"
                            TakeStatus.VERY_LATE -> "${e.treatmentName} donné très en retard : +${e.points} pts"
                            TakeStatus.EARLY -> "${e.treatmentName} noté comme pris en avance : +${e.points} pts"
                        }
                        val bonus = if (e.perfectDayBonus > 0) " · Journée parfaite : +${e.perfectDayBonus} !" else ""
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(if (sober) com.medipote.app.domain.Sober.clean(base) ?: "Prise notée" else base + bonus)
                    }
                    is UiEvent.CheckedIn -> Unit // shown from vm.checkIn
                    is UiEvent.BadgeEarned -> if (!sober) badgeQueue.add(e.badge)
                    is UiEvent.Bought -> {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("${e.item.name} acheté et équipé !")
                    }
                    is UiEvent.Message -> {
                        val text = if (sober) com.medipote.app.domain.Sober.clean(e.text) else e.text
                        if (text != null) {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar(text)
                        }
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
            // "journal_today" comes from a launcher shortcut: the day it means is today's.
            nav.navigate(if (r == "journal_today") "day/${Rules.gameDay(vm.now.value)}" else r) { launchSingleTop = true }
            vm.routeHandled()
        }
        // The long-press shortcuts follow what is turned on.
        LaunchedEffect(state.settings.wellbeing.enabled) { com.medipote.app.Shortcuts.update(context, state.settings.wellbeing.enabled) }

        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route?.substringBefore('?')
        val showBar = TABS.any { it.route == route }
        val openProfile = { nav.navigate("profile") }
        // Chosen once: finishing the first run must not send the app back to "home" before the tour.
        val startDestination = remember { if (state.onboarded) "home" else "welcome" }
        val openSettings = { nav.navigate("settings") }
        val openSearch = { nav.navigate("find") }
        val hiddenCards = HubCatalog.hidden(state.settings)
        val nextAppointment = state.appointments.filter { !it.dateTime.isBefore(now) }.minByOrNull { it.dateTime }
        val hubTools = HubTools(
            usage = state.settings.usage, byUse = state.settings.cardsByUse, pins = state.settings.pins,
            onOpened = vm::cardOpened, onTogglePin = vm::togglePin, onKeep = vm::keepCard,
            onPutAway = { key -> vm.setCardHidden(key, true); vm.message("Rangé : tu la retrouves avec la loupe") },
        )
        // A search result and a pinned shortcut lead to the same place: one way to open anything.
        val openEntry: (com.medipote.app.domain.SearchEntry) -> Unit = { entry ->
            when (val t = entry.target) {
                is com.medipote.app.domain.SearchTarget.Action -> if (t.id == "water") vm.addWater(1)
                is com.medipote.app.domain.SearchTarget.Route -> {
                    val route = when (t.route) {
                        "journal_today" -> "day/$today"
                        // The idea box remembers where the person was.
                        "ideas" -> "ideas?from=${Uri.encode(nav.currentBackStackEntry?.destination?.route.orEmpty())}"
                        else -> t.route
                    }
                    val tab = route.substringBefore('?')
                    nav.navigate(route) {
                        // A tab opens fresh, on its card; anything else is a screen on top.
                        if (TABS.any { it.route == tab }) popUpTo("home")
                        launchSingleTop = true
                    }
                }
            }
        }
        // The heart is in every bar unless the person hid it: asking for help never needs a search.
        val openSupport: (() -> Unit)? = if (state.settings.support.homeButton) ({ nav.navigate("support") }) else null
        // "I wake up": noted at once, unless the timer was forgotten (then the duration is fixed by hand).
        val wakeNap: () -> Unit = {
            val r = vm.stopNap()
            if (r is com.medipote.app.domain.NapStop.TooLong) nav.navigate("nap_log?start=${Uri.encode(r.start.toString())}&minutes=60")
        }

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
                startDestination = startDestination,
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
                        nav.navigate("setup_wellbeing")
                    }
                }
                composable("setup_wellbeing") {
                    com.medipote.app.ui.wellbeing.ConditionsScreen(
                        state.settings.wellbeing, onChange = vm::updateWellbeing, onboarding = true,
                        onBack = { nav.navigate("setup_treatments") },
                    )
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
                            nav.navigate("tour?first=true") { popUpTo(0) { inclusive = true } }
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
                        onOpenSearch = openSearch,
                        onOpenCheckup = { nav.navigate("suivi?card=suivi:checkup") { popUpTo("home") } },
                        onOpenPause = { nav.navigate("settings?section=reminders") },
                        onOpenAfter = { nav.navigate("appointment_after/${it.id}") },
                        onOpenAgenda = { nav.navigate("treatments?card=soins:agenda") { popUpTo("home") } },
                        onOpenTravel = { nav.navigate("travel") },
                        onOpenShortcut = { id -> com.medipote.app.domain.AppSearch.CATALOG.firstOrNull { it.id == id }?.let(openEntry) },
                        onUnpin = vm::togglePin,
                        onAddWater = vm::addWater,
                        onAddDrink = vm::addDrink,
                        onOpenVaccines = { nav.navigate("treatments?card=soins:vaccines") { popUpTo("home") } },
                        onApplyHabit = vm::applyHabit,
                        onDismissHabit = vm::dismissHabit,
                        onRemoveDrink = vm::removeDrink,
                        onOpenJournal = { nav.navigate("day/$today") },
                        onOpenAppointment = { nav.navigate("appointment?id=${it.id}") },
                        onOpenTreatments = { nav.navigate("treatments") { launchSingleTop = true } },
                        pointsSignal = pointsSignal,
                        notificationsBlocked = !notificationsAllowed,
                        remindersQuiet = notificationsAllowed && Notifications.CHANNEL_DOSES in quietChannels,
                        onFixQuietReminders = { context.startActivity(Notifications.channelSettingsIntent(context)) },
                        onFixNotifications = ::fixNotifications,
                        onCuddle = vm::cuddle,
                        onGiveEarly = vm::giveEarly,
                        onLogPrn = { nav.navigate("prn_log/${it.id}") },
                        onOpenPrnIntake = { nav.navigate("prn_log/${it.treatmentId}?intake=${it.id}") },
                        onOpenSleep = { nav.navigate("sleep_log") },
                        onAddMeasure = { nav.navigate("measure_log/${it.name}") },
                        onOpenMeasure = { nav.navigate("measure/${it.name}") },
                        onOpenDocument = { nav.navigate("document/${it.id}") },
                        onOpenHistory = { nav.navigate("history/$it") },
                        onMood = vm::setMood,
                        onRitual = { nav.navigate("ritual/${it.name}") },
                        onOpenBadges = { nav.navigate("badges") },
                        onOpenRecap = { nav.navigate("recap/$it") },
                        onProductChanged = vm::productChanged,
                        onProductRemoved = vm::productRemoved,
                        onProductStart = vm::startProduct,
                        onOpenHomeLayout = { nav.navigate("home_layout") },
                        onNapWake = wakeNap,
                        onNapCancel = vm::cancelNap,
                        onOpenSupport = { nav.navigate("support") },
                        onOpenSoftDay = { nav.navigate("softday") },
                        quickAdd = com.medipote.app.ui.home.QuickAddActions(
                            onLogPrn = { nav.navigate("prn_log/${it.id}") },
                            onAddPrnTreatment = { nav.navigate("search") },
                            onMood = vm::setMood,
                            onSleep = { nav.navigate("sleep_log") },
                            onNapStart = vm::startNap,
                            onNapWake = wakeNap,
                            onNapLog = { nav.navigate("nap_log") },
                            onSupport = { nav.navigate("support") },
                            onWater = { vm.addWater(1) },
                            onProduct = vm::startProduct,
                            onProductChanged = vm::productChanged,
                            onMeasure = { nav.navigate("measure_log/${it.name}") },
                            onJournal = { nav.navigate("day/$today") },
                            onAppointment = { nav.navigate("appointment?date=$today") },
                            onSymptoms = { nav.navigate("symptoms") },
                            onSoftDay = { nav.navigate("softday") },
                        ),
                    )
                }
                composable(
                    "treatments?card={card}",
                    arguments = listOf(navArgument("card") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { cardEntry ->
                    // Soins: everything about care, medication first.
                    val cards = listOfNotNull(
                        HubCard("soins:treatments", "💊", "Traitements", HubPreviews.treatments(state, now)),
                        HubCard("soins:pill", "🔁", "Pilule", "Plaquette, pauses et rappels").takeIf { menstruating || hasPill },
                        HubCard("soins:agenda", "📅", "Agenda", HubPreviews.agenda(state, now)),
                        HubCard("soins:docs", "📂", "Carnet de santé", HubPreviews.documents(state)),
                        HubCard("soins:vaccines", "💉", "Vaccins", HubPreviews.vaccines(state, today)),
                    )
                    CardHub("Soins", cards, hiddenCards, openProfile, openSettings, { nav.navigate("cards_layout") }, onSearch = openSearch, onSupport = openSupport, initialOpen = cardEntry.arguments?.getString("card"), tools = hubTools) { key ->
                        when (key) {
                            "soins:treatments" -> TreatmentsScreen(
                                state.treatments, today,
                                onAdd = { nav.navigate("search") },
                                onEdit = { nav.navigate(if (it.pill != null) "pill_setup" else "editor?treatment=${it.id}") },
                                embedded = true,
                                onRefill = { vm.refill(it.id) },
                                lastIntakes = lastIntakes,
                                now = now,
                                onLogPrn = { nav.navigate("prn_log/${it.id}") },
                                onPrnStats = { nav.navigate("prn_stats/${it.id}") },
                                onPharmacy = { nav.navigate("pharmacy") }.takeIf { com.medipote.app.domain.Pharmacy.list(state, today).isNotEmpty() },
                            )
                            "soins:pill" -> PillScreen(
                                state, now,
                                onSetup = { nav.navigate("pill_setup") },
                                onTake = vm::give,
                                onNewPack = { date -> state.treatments.firstOrNull { it.pill != null }?.let { vm.startNewPillPack(it.id, date) } },
                                onTakeEarly = vm::giveEarly,
                            )
                            "soins:agenda" -> AgendaScreen(
                                state.appointments, now,
                                onNew = { date -> nav.navigate("appointment?date=${date ?: today}") },
                                onOpen = { nav.navigate("appointment?id=${it.id}") },
                                checkups = state.checkups,
                                onCheckupDone = vm::checkupDone, onCheckupSave = vm::saveCheckup,
                                onCheckupAdd = vm::addCheckup, onCheckupDelete = vm::deleteCheckup,
                            )
                            "soins:vaccines" -> com.medipote.app.ui.vaccines.VaccinesScreen(
                                state, today,
                                onAdd = vm::addVaccination,
                                onRemove = vm::removeVaccination,
                                onProfile = vm::setVaccineProfile,
                                onReminders = vm::setVaccineReminders,
                            )
                            else -> DocumentsScreen(
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
                composable(
                    "suivi?card={card}",
                    arguments = listOf(navArgument("card") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { cardEntry ->
                    // Suivi: what's noted day after day.
                    val cards = listOfNotNull(
                        HubCard("suivi:journal", "📖", "Journal", HubPreviews.journal(state, now)),
                        HubCard("suivi:calendar", "🗓️", "Calendrier", "Tes prises, ton humeur et tes symptômes, mois par mois"),
                        HubCard("suivi:labs", "🧪", "Analyses", HubPreviews.labs(state)),
                        HubCard("suivi:timeline", "🕰️", "Ma période", HubPreviews.timeline(state, today)),
                        HubCard("suivi:checkup", "🔎", "Comprendre", HubPreviews.checkup(state, today)),
                        HubCard("suivi:sleep", "🌙", "Sommeil", HubPreviews.sleep(state, now)),
                        HubCard("suivi:measures", "🩺", "Mesures", HubPreviews.measures(state)).takeIf { state.settings.measures.isNotEmpty() },
                        HubCard("suivi:cycle", "🩸", "Cycle", "Règles, cycle et protections").takeIf { menstruating },
                        HubCard("suivi:symptoms", "🌡️", "Symptômes", HubPreviews.symptoms(state, now)).takeIf { com.medipote.app.domain.Wellbeing.enabled(state) },
                    )
                    CardHub("Suivi", cards, hiddenCards, openProfile, openSettings, { nav.navigate("cards_layout") }, onSearch = openSearch, onSupport = openSupport, initialOpen = cardEntry.arguments?.getString("card"), tools = hubTools) { key ->
                        when (key) {
                            "suivi:journal" -> JournalScreen(
                                state.journal, today,
                                onOpenDay = { nav.navigate("day/$it") },
                                onOpenMyDay = { nav.navigate("myday/$it") },
                            )
                            "suivi:calendar" -> com.medipote.app.ui.journal.MonthScreen(state, today, onOpenDay = { nav.navigate("myday/$it") })
                            "suivi:labs" -> com.medipote.app.ui.labs.LabsScreen(
                                state,
                                onOpen = { nav.navigate("lab/${Uri.encode(it)}") },
                                onAdd = { nav.navigate("lab_edit") },
                            )
                            "suivi:timeline" -> com.medipote.app.ui.timeline.TimelineScreen(
                                state, today,
                                onOpenLab = { nav.navigate("lab/${Uri.encode(it)}") },
                                onOpenTreatment = { nav.navigate(if (state.treatments.firstOrNull { t -> t.id == it }?.pill != null) "pill_setup" else "editor?treatment=$it") },
                            )
                            "suivi:checkup" -> com.medipote.app.ui.understand.CheckupScreen(
                                state, today,
                                onCall = { com.medipote.app.ui.support.dial(context, it) },
                                onBook = { nav.navigate("appointment?date=$today") },
                                onPrepare = nextAppointment?.let { a -> { nav.navigate("appointment_prep/${a.id}") } },
                                onDismiss = vm::dismissCheck,
                                onOpenSymptom = { nav.navigate("symptom/${Uri.encode(it)}") },
                                onBack = {}, embedded = true,
                            )
                            "suivi:measures" -> com.medipote.app.ui.measures.MeasuresScreen(
                                state, now,
                                onAdd = { nav.navigate("measure_log/${it.name}") },
                                onOpen = { nav.navigate("measure/${it.name}") },
                                onSettings = openSettings,
                            )
                            "suivi:sleep" -> SleepScreen(
                                state, now,
                                onLog = { nav.navigate(if (state.sleep.any { it.date == today }) "sleep_log?new=true" else "sleep_log") },
                                onOpen = { nav.navigate("sleep_log?date=${it.date}") },
                                onNapStart = vm::startNap,
                                onNapWake = wakeNap,
                                onNapCancel = vm::cancelNap,
                                onNapLog = { nav.navigate("nap_log") },
                                onOpenNap = { nav.navigate("nap_log?id=${it.id}") },
                                onNightHelp = { nav.navigate("sleep_help") },
                                onBlankNight = vm::saveBlankNight,
                                onEvening = vm::setEvening,
                                onSleepSettings = { t -> vm.updateSettings { it.copy(sleep = t(it.sleep)) } },
                            )
                            "suivi:symptoms" -> com.medipote.app.ui.wellbeing.SymptomsScreen(
                                state, now,
                                onSet = vm::setSymptom,
                                onAdvice = { nav.navigate("symptom/${Uri.encode(it)}") },
                                onManage = { nav.navigate("wellbeing_me") },
                                onSoftDay = { nav.navigate("softday") },
                                onZones = vm::setSymptomZones,
                            )
                            else -> {
                                // During the period, protection and medication come first; the rest of the month, after the cycle.
                                val periodCards: @Composable () -> Unit = {
                                    com.medipote.app.ui.cycle.PeriodCareCard(
                                        state, now,
                                        onStart = vm::startProduct, onChanged = vm::productChanged, onRemoved = vm::productRemoved,
                                        onSetReminder = vm::setProductReminder,
                                    )
                                    val periodLog = { id: String ->
                                        nav.navigate("prn_log/$id?reason=${Uri.encode(com.medipote.app.domain.PeriodCare.REASON)}&context=${Uri.encode(com.medipote.app.domain.PeriodCare.CONTEXT)}")
                                    }
                                    com.medipote.app.ui.cycle.PeriodMedsCard(
                                        state, now,
                                        onLog = periodLog,
                                        onAddCommon = { drugId -> vm.addPeriodMedication(drugId)?.let(periodLog) },
                                        onAddCustom = { name, strength, drugId -> vm.addCustomPeriodMedication(name, strength, drugId) },
                                        onSetListed = vm::setPeriodMed,
                                    )
                                }
                                val inPeriod = com.medipote.app.domain.PeriodCare.inPeriod(state, today)
                                CycleScreen(
                                    state.profile?.cycle, state.journal, today, vm::logPeriod, vm::updateCycle,
                                    onOpenDay = { nav.navigate("day/$it") }, embedded = true,
                                    header = { if (inPeriod) periodCards() },
                                    footer = {
                                        if (!inPeriod) periodCards()
                                        com.medipote.app.ui.understand.AdviceCard("🌸  Pour ton cycle, en ce moment", com.medipote.app.domain.Understand.cycleAdvice(state, today))
                                    },
                                )
                            }
                        }
                    }
                }
                composable(
                    "mieux?card={card}",
                    arguments = listOf(navArgument("card") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { cardEntry ->
                    // Mieux-être: body and mind together, what helps on a hard day.
                    val wellbeing = com.medipote.app.domain.Wellbeing.enabled(state)
                    val cards = listOfNotNull(
                        HubCard("mieux:softday", "💛", "Journée douce", HubPreviews.softDay(state, now)).takeIf { wellbeing },
                        HubCard("mieux:feel", "💭", "Ce que je ressens", "Anxiété, angoisse, moral bas : ce qui peut aider") { nav.navigate("support_feel") },
                        HubCard("mieux:rituals", "✨", "Rituels du matin et du soir", "Deux petits moments pour commencer et finir la journée") {
                            nav.navigate("ritual/${(com.medipote.app.domain.Rituals.current(now) ?: com.medipote.app.domain.RitualKind.MORNING).name}")
                        },
                        HubCard("mieux:me", "🌿", "Ce qui m'accompagne", HubPreviews.me(state)) { nav.navigate("wellbeing_me") },
                    )
                    CardHub(
                        "Mieux-être", cards, hiddenCards, openProfile, openSettings, { nav.navigate("cards_layout") },
                        onSearch = openSearch, onSupport = openSupport, initialOpen = cardEntry.arguments?.getString("card"), tools = hubTools,
                        intro = if (wellbeing) null else {
                            {
                                androidx.compose.material3.Surface(
                                    onClick = { nav.navigate("wellbeing_me") },
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                                        Text("💛  Pour les jours où ça pèse", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Ajoute ce que tu vis : tu auras le suivi de tes symptômes, des petites missions et des conseils adaptés à toi.",
                                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        },
                    ) { key ->
                        com.medipote.app.ui.wellbeing.SoftDayScreen(
                            state, now,
                            onEnergy = vm::setEnergy,
                            onDo = vm::doMission,
                            onNewTask = { nav.navigate("task_new") },
                            onOpenTask = { nav.navigate("task/$it") },
                            onSupport = { nav.navigate("support") },
                            onMine = { nav.navigate("wellbeing_me") },
                        )
                    }
                }
                composable(
                    "moi?card={card}",
                    arguments = listOf(navArgument("card") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { cardEntry ->
                    // Moi: looking back, and the companion's side.
                    val cards = listOf(
                        HubCard("moi:stats", "📊", "Statistiques", "${state.game.streak} jour${if (state.game.streak > 1) "s" else ""} de suite · prises, nuits, humeur"),
                        HubCard("moi:year", "📆", "Mon année", "Ton année en couleurs"),
                        HubCard("moi:shop", "🛍️", "Boutique", "${state.game.points} pts à dépenser pour $pet"),
                        HubCard("moi:badges", "🏅", "Badges et défis", "${state.game.badges.size} badge${if (state.game.badges.size > 1) "s" else ""} gagné${if (state.game.badges.size > 1) "s" else ""}"),
                    )
                    CardHub("Moi", cards, hiddenCards, openProfile, openSettings, { nav.navigate("cards_layout") }, onSearch = openSearch, onSupport = openSupport, initialOpen = cardEntry.arguments?.getString("card"), tools = hubTools) { key ->
                        when (key) {
                            "moi:stats" -> StatsScreen(state, now, embedded = true, onOpenPrn = { nav.navigate("prn_stats/$it") }, onReport = { nav.navigate("report") }, onManageSymptoms = { nav.navigate("wellbeing_me") })
                            "moi:year" -> com.medipote.app.ui.motivation.YearScreen(state, now, onOpenDay = { nav.navigate("myday/$it") }, onBack = {}, embedded = true)
                            "moi:shop" -> ShopScreen(state.game, vm::buy, vm::equip, embedded = true)
                            else -> com.medipote.app.ui.motivation.BadgesScreen(state, today, onBack = {}, embedded = true)
                        }
                    }
                }

                // ── Hard days: symptoms, small missions, tasks cut into steps ──
                composable("symptoms") {
                    com.medipote.app.ui.wellbeing.SymptomsScreen(
                        state, now,
                        onSet = vm::setSymptom,
                        onAdvice = { nav.navigate("symptom/${Uri.encode(it)}") },
                        onManage = { nav.navigate("wellbeing_me") },
                        onSoftDay = { nav.navigate("softday") },
                        embedded = false,
                        onBack = { nav.popBackStack() },
                        onZones = vm::setSymptomZones,
                    )
                }
                composable("symptom/{id}") { entry ->
                    com.medipote.app.ui.wellbeing.SymptomAdviceScreen(
                        entry.arguments?.getString("id").orEmpty(),
                        onSoftDay = { nav.navigate("softday") },
                        onSupport = { nav.navigate("support") },
                        onBack = { nav.popBackStack() },
                        factors = com.medipote.app.domain.Understand.factors(state, entry.arguments?.getString("id").orEmpty(), today),
                        concerns = com.medipote.app.domain.Understand.visible(
                            com.medipote.app.domain.Understand.symptomConcerns(state, entry.arguments?.getString("id").orEmpty(), today),
                            state.settings.dismissedChecks, today,
                        ),
                        onCall = { com.medipote.app.ui.support.dial(context, it) },
                        onBook = { nav.navigate("appointment?date=$today") },
                        onPrepare = nextAppointment?.let { a -> { nav.navigate("appointment_prep/${a.id}") } },
                        onDismiss = vm::dismissCheck,
                        tried = entry.arguments?.getString("id").orEmpty() in com.medipote.app.domain.Wellbeing.triedToday(state, today, "advice"),
                        onTried = {
                            val id = entry.arguments?.getString("id").orEmpty()
                            vm.tried("advice", id, "J'ai essayé un conseil : ${com.medipote.app.domain.Wellbeing.name(id).lowercase()}")
                        },
                    )
                }
                composable("softday") {
                    com.medipote.app.ui.wellbeing.SoftDayScreen(
                        state, now,
                        onEnergy = vm::setEnergy,
                        onDo = vm::doMission,
                        onNewTask = { nav.navigate("task_new") },
                        onOpenTask = { nav.navigate("task/$it") },
                        onSupport = { nav.navigate("support") },
                        onMine = { nav.navigate("wellbeing_me") },
                        embedded = false,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("task_new") {
                    com.medipote.app.ui.wellbeing.TaskEditScreen(
                        onSave = { title, steps ->
                            val id = vm.saveTask(null, title, steps)
                            nav.navigate("task/$id") { popUpTo("task_new") { inclusive = true } }
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("task/{id}") { entry ->
                    val id = entry.arguments?.getString("id")
                    com.medipote.app.ui.wellbeing.TaskScreen(
                        state.tasks.firstOrNull { it.id == id },
                        onTick = { index -> id?.let { vm.completeStep(it, index) } },
                        onDelete = { id?.let(vm::deleteTask); nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("wellbeing_me") {
                    com.medipote.app.ui.wellbeing.ConditionsScreen(state.settings.wellbeing, onChange = vm::updateWellbeing, onBack = { nav.popBackStack() })
                }
                composable("cards_layout") {
                    com.medipote.app.ui.settings.CardsScreen(
                        state, onToggle = vm::setCardHidden, onReset = vm::showAllCards, onBack = { nav.popBackStack() },
                        onByUse = { on -> vm.updateSettings { it.copy(cardsByUse = on) } }, onUnpin = vm::togglePin,
                    )
                }

                // ── Treatments flow ──
                composable("search") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    var scanMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
                    fun open(box: com.medipote.app.domain.MedicineBox) {
                        val strength = Uri.encode(box.strength)
                        val target = box.drug?.let { "editor?drug=${it.id}&strength=$strength" }
                            ?: "editor?custom=${Uri.encode(com.medipote.app.domain.Medicines.displayName(box.denomination))}&strength=$strength"
                        nav.navigate(target) { popUpTo("search") { inclusive = true } }
                    }
                    DrugSearchScreen(
                        onBack = { nav.popBackStack() },
                        onPick = { nav.navigate("editor?drug=${it.id}") { popUpTo("search") { inclusive = true } } },
                        onCustom = { nav.navigate("editor?custom=${Uri.encode(it)}") { popUpTo("search") { inclusive = true } } },
                        onScan = {
                            scanMessage = null
                            com.medipote.app.ui.treatments.BoxScanner.scan(
                                context,
                                onCode = { raw ->
                                    scope.launch {
                                        val directory = withContext(kotlinx.coroutines.Dispatchers.IO) { com.medipote.app.domain.MedicineDirectoryStore.get(context) }
                                        val box = directory.identify(raw)
                                        if (box != null) open(box)
                                        else scanMessage = if (com.medipote.app.domain.CipCode.parse(raw) == null) "Ce code n'est pas celui d'une boîte de médicament. Essaie le code carré ou les barres." else "Cette boîte n'est pas dans la liste du téléphone : cherche-la par son nom."
                                    }
                                },
                                onFail = { scanMessage = "Le scanner n'a pas pu s'ouvrir. Cherche le médicament par son nom." },
                            )
                        },
                        scanMessage = scanMessage,
                    )
                }
                composable(
                    "editor?drug={drug}&custom={custom}&treatment={treatment}&strength={strength}",
                    arguments = listOf(
                        navArgument("drug") { type = NavType.StringType; nullable = true },
                        navArgument("custom") { type = NavType.StringType; nullable = true },
                        navArgument("treatment") { type = NavType.StringType; nullable = true },
                        navArgument("strength") { type = NavType.StringType; nullable = true },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    val existing = args?.getString("treatment")?.let { id -> state.treatments.firstOrNull { it.id == id } }
                    val drug = (existing?.drugId ?: args?.getString("drug"))?.let(DrugGuide::byId)
                    TreatmentEditorScreen(
                        existing = existing,
                        drug = drug,
                        customName = args?.getString("custom").orEmpty(),
                        presetStrength = args?.getString("strength").orEmpty(),
                        allergies = state.profile?.allergies.orEmpty(),
                        today = today,
                        others = state.treatments,
                        routine = state.settings.routine,
                        onRoutine = { t -> vm.updateSettings { it.copy(routine = t(it.routine)) } },
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
                    "prn_log/{treatment}?intake={intake}&reason={reason}&context={context}",
                    arguments = listOf(
                        navArgument("treatment") { type = NavType.StringType },
                        navArgument("intake") { type = NavType.StringType; nullable = true },
                        navArgument("reason") { type = NavType.StringType; nullable = true },
                        navArgument("context") { type = NavType.StringType; nullable = true },
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
                            presetReason = entry.arguments?.getString("reason").orEmpty(),
                            presetContexts = listOfNotNull(entry.arguments?.getString("context")),
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
                        onSettings = { t -> vm.updateSettings { it.copy(sleep = t(it.sleep)) } },
                    )
                }
                composable(
                    "nap_log?id={id}&start={start}&minutes={minutes}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; nullable = true },
                        navArgument("start") { type = NavType.StringType; nullable = true },
                        navArgument("minutes") { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) { entry ->
                    val existing = entry.arguments?.getString("id")?.let { id -> state.naps.firstOrNull { it.id == id } }
                    val start = entry.arguments?.getString("start")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                    val minutes = entry.arguments?.getInt("minutes")?.takeIf { it > 0 }
                    NapEditorScreen(
                        existing, start, minutes, now,
                        onSave = { nap -> vm.saveNap(nap); nav.popBackStack() },
                        onDelete = { existing?.let { vm.deleteNap(it.id) }; nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                        onNight = { nav.navigate("sleep_log") { popUpTo("nap_log?id={id}&start={start}&minutes={minutes}") { inclusive = true } } },
                    )
                }
                // ── Finding things, and the first tour ──
                composable(
                    "ideas?from={from}",
                    arguments = listOf(navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { ideasEntry ->
                    val screen = com.medipote.app.domain.Ideas.screenName(ideasEntry.arguments?.getString("from"))
                    com.medipote.app.ui.ideas.IdeasScreen(
                        ideas = state.ideas,
                        screen = screen,
                        onAdd = { text -> vm.addIdea(text, screen); vm.message("Ajouté à ta liste") },
                        onRemove = vm::removeIdea,
                        onShare = {
                            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Idées pour MédiPote")
                                .putExtra(Intent.EXTRA_TEXT, com.medipote.app.domain.Ideas.shareText(state.ideas, version))
                            context.startActivity(Intent.createChooser(send, "Partager mes idées"))
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("lab/{marker}") { entry ->
                    val marker = entry.arguments?.getString("marker").orEmpty()
                    com.medipote.app.ui.labs.LabDetailScreen(
                        state, marker,
                        onAdd = { nav.navigate("lab_edit?marker=${Uri.encode(marker)}") },
                        onEdit = { nav.navigate("lab_edit?id=$it") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "lab_edit?id={id}&marker={marker}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("marker") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val existing = entry.arguments?.getString("id")?.let { id -> state.labs.firstOrNull { it.id == id } }
                    com.medipote.app.ui.labs.LabEditorScreen(
                        existing, entry.arguments?.getString("marker"), today,
                        onSave = { vm.saveLab(it); nav.popBackStack() },
                        onDelete = { existing?.let { vm.deleteLab(it.id) }; nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("pharmacy") {
                    val items = com.medipote.app.domain.Pharmacy.list(state, today)
                    com.medipote.app.ui.treatments.PharmacyScreen(
                        items,
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, com.medipote.app.domain.Pharmacy.shareText(items))
                            context.startActivity(Intent.createChooser(send, "Partager la liste"))
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("emergency") {
                    val card = com.medipote.app.domain.Emergency.card(state, today)
                    com.medipote.app.ui.emergency.EmergencyScreen(
                        card,
                        onCall = { com.medipote.app.ui.support.dial(context, it) },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, com.medipote.app.domain.Emergency.shareText(card))
                            context.startActivity(Intent.createChooser(send, "Partager ma fiche d'urgence"))
                        },
                        onEditProfile = { nav.navigate("profile") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("travel") {
                    com.medipote.app.ui.settings.TravelScreen(
                        state.travel, today,
                        sampleMinute = state.treatments.firstOrNull { !it.asNeeded }?.doses?.firstOrNull()?.minuteOfDay ?: (8 * 60),
                        onSave = { vm.setTravel(it); nav.popBackStack() },
                        onCancelTrip = { vm.setTravel(null); nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("appointment_after/{id}") { entry ->
                    val a = state.appointments.firstOrNull { it.id == entry.arguments?.getString("id") }
                    if (a == null) LaunchedEffect(Unit) { nav.popBackStack() } else {
                        com.medipote.app.ui.agenda.AfterAppointmentScreen(
                            a,
                            onSaveNotes = { vm.saveAfterNotes(a.id, it) },
                            onAttended = { vm.markAttended(a) },
                            onTreatments = { nav.navigate("treatments") { launchSingleTop = true } },
                            onLabs = { nav.navigate("suivi?card=suivi:labs") { popUpTo("home") } },
                            onNext = { nav.navigate("appointment?date=$today&like=${a.id}") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("checkup") {
                    com.medipote.app.ui.understand.CheckupScreen(
                        state, today,
                        onCall = { com.medipote.app.ui.support.dial(context, it) },
                        onBook = { nav.navigate("appointment?date=$today") },
                        onPrepare = nextAppointment?.let { a -> { nav.navigate("appointment_prep/${a.id}") } },
                        onDismiss = vm::dismissCheck,
                        onOpenSymptom = { nav.navigate("symptom/${Uri.encode(it)}") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("find") {
                    com.medipote.app.ui.SearchScreen(
                        state,
                        onOpen = { entry ->
                            nav.popBackStack()
                            openEntry(entry)
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "tour?first={first}",
                    arguments = listOf(navArgument("first") { type = NavType.BoolType; defaultValue = false }),
                ) { entry ->
                    val first = entry.arguments?.getBoolean("first") == true
                    com.medipote.app.ui.onboarding.TourScreen(
                        onFinish = { chosen ->
                            // Seen again later (from the settings), the choice of cards is left alone.
                            if (first) vm.applyInterests(chosen) else vm.tourSeen()
                            nav.navigate("home") { popUpTo(0) { inclusive = true } }
                        },
                        onSkip = {
                            vm.tourSeen()
                            nav.navigate("home") { popUpTo(0) { inclusive = true } }
                        },
                    )
                }
                // ── Hard nights ──
                composable("sleep_help") {
                    com.medipote.app.ui.sleep.SleepHelpScreen(
                        state.sleepRecipe,
                        com.medipote.app.domain.Wellbeing.triedToday(state, today, com.medipote.app.domain.SleepHelp.TRIED_KIND),
                        com.medipote.app.ui.sleep.SleepHelpActions(
                            onBack = { nav.popBackStack() },
                            onTool = { tool ->
                                nav.navigate(
                                    when (tool) {
                                        com.medipote.app.domain.SleepTool.BREATHE -> "support_breathe"
                                        com.medipote.app.domain.SleepTool.RELAX -> "sleep_relax"
                                        com.medipote.app.domain.SleepTool.THOUGHTS -> "support_thoughts"
                                    },
                                )
                            },
                            onTips = { nav.navigate("sleep_tips") },
                            onLogNight = { nav.navigate("sleep_log") },
                            onSupport = { nav.navigate("support") },
                            onTried = { t -> vm.tried(com.medipote.app.domain.SleepHelp.TRIED_KIND, t.id, "J'ai essayé : m'aider à dormir") },
                            onRecipe = vm::setSleepRecipe,
                        ),
                    )
                }
                composable("sleep_relax") { com.medipote.app.ui.sleep.RelaxScreen(onBack = { nav.popBackStack() }) }
                composable("sleep_tips") {
                    com.medipote.app.ui.sleep.SleepTipsScreen(
                        windDown = state.settings.sleep.windDown,
                        onWindDown = { on -> vm.updateSettings { it.copy(sleep = it.sleep.copy(windDown = on)) } },
                        onEvening = { nav.navigate("ritual/EVENING") },
                        onHelp = { nav.navigate("sleep_help") },
                        onBack = { nav.popBackStack() },
                    )
                }
                // ── "Ça ne va pas" ──
                composable("support") {
                    LaunchedEffect(Unit) { vm.supportOpened() }
                    com.medipote.app.ui.support.SupportScreen(
                        state, now,
                        com.medipote.app.ui.support.SupportActions(
                            onBack = { nav.popBackStack() },
                            onBreathe = { nav.navigate("support_breathe") },
                            onGround = { nav.navigate("support_ground") },
                            onThoughts = { nav.navigate("support_thoughts") },
                            onComfort = { nav.navigate("support_comfort") },
                            onPlan = { nav.navigate("support_plan") },
                            onSettings = { nav.navigate("support_settings") },
                            onCall = { com.medipote.app.ui.support.dial(context, it) },
                            onMessage = { com.medipote.app.ui.support.sendHelpMessage(context, it) },
                            onMood = vm::setMood,
                            onAskAgain = vm::askAgainLater,
                            onSoftDay = { nav.navigate("softday") },
                            onFeel = { nav.navigate("support_feel") },
                        ),
                    )
                }
                composable("support_feel") {
                    com.medipote.app.ui.support.FeelScreen(
                        com.medipote.app.ui.support.FeelActions(
                            onBack = { nav.popBackStack() },
                            onTool = { tool ->
                                nav.navigate(
                                    when (tool) {
                                        com.medipote.app.domain.SupportTool.BREATHE -> "support_breathe"
                                        com.medipote.app.domain.SupportTool.GROUND -> "support_ground"
                                        com.medipote.app.domain.SupportTool.THOUGHTS -> "support_thoughts"
                                        com.medipote.app.domain.SupportTool.COMFORT -> "support_comfort"
                                        com.medipote.app.domain.SupportTool.SOFT_DAY -> "softday"
                                    },
                                )
                            },
                            onCall = { com.medipote.app.ui.support.dial(context, it) },
                            onNote = vm::noteFelt,
                            onTried = { sign -> vm.tried("feel", sign.id, "J'ai essayé : ${sign.label.lowercase()}") },
                        ),
                        tried = com.medipote.app.domain.Wellbeing.triedToday(state, today, "feel"),
                    )
                }
                composable("support_breathe") {
                    com.medipote.app.ui.support.BreathingScreen(state.settings.support.vibrate, onBack = { nav.popBackStack() })
                }
                composable("support_ground") {
                    com.medipote.app.ui.support.GroundingScreen(
                        onBack = { nav.popBackStack() },
                        onBreathe = { nav.navigate("support_breathe") { popUpTo("support_ground") { inclusive = true } } },
                    )
                }
                composable("support_thoughts") {
                    com.medipote.app.ui.support.ThoughtsScreen(state.support, onKeep = vm::keepThought, onDelete = vm::deleteThought, onBack = { nav.popBackStack() })
                }
                composable("support_comfort") {
                    com.medipote.app.ui.support.ComfortScreen(state.support, onChange = vm::updateSupport, onBack = { nav.popBackStack() })
                }
                composable("support_plan") {
                    com.medipote.app.ui.support.PlanScreen(state, onChange = vm::updateSupport, onContacts = { nav.navigate("support_settings") }, onBack = { nav.popBackStack() })
                }
                composable("support_settings") {
                    com.medipote.app.ui.support.SupportSettingsScreen(state.settings.support, onChange = vm::updateSupportSettings, onBack = { nav.popBackStack() })
                }
                composable("measure/{type}") { entry ->
                    val type = entry.arguments?.getString("type")?.let { runCatching { com.medipote.app.data.MeasureType.valueOf(it) }.getOrNull() }
                    if (type == null) LaunchedEffect(Unit) { nav.popBackStack() } else {
                        com.medipote.app.ui.measures.MeasureDetailScreen(
                            state, type, now,
                            onBack = { nav.popBackStack() },
                            onAdd = { nav.navigate("measure_log/${type.name}") },
                            onEdit = { nav.navigate("measure_log/${type.name}?id=${it.id}") },
                        )
                    }
                }
                composable(
                    "measure_log/{type}?id={id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true }),
                ) { entry ->
                    val type = entry.arguments?.getString("type")?.let { runCatching { com.medipote.app.data.MeasureType.valueOf(it) }.getOrNull() }
                    val existing = entry.arguments?.getString("id")?.let { id -> state.measurements.firstOrNull { it.id == id } }
                    if (type == null) LaunchedEffect(Unit) { nav.popBackStack() } else {
                        com.medipote.app.ui.measures.MeasureEditorScreen(
                            state, type, existing, now,
                            onSave = { vm.saveMeasurement(it); nav.popBackStack() },
                            onDelete = { existing?.let { vm.deleteMeasurement(it.id) }; nav.popBackStack() },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("badges") { com.medipote.app.ui.motivation.BadgesScreen(state, today, onBack = { nav.popBackStack() }) }
                composable("ritual/{kind}") { entry ->
                    val kind = entry.arguments?.getString("kind")?.let { runCatching { com.medipote.app.domain.RitualKind.valueOf(it) }.getOrNull() }
                        ?: com.medipote.app.domain.Rituals.current(now) ?: com.medipote.app.domain.RitualKind.EVENING
                    com.medipote.app.ui.motivation.RitualScreen(
                        state, kind, now,
                        com.medipote.app.ui.motivation.RitualActions(
                            give = { vm.give(it) },
                            giveEarly = vm::giveEarly,
                            catchUp = vm::catchUp,
                            saveEntry = vm::saveEntry,
                            addWater = vm::addWater,
                            saveSleep = vm::saveSleep,
                            complete = vm::completeRitual,
                            openSleepEditor = { nav.navigate("sleep_log") },
                            close = { nav.popBackStack() },
                        ),
                    )
                }
                composable("myday/{date}") { entry ->
                    var date by rememberSaveable { mutableStateOf(entry.arguments?.getString("date")?.let(LocalDate::parse) ?: today) }
                    com.medipote.app.ui.motivation.MyDayScreen(
                        state, date, now,
                        onDate = { date = it },
                        onEditJournal = { nav.navigate("day/$date") },
                        onEditSleep = { nav.navigate("sleep_log?date=$date") },
                        onOpenDoses = { nav.navigate("history/$date") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("year") {
                    com.medipote.app.ui.motivation.YearScreen(state, now, onOpenDay = { nav.navigate("myday/$it") }, onBack = { nav.popBackStack() })
                }
                composable("recap/{week}") { entry ->
                    val week = entry.arguments?.getString("week")?.let(LocalDate::parse) ?: com.medipote.app.domain.Recaps.weekToShow(now)
                    com.medipote.app.ui.motivation.RecapScreen(state, week, now, onSeen = { vm.markRecapSeen(week) }, onClose = { nav.popBackStack() })
                }
                composable("history/{date}") { entry ->
                    var date by rememberSaveable { mutableStateOf(entry.arguments?.getString("date")?.let(LocalDate::parse) ?: today.minusDays(1)) }
                    com.medipote.app.ui.home.DoseHistoryScreen(
                        state, date, now,
                        onDate = { date = it },
                        onCatchUp = vm::catchUp,
                        onUndo = vm::undoDose,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("appearance") {
                    com.medipote.app.ui.settings.AppearanceScreen(
                        state.settings, state.profile?.name.orEmpty(), Build.VERSION.SDK_INT >= 31,
                        onChange = vm::updateSettings,
                        onBack = { nav.popBackStack() },
                        onPreviewStreak = { vm.previewStreak() },
                        onOpenHomeLayout = { nav.navigate("home_layout") },
                        onOpenSeasons = { nav.navigate("seasons") },
                    )
                }
                composable("seasons") {
                    com.medipote.app.ui.settings.SeasonsScreen(
                        state.settings.seasons, pet, com.medipote.app.domain.Seasons.petBirthday(state, today), today,
                        onChange = vm::updateSeasons, onBack = { nav.popBackStack() },
                    )
                }
                composable("home_layout") {
                    com.medipote.app.ui.settings.HomeLayoutScreen(state.settings, vm::updateSettings, onBack = { nav.popBackStack() })
                }
                composable("report") {
                    com.medipote.app.ui.report.ReportScreen(state, now, onMessage = vm::message, onBack = { nav.popBackStack() })
                }
                composable("backup") {
                    com.medipote.app.ui.settings.BackupScreen(
                        state.settings, now,
                        onChange = vm::updateSettings,
                        onMessage = vm::message,
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
                        trackedSymptoms = if (com.medipote.app.domain.Wellbeing.enabled(state)) com.medipote.app.domain.Wellbeing.tracked(state) else emptyList(),
                        intensities = state.symptomLog.filter { it.date == date }.associate { it.symptom to it.intensity },
                        onSymptom = { id, v -> vm.setSymptomOn(date, id, v) },
                    )
                }

                // ── Appointments ──
                composable(
                    "appointment?id={id}&date={date}&like={like}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; nullable = true },
                        navArgument("date") { type = NavType.StringType; nullable = true },
                        navArgument("like") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    val existing = entry.arguments?.getString("id")?.let { id -> state.appointments.firstOrNull { it.id == id } }
                    val date = entry.arguments?.getString("date")?.let(LocalDate::parse) ?: today
                    AppointmentEditorScreen(
                        existing, date,
                        onSave = { vm.saveAppointment(it); nav.popBackStack() },
                        onDelete = { existing?.let { vm.deleteAppointment(it.id) }; nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                        onPrepare = existing?.let { a -> { nav.navigate("appointment_prep/${a.id}") } },
                        onAfter = existing?.takeIf { it.dateTime.isBefore(now.plusHours(1)) }?.let { a -> { nav.navigate("appointment_after/${a.id}") } },
                        like = entry.arguments?.getString("like")?.let { id -> state.appointments.firstOrNull { it.id == id } },
                    )
                }
                composable("appointment_prep/{id}") { entry ->
                    val a = state.appointments.firstOrNull { it.id == entry.arguments?.getString("id") }
                    if (a == null) LaunchedEffect(Unit) { nav.popBackStack() } else {
                        val prep = com.medipote.app.domain.AppointmentPrepper.build(state, a, now)
                        com.medipote.app.ui.agenda.AppointmentPrepScreen(
                            prep, a.dateTime.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM · HH:mm", java.util.Locale.FRANCE)),
                            onQuestions = { vm.savePrepQuestions(a, it) },
                            attended = a.attended,
                            canMarkAttended = !a.dateTime.isAfter(now.plusHours(2)),
                            onAttended = { vm.markAttended(a) },
                            onCopy = { text ->
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Rendez-vous", text))
                                vm.message("Texte copié")
                            },
                            onShare = { text ->
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(send, "Partager mon résumé"))
                            },
                            onReport = { nav.navigate("report") },
                            onBack = { nav.popBackStack() },
                        )
                    }
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
                            onRenew = { vm.renewDocument(doc) },
                        )
                    }
                }

                // ── Settings ──
                composable(
                    "settings?section={section}",
                    arguments = listOf(navArgument("section") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { settingsEntry ->
                    SettingsScreen(
                        openSection = settingsEntry.arguments?.getString("section"),
                        settings = state.settings,
                        usualWake = com.medipote.app.domain.Routines.usualWake(state, today),
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
                        quietReminders = if (notificationsAllowed) quietChannels.size else 0,
                        onOpenBackup = { nav.navigate("backup") },
                        onOpenAppearance = { nav.navigate("appearance") },
                        onOpenHomeLayout = { nav.navigate("home_layout") },
                        onOpenSupport = { nav.navigate("support_settings") },
                        onOpenWellbeing = { nav.navigate("wellbeing_me") },
                        onOpenTour = { nav.navigate("tour?first=false") },
                        onOpenIdeas = { nav.navigate("ideas?from=settings") },
                        onOpenTravel = { nav.navigate("travel") },
                        onOpenCards = { nav.navigate("cards_layout") },
                        watchLastSeen = com.medipote.app.wear.WatchLink.lastSeen(context),
                        watchRings = com.medipote.app.wear.WatchLink.watchHandlesReminders(context),
                        onSyncWatch = {
                            com.medipote.app.wear.WatchLink.publish(context, force = true)
                            vm.message("Envoyé à la montre, si elle est à portée")
                        },
                        lockEnabled = state.settings.lockEnabled,
                        onToggleLock = { on ->
                            val activity = context.findActivity()
                            when {
                                !on -> vm.updateSettings { it.copy(lockEnabled = false) }
                                activity == null || !AppLock.available(context) ->
                                    vm.message("Configure d'abord un code, un schéma ou une empreinte dans les réglages du téléphone")
                                else -> AppLock.prompt(activity, "Confirme pour activer le verrouillage", onSuccess = {
                                    vm.updateSettings { it.copy(lockEnabled = true) }
                                    vm.message("Verrouillage activé")
                                })
                            }
                        },
                        lastBackup = listOfNotNull(state.settings.backup.lastAuto, state.settings.backup.lastManual).maxOrNull(),
                        onFixQuietReminders = {
                            context.startActivity(Notifications.channelSettingsIntent(context, quietChannels.firstOrNull() ?: Notifications.CHANNEL_DOSES))
                        },
                        onOpenBatterySettings = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                    )
                }
                composable("profile") {
                    state.profile?.let { p -> ProfileScreen(p, today, onBack = { nav.popBackStack() }, onSave = vm::saveProfile, onOpenEmergency = { nav.navigate("emergency") }) }
                }
            }
        }

        checkIn?.let { event ->
            if (state.settings.sober) {
                LaunchedEffect(event) { vm.checkInShown() }
            } else if (state.settings.look.celebrations) {
                com.medipote.app.ui.home.StreakCelebration(event.reward, pet, com.medipote.app.domain.Rules.gameDay(now)) { vm.checkInShown() }
            } else {
                CheckInDialog(event, pet) { vm.checkInShown() }
            }
        }

        badgeQueue.firstOrNull()?.let { badge ->
            com.medipote.app.ui.motivation.BadgeEarnedDialog(
                badge,
                onDismiss = { badgeQueue.removeAt(0) },
                onOpenShop = {
                    com.medipote.app.domain.Badges.reward(badge.id)?.let(vm::equip)
                    badgeQueue.removeAt(0)
                    nav.navigate("moi") { launchSingleTop = true }
                },
            )
        }

        val locked by vm.locked.collectAsStateWithLifecycle()
        if (locked) {
            LockScreen(
                pet, onUnlocked = vm::unlock,
                emergency = com.medipote.app.domain.Emergency.card(state, today).takeIf { state.settings.emergencyOnLock },
                onCall = { com.medipote.app.ui.support.dial(context, it) },
            )
        }
    }
}

private tailrec fun android.content.Context.findActivity(): androidx.fragment.app.FragmentActivity? = when (this) {
    is androidx.fragment.app.FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Covers the app until the phone's fingerprint, face or PIN is given. */
@Composable
private fun LockScreen(pet: String, onUnlocked: () -> Unit, emergency: com.medipote.app.domain.EmergencyCard? = null, onCall: (String) -> Unit = {}) {
    val context = LocalContext.current
    var showEmergency by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun ask() {
        val activity = context.findActivity()
        // Nothing protects the phone anymore (or no prompt possible): don't lock the person out.
        if (activity == null || !AppLock.available(context)) {
            onUnlocked()
            return
        }
        AppLock.prompt(activity, "Déverrouille pour voir tes données de santé", onSuccess = onUnlocked, onError = { error = it })
    }
    LaunchedEffect(Unit) { ask() }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false, decorFitsSystemWindows = false,
        ),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                Text("MédiPote est verrouillé", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    "$pet garde tes données de santé au chaud 🔒",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                error?.let {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
                androidx.compose.material3.Button(onClick = { error = null; ask() }) { Text("Déverrouiller") }
                // The person chose to let the emergency services read this card without the code.
                if (emergency != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                    androidx.compose.material3.OutlinedButton(onClick = { showEmergency = !showEmergency }) { Text(if (showEmergency) "Masquer la fiche" else "🚑  Fiche d'urgence") }
                    if (showEmergency) {
                        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            com.medipote.app.ui.emergency.EmergencyCardContent(emergency, onCall)
                        }
                    }
                }
            }
        }
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
