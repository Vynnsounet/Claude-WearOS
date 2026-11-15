package com.medipote.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.medipote.app.data.AppState
import com.medipote.app.data.HomeSection
import com.medipote.app.data.TakeStatus
import com.medipote.app.domain.DoseItem
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.PetCondition
import com.medipote.app.domain.Schedule
import com.medipote.app.ui.components.PointsBadge
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.StreakBadge
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.fullDay
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.pet.InteractivePet
import com.medipote.app.domain.PetTalk
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppState,
    now: LocalDateTime,
    feedSignal: Int,
    lastPoints: Int,
    onGive: (DoseItem) -> Unit,
    onAddTreatment: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    animate: Boolean = true,
    onAddWater: (Int) -> Unit = {},
    onOpenJournal: () -> Unit = {},
    onOpenAppointment: (com.medipote.app.data.Appointment) -> Unit = {},
    onOpenTreatments: () -> Unit = {},
    pointsSignal: Int = feedSignal,
    notificationsBlocked: Boolean = false,
    remindersQuiet: Boolean = false,
    onFixQuietReminders: () -> Unit = {},
    onFixNotifications: () -> Unit = {},
    onCuddle: () -> Unit = {},
    onGiveEarly: (DoseItem) -> Unit = {},
    onLogPrn: (com.medipote.app.data.Treatment) -> Unit = {},
    onOpenPrnIntake: (com.medipote.app.data.PrnIntake) -> Unit = {},
    onOpenSleep: () -> Unit = {},
    onAddMeasure: (com.medipote.app.data.MeasureType) -> Unit = {},
    onOpenMeasure: (com.medipote.app.data.MeasureType) -> Unit = {},
    onOpenDocument: (com.medipote.app.data.HealthDocument) -> Unit = {},
    onOpenHistory: (java.time.LocalDate) -> Unit = {},
    onMood: (Int?) -> Unit = {},
    onRitual: (com.medipote.app.domain.RitualKind) -> Unit = {},
    onOpenBadges: () -> Unit = {},
    onOpenRecap: (java.time.LocalDate) -> Unit = {},
    onProductChanged: () -> Unit = {},
    onProductRemoved: () -> Unit = {},
    onProductStart: (com.medipote.app.data.PeriodProduct) -> Unit = {},
    onOpenHomeLayout: () -> Unit = {},
    onNapWake: () -> Unit = {},
    onNapCancel: () -> Unit = {},
    quickAdd: QuickAddActions? = null,
) {
    val logs = remember(state.doseLogs) { state.doseLogs.associateBy { it.key } }
    val today = remember(state.treatments, logs, now) {
        Schedule.carriedOver(state.treatments, logs, now) + Schedule.itemsOn(state.treatments, logs, now.toLocalDate(), now)
    }
    val condition = remember(state, now) { Game.petCondition(state, now) }
    val growth = remember(state.game.growth, state.doseLogs) { com.medipote.app.domain.Evolution.growth(state) }
    val giveable = today.filter { it.canGive }
    var petBounds by remember { mutableStateOf<Rect?>(null) }
    var dragHover by remember { mutableStateOf(false) }

    val look = state.settings.look
    val date = now.toLocalDate()
    val yesterday = date.minusDays(1)
    val forgotten = remember(state.doseLogs, state.treatments, yesterday) { forgottenDoses(state, yesterday, now) }
    val sections = look.visibleSections
    val hero = if (HomeSection.NEXT_DOSE in sections) com.medipote.app.domain.HomeRules.nextDose(today, now) else null
    val todayOnly = today.filter { it.occurrence.date == date }

    // Each block decides here whether it has something useful to say; null = not shown.
    fun block(section: HomeSection): (@Composable () -> Unit)? = when (section) {
        HomeSection.STATUS -> { { StatusRow(state, now, onOpenBadges) } }
        HomeSection.PHRASE -> { { DailyPhrase(com.medipote.app.domain.DailyPhrases.of(date)) } }
        HomeSection.NEXT_DOSE -> when {
            hero != null -> { { HeroDose(hero, now, state.game.petName, onGive, onGiveEarly) } }
            todayOnly.isNotEmpty() && todayOnly.all { it.state == DoseState.TAKEN } -> { { AllDone(todayOnly.size, state.game.petName) } }
            else -> null
        }
        HomeSection.COMPANION -> { {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = if (dragHover) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.25f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .onGloballyPositioned { petBounds = it.boundsInRoot() },
                ) {
                    InteractivePet(
                        mood = condition.mood,
                        equipped = state.game.equipped,
                        talk = { r -> PetTalk.line(r, state, now) },
                        petName = state.game.petName,
                        feedSignal = feedSignal,
                        animate = animate,
                        description = "${state.game.petName} : ${condition.message}",
                        greeting = remember(state.profile?.name) { PetTalk.greeting(state, now) },
                        onCuddle = onCuddle,
                        stage = growth.stage,
                        time = com.medipote.app.domain.TimeOfDay.of(now),
                        modifier = Modifier.fillMaxSize(),
                    )
                    HealthTag(state.game.petName, condition, growth, Modifier.align(Alignment.TopStart).padding(12.dp))
                    FloatingPoints(pointsSignal, lastPoints, Modifier.align(Alignment.TopCenter).padding(top = 32.dp))
                }
                MoodMessage(condition)
            }
            if (giveable.isNotEmpty()) {
                PillTray(items = giveable, petBounds = petBounds, onHover = { dragHover = it }, onDrop = onGive)
            }
        } }
        HomeSection.MOOD -> { { com.medipote.app.ui.motivation.MoodCard(state.entryFor(date), onMood, foldWhenDone = true) } }
        HomeSection.RITUAL -> com.medipote.app.domain.Rituals.current(now)
            ?.takeIf { !com.medipote.app.domain.Rituals.done(state.game, now, it) }
            ?.let { kind -> { com.medipote.app.ui.motivation.RitualCard(kind) { onRitual(kind) } } }
        HomeSection.PERIOD -> when {
            state.productSession != null -> { {
                com.medipote.app.ui.cycle.PeriodCareCard(
                    state, now, onStart = {}, onChanged = onProductChanged, onRemoved = onProductRemoved,
                    onSetReminder = { _, _ -> }, compact = true,
                )
            } }
            com.medipote.app.domain.PeriodCare.inPeriod(state, date) -> { {
                com.medipote.app.ui.cycle.PeriodCareCard(state, now, onStart = onProductStart, onChanged = {}, onRemoved = {}, onSetReminder = { _, _ -> }, compact = true)
            } }
            else -> null
        }
        HomeSection.DOSES -> when {
            state.treatments.isEmpty() -> { { EmptyTreatments(state.game.petName, onAddTreatment) } }
            state.treatments.none { !it.asNeeded } || today.isEmpty() -> null
            else -> { {
                val done = todayOnly.count { it.state == DoseState.TAKEN }
                SectionTitle("Aujourd'hui") {
                    Text("$done / ${todayOnly.size} prises", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (todayOnly.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / todayOnly.size },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
                // Taken doses fold into one line; the one shown in big above isn't repeated.
                val taken = today.filter { it.state == DoseState.TAKEN }
                val rest = today.filter { it.state != DoseState.TAKEN && it.occurrence.key != hero?.occurrence?.key }
                rest.forEach { item -> DoseCard(item, now, onGive, onGiveEarly) }
                if (taken.isNotEmpty()) TakenFold(taken, now, onGive, onGiveEarly)
                if (forgotten.isEmpty()) {
                    TextButton(onClick = { onOpenHistory(yesterday) }) {
                        Icon(Icons.Rounded.History, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Jours précédents : noter un oubli")
                    }
                }
            } }
        }
        HomeSection.AS_NEEDED -> com.medipote.app.domain.HomeRules.recentPrn(state, now).takeIf { it.isNotEmpty() }?.let { prn ->
            { PrnCard(state, prn, now, onLogPrn, onOpenPrnIntake) }
        }
        HomeSection.MEASURES -> if (state.settings.measures.isEmpty()) null else { { com.medipote.app.ui.measures.HomeMeasuresCard(state, now, onAddMeasure, onOpenMeasure) } }
        HomeSection.WATER -> if (!com.medipote.app.domain.HomeRules.showWater(state, date)) null else { {
            val water = state.settings.water
            WaterCard(state.entryFor(date).waterGlasses, water.goalGlasses, water.glassMl, onAddWater)
        } }
        HomeSection.PROMPTS -> {
            // Not twice the same invitation: the ritual and mood cards already ask when they are shown.
            val ritualShown = HomeSection.RITUAL in sections &&
                com.medipote.app.domain.Rituals.current(now) == com.medipote.app.domain.RitualKind.MORNING &&
                !com.medipote.app.domain.Rituals.done(state.game, now, com.medipote.app.domain.RitualKind.MORNING)
            val sleep = !ritualShown && com.medipote.app.domain.HomeRules.sleepPrompt(state, now)
            val journal = HomeSection.MOOD !in sections && com.medipote.app.domain.HomeRules.journalPrompt(state, now)
            if (!sleep && !journal) null else { {
                if (sleep) SleepPrompt(onOpenSleep)
                if (journal) JournalPrompt(state.game.petName, onOpenJournal)
            } }
        }
        HomeSection.APPOINTMENT -> com.medipote.app.domain.HomeRules.nextAppointment(state, now)?.let { a ->
            {
                SectionTitle("Prochain rendez-vous")
                com.medipote.app.ui.agenda.AppointmentCard(a, now) { onOpenAppointment(a) }
            }
        }
        // Shown in the companion space; on home, in the status line.
        HomeSection.CHALLENGES, HomeSection.GARDEN -> null
    }

    // Important alerts always show, right under the status line.
    val recap = com.medipote.app.domain.Recaps.pending(state, now)?.takeIf { com.medipote.app.domain.HomeRules.recapTime(now) }
    val low = state.treatments.filter { it.isLowStock }
    val renewals = com.medipote.app.domain.Renewals.due(state, date)
    val blocked = notificationsBlocked && state.settings.notificationsEnabled
    val quiet = !blocked && remindersQuiet && state.settings.notificationsEnabled
    val alerts: (@Composable () -> Unit)? =
        if (recap == null && low.isEmpty() && renewals.isEmpty() && !blocked && !quiet && forgotten.isEmpty()) null
        else { {
            if (blocked) Notice(Icons.Rounded.NotificationsOff, AppTheme.status.warning, "Les notifications sont bloquées", "Tu ne recevras pas les rappels. Touche ici pour les autoriser.", onClick = onFixNotifications)
            if (quiet) Notice(
                Icons.Rounded.NotificationsOff, AppTheme.status.warning, "Les rappels arrivent sans pop-up",
                "Ils restent cachés dans le volet. Touche ici et active « Afficher en pop-up » et le son.", onClick = onFixQuietReminders,
            )
            if (forgotten.isNotEmpty()) {
                val n = forgotten.size
                Notice(
                    Icons.Rounded.History, AppTheme.status.warning,
                    "Hier : $n prise${if (n > 1) "s" else ""} non notée${if (n > 1) "s" else ""}",
                    "Prise${if (n > 1) "s" else ""} mais pas noté${if (n > 1) "es" else "e"} ? Touche pour rattraper.",
                ) { onOpenHistory(yesterday) }
            }
            if (low.isNotEmpty()) Notice(
                Icons.Outlined.Inventory2, AppTheme.status.critical, "Pense à racheter",
                low.joinToString(" · ") { t -> "${t.displayName} : " + (t.daysLeft?.let { "$it j" } ?: t.stock?.let { t.unit.format(it) } ?: "") },
                onClick = onOpenTreatments,
            )
            renewals.forEach { doc ->
                val left = com.medipote.app.domain.Renewals.daysLeft(doc, date) ?: 0
                Notice(
                    null, if (left < 0) AppTheme.status.critical else AppTheme.status.warning,
                    if (left < 0) "Ordonnance expirée" else "Ordonnance à renouveler",
                    "${doc.title} · ${com.medipote.app.domain.Renewals.label(doc, date)?.lowercase()}", emoji = "📄",
                ) { onOpenDocument(doc) }
            }
            recap?.let { week -> Notice(null, MaterialTheme.colorScheme.primary, "Ton récap de la semaine est prêt", "Tes prises, tes nuits et ton humeur en quelques cartes.", emoji = "🎁") { onOpenRecap(week) } }
        } }

    val blocks = buildList<@Composable () -> Unit> {
        val rest = sections.toMutableList()
        // The status line and the phrase of the day stay on top, then the companion, then the alerts.
        while (rest.firstOrNull() in setOf(HomeSection.STATUS, HomeSection.PHRASE)) block(rest.removeAt(0))?.let(::add)
        if (rest.firstOrNull() == HomeSection.COMPANION) block(rest.removeAt(0))?.let(::add)
        // A nap being timed stays in sight, whatever the layout.
        com.medipote.app.domain.Naps.running(state, now)?.let { since ->
            add { com.medipote.app.ui.sleep.NapTimerCard(since, now, onWake = onNapWake, onCancel = onNapCancel, compact = true) }
        }
        alerts?.let(::add)
        rest.forEach { section -> block(section)?.let(::add) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bonjour ${state.profile?.name.orEmpty()} ${look.avatar}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            now.format(fullDay).capitalized(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenProfile) { Icon(Icons.Outlined.AccountCircle, "Profil médical") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Paramètres") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = { quickAdd?.let { QuickAddButton(state, now, it) } },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(0.dp))
            blocks.forEach { content ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            }
            TextButton(onClick = onOpenHomeLayout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("✏️  Personnaliser l'accueil")
            }
            // Room for the "+" button.
            Spacer(Modifier.height(if (quickAdd != null) 64.dp else 0.dp))
        }
    }
}

/** The phrase of the day: quiet, centred, no card. */
@Composable
private fun DailyPhrase(text: String) {
    Text(
        "🌼  $text",
        style = MaterialTheme.typography.titleMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
        color = MaterialTheme.colorScheme.primary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun HealthTag(name: String, condition: PetCondition, growth: com.medipote.app.domain.Growth, modifier: Modifier) {
    val status = AppTheme.status
    val color = when {
        condition.health >= 80 -> status.good
        condition.health >= 50 -> status.warning
        else -> status.critical
    }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(120.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { condition.health / 100f },
                    color = color,
                    trackColor = color.copy(alpha = 0.2f),
                    drawStopIndicator = {},
                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text("${condition.health} %", style = MaterialTheme.typography.labelSmall)
            }
            Text("Santé", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "🌱 ${growth.stage.label}",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                growth.toNext?.let { "encore $it prise${if (it > 1) "s" else ""} à l'heure pour grandir" } ?: "taille adulte, en fleurs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoodMessage(condition: PetCondition) {
    val status = AppTheme.status
    val (bg, fg) = when (condition.mood) {
        com.medipote.app.domain.PetMood.SICK -> status.criticalContainer to MaterialTheme.colorScheme.onSurface
        com.medipote.app.domain.PetMood.UNWELL -> status.warningContainer to MaterialTheme.colorScheme.onSurface
        com.medipote.app.domain.PetMood.HUNGRY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    Text(
        condition.message,
        style = MaterialTheme.typography.bodyLarge,
        color = fg,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun FloatingPoints(signal: Int, points: Int, modifier: Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(signal) {
        if (signal != 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1800))
        }
    }
    if (progress.value < 1f) {
        Text(
            "+$points ⭐",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF8A5A00),
            modifier = modifier
                .graphicsLayer {
                    translationY = -progress.value * 80f
                    alpha = (1f - progress.value * 1.1f).coerceIn(0f, 1f)
                }
                .background(Color(0xFFFFF3C4), CircleShape)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

/** Draggable pills: drop one on the pet to give the dose. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillTray(
    items: List<DoseItem>,
    petBounds: Rect?,
    onHover: (Boolean) -> Unit,
    onDrop: (DoseItem) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Fais glisser un médicament sur ton compagnon pour le lui donner",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                DraggablePill(item, petBounds, onHover) { onDrop(item) }
            }
        }
    }
}

@Composable
private fun DraggablePill(item: DoseItem, petBounds: Rect?, onHover: (Boolean) -> Unit, onDrop: () -> Unit) {
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var origin by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val late = item.state == DoseState.OVERDUE
    val t = item.occurrence.treatment
    Surface(
        shape = CircleShape,
        color = if (late) AppTheme.status.warningContainer else MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = if (dragging) 8.dp else 1.dp,
        modifier = Modifier
            .onGloballyPositioned { origin = it.boundsInRoot() }
            .zIndex(if (dragging) 10f else 0f)
            .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
            .scale(if (dragging) 1.08f else 1f)
            .semantics { contentDescription = "Glisser ${t.displayName} sur le compagnon" }
            .pointerInput(item.occurrence.key) {
                fun over() = petBounds?.contains(origin.center + offset.value) == true
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        val hit = over()
                        dragging = false
                        onHover(false)
                        if (hit) {
                            scope.launch { offset.snapTo(Offset.Zero) }
                            onDrop()
                        } else {
                            scope.launch { offset.animateTo(Offset.Zero, spring()) }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        onHover(false)
                        scope.launch { offset.animateTo(Offset.Zero, spring()) }
                    },
                ) { change, amount ->
                    change.consume()
                    scope.launch {
                        offset.snapTo(offset.value + amount)
                        onHover(over())
                    }
                }
            },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Medication, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(t.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(
                    "${item.occurrence.dose.timeLabel} · ${t.unit.format(item.occurrence.quantity)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DoseCard(item: DoseItem, now: LocalDateTime, onGive: (DoseItem) -> Unit, onGiveEarly: (DoseItem) -> Unit) {
    var confirmEarly by remember { mutableStateOf(false) }
    val status = AppTheme.status
    val t = item.occurrence.treatment
    val (label, color, container) = when (item.state) {
        DoseState.UPCOMING -> Triple("À venir", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHigh)
        DoseState.DUE -> Triple("C'est l'heure", MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        DoseState.OVERDUE -> Triple("En retard", MaterialTheme.colorScheme.onSurface, status.warningContainer)
        DoseState.MISSED -> Triple("Oublié", MaterialTheme.colorScheme.onSurface, status.criticalContainer)
        DoseState.TAKEN -> {
            val log = item.log!!
            val txt = when (log.status) {
                TakeStatus.ON_TIME -> "Donné à ${log.takenAt.format(hourMinute)} · +${log.points}"
                TakeStatus.EARLY -> "Pris en avance (${log.takenAt.format(hourMinute)}) · +${log.points}"
                else -> "En retard (${log.takenAt.format(hourMinute)}) · +${log.points}"
            }
            Triple(txt, MaterialTheme.colorScheme.onSurface, if (log.status == TakeStatus.ON_TIME) status.goodContainer else status.warningContainer)
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(item.occurrence.dose.timeLabel, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.unit.format(item.occurrence.quantity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (t.withFood) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.Restaurant, "Pendant le repas", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
                Surface(color = container, contentColor = color, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (item.state) {
                            DoseState.TAKEN -> Icon(Icons.Rounded.Check, null, Modifier.size(14.dp))
                            DoseState.OVERDUE, DoseState.MISSED -> Icon(Icons.Rounded.WarningAmber, null, Modifier.size(14.dp))
                            else -> Unit
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (item.canGive) {
                Spacer(Modifier.width(8.dp))
                if (item.state == DoseState.DUE) {
                    Button(onClick = { onGive(item) }) { Text("Donner") }
                } else {
                    FilledTonalButton(onClick = { onGive(item) }) { Text("Donner") }
                }
            } else if (item.canMarkEarly(now)) {
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.TextButton(onClick = { confirmEarly = true }) { Text("Déjà pris ?") }
            }
        }
    }
    if (confirmEarly) {
        EarlyDialog(
            name = item.occurrence.treatment.displayName,
            time = item.occurrence.dose.timeLabel,
            onConfirm = { confirmEarly = false; onGiveEarly(item) },
            onDismiss = { confirmEarly = false },
        )
    }
}

/** Confirms marking a dose planned later today as already taken. */
@Composable
fun EarlyDialog(name: String, time: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prise déjà faite ?") },
        text = {
            Text(
                "Prise prévue à $time : $name. Déjà faite ? Elle sera notée et le rappel ne sonnera pas.\n\n" +
                    "Plus de ${com.medipote.app.domain.Rules.EARLY_WINDOW_MINUTES} min en avance, elle rapporte ${com.medipote.app.domain.Rules.POINTS_EARLY} points " +
                    "au lieu de ${com.medipote.app.domain.Rules.POINTS_ON_TIME}. En cas de doute sur le moment de la prise, " +
                    "demande conseil à ton pharmacien.",
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text("Oui, c'est fait") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun EmptyTreatments(petName: String, onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aucun traitement pour l'instant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Ajoute tes médicaments pour que $petName puisse t'accompagner à chaque prise.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAdd) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Ajouter un traitement")
            }
        }
    }
}

@Composable
private fun PrnCard(
    state: AppState,
    treatments: List<com.medipote.app.data.Treatment>,
    now: LocalDateTime,
    onLog: (com.medipote.app.data.Treatment) -> Unit,
    onOpenIntake: (com.medipote.app.data.PrnIntake) -> Unit,
) {
    // An intake from the last 12 hours that asked to be rated and is now old enough.
    val toRate = state.prnIntakes.firstOrNull {
        it.askRelief && it.relief == null &&
            !it.takenAt.plusMinutes(com.medipote.app.domain.Prn.RELIEF_CHECK_MINUTES).isAfter(now) &&
            it.takenAt.isAfter(now.minusHours(12)) && treatments.any { t -> t.id == it.treatmentId }
    }
    SectionTitle("Si besoin")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            toRate?.let { intake ->
                val name = treatments.first { it.id == intake.treatmentId }.name
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenIntake(intake) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("💬", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Est-ce que $name a fait effet ?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Pris à ${intake.takenAt.format(com.medipote.app.ui.components.hourMinute)} · touche pour noter l'effet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
            treatments.forEachIndexed { i, t ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                val intakes = com.medipote.app.domain.Prn.intakesOf(state, t.id)
                val last = intakes.firstOrNull { !it.takenAt.isAfter(now) }
                val today = intakes.count { it.takenAt.toLocalDate() == now.toLocalDate() }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                if (today > 0 && last != null) {
                                    append("Dernière prise il y a ${com.medipote.app.domain.Prn.formatDuration(java.time.Duration.between(last.takenAt, now))}")
                                    append(" · $today prise${if (today > 1) "s" else ""} aujourd'hui")
                                } else {
                                    append(last?.let { "Dernière prise ${com.medipote.app.ui.treatments.sinceLabel(it.takenAt, now)}" } ?: "Aucune prise notée")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.FilledTonalButton(onClick = { onLog(t) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Noter")
                    }
                }
            }
        }
    }
}

/** Water of the day: one drop per glass, tap + after each glass. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaterCard(glasses: Int, goal: Int, glassMl: Int, onAdd: (Int) -> Unit) {
    val water = Color(0xFF3A9BDC)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hydratation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$glasses / $goal verres · ${"%.2f".format(java.util.Locale.FRANCE, glasses * glassMl / 1000.0).trimEnd('0').trimEnd(',')} L" +
                            when {
                                glasses >= com.medipote.app.domain.Rules.waterRewardedGlasses(goal) -> " · bravo, belle hydratation 🎉"
                                glasses >= goal -> " · objectif atteint 🎉 · +${com.medipote.app.domain.Rules.WATER_GLASS_POINTS} pts par verre en plus"
                                else -> " · +${com.medipote.app.domain.Rules.WATER_GLASS_POINTS} pts par verre"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.FilledTonalIconButton(onClick = { onAdd(-1) }, enabled = glasses > 0) {
                    Icon(Icons.Rounded.Remove, "Retirer un verre")
                }
                Spacer(Modifier.width(4.dp))
                androidx.compose.material3.FilledIconButton(onClick = { onAdd(1) }) { Icon(Icons.Rounded.Add, "Ajouter un verre") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(maxOf(goal, glasses)) { i ->
                    Icon(
                        Icons.Rounded.WaterDrop, null,
                        tint = if (i < glasses) water else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepPrompt(onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bien dormi ?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    "Note ta nuit et tes réveils : +${com.medipote.app.domain.Rules.SLEEP_BONUS} pts.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun JournalPrompt(pet: String, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🙂", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Comment te sens-tu aujourd'hui ?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "$pet aimerait savoir ! Note ton état dans ton journal : +5 pts.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/** Streak, points, badges and challenges in one line. */
@Composable
private fun StatusRow(state: AppState, now: LocalDateTime, onOpen: () -> Unit) {
    val challenges = remember(state, now.toLocalDate()) { com.medipote.app.domain.Challenges.status(state, now.toLocalDate()) }
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreakBadge(state.game.streak)
        PointsBadge(state.game.points)
        StatusChip("🏅 ${state.game.badges.size}")
        if (challenges.isNotEmpty()) StatusChip("🎯 ${challenges.count { it.complete }}/${challenges.size}")
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** The next dose, in big: the one thing to do. */
@Composable
private fun HeroDose(item: DoseItem, now: LocalDateTime, petName: String, onGive: (DoseItem) -> Unit, onGiveEarly: (DoseItem) -> Unit) {
    var confirmEarly by remember { mutableStateOf(false) }
    val t = item.occurrence.treatment
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    val whenText = when (item.state) {
        DoseState.DUE -> "C'est l'heure"
        DoseState.OVERDUE -> "En retard de ${com.medipote.app.domain.Prn.formatDuration(java.time.Duration.between(item.occurrence.scheduled, now))}"
        else -> "Dans ${com.medipote.app.domain.Prn.formatDuration(java.time.Duration.between(now, item.occurrence.scheduled))}"
    }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = on),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Prochaine prise · $whenText", style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.occurrence.dose.timeLabel, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        t.unit.format(item.occurrence.quantity) + if (t.withFood) " · pendant le repas" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = on.copy(alpha = 0.8f),
                    )
                }
            }
            if (item.canGive) {
                Button(onClick = { onGive(item) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Donner à $petName", style = MaterialTheme.typography.titleMedium)
                }
            } else if (item.canMarkEarly(now)) {
                TextButton(onClick = { confirmEarly = true }) { Text("Déjà pris ?", color = on) }
            }
        }
    }
    if (confirmEarly) {
        EarlyDialog(t.displayName, item.occurrence.dose.timeLabel, onConfirm = { confirmEarly = false; onGiveEarly(item) }, onDismiss = { confirmEarly = false })
    }
}

@Composable
private fun AllDone(count: Int, petName: String) {
    Surface(color = AppTheme.status.goodContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎉", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Text(
                "Bravo, tout est pris pour aujourd'hui ($count prise${if (count > 1) "s" else ""}). $petName te dit merci !",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Doses already taken today, folded into one line. */
@Composable
private fun TakenFold(taken: List<DoseItem>, now: LocalDateTime, onGive: (DoseItem) -> Unit, onGiveEarly: (DoseItem) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = !open },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, tint = AppTheme.status.good, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "${taken.size} prise${if (taken.size > 1) "s" else ""} faite${if (taken.size > 1) "s" else ""} : " +
                    taken.joinToString(", ") { it.occurrence.treatment.name },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(if (open) "Masquer" else "Voir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) taken.forEach { DoseCard(it, now, onGive, onGiveEarly) }
}

/** An alert in a calm card: the colour is only on its icon. */
@Composable
private fun Notice(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    tint: Color,
    title: String,
    text: String,
    emoji: String = "",
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = tint.copy(alpha = 0.15f), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    else Text(emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
