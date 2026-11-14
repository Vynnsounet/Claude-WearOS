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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
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
    onFixNotifications: () -> Unit = {},
    onCuddle: () -> Unit = {},
    onGiveEarly: (DoseItem) -> Unit = {},
    onLogPrn: (com.medipote.app.data.Treatment) -> Unit = {},
    onOpenPrnIntake: (com.medipote.app.data.PrnIntake) -> Unit = {},
) {
    val logs = remember(state.doseLogs) { state.doseLogs.associateBy { it.key } }
    val today = remember(state.treatments, logs, now) {
        Schedule.carriedOver(state.treatments, logs, now) + Schedule.itemsOn(state.treatments, logs, now.toLocalDate(), now)
    }
    val condition = remember(state, now) { Game.petCondition(state, now) }
    val giveable = today.filter { it.canGive }
    var petBounds by remember { mutableStateOf<Rect?>(null) }
    var dragHover by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bonjour ${state.profile?.name.orEmpty()} 👋", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreakBadge(state.game.streak)
                PointsBadge(state.game.points)
            }
            if (notificationsBlocked && state.settings.notificationsEnabled) NotificationsBanner(onFixNotifications)

            // ── The companion ──
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
                        modifier = Modifier.fillMaxSize(),
                    )
                    HealthTag(state.game.petName, condition, Modifier.align(Alignment.TopStart).padding(12.dp))
                    FloatingPoints(pointsSignal, lastPoints, Modifier.align(Alignment.TopCenter).padding(top = 32.dp))
                }
                MoodMessage(condition)
            }

            if (giveable.isNotEmpty()) {
                PillTray(
                    items = giveable,
                    petBounds = petBounds,
                    onHover = { dragHover = it },
                    onDrop = onGive,
                )
            }

            // ── Today's doses ──
            if (state.treatments.isEmpty()) {
                EmptyTreatments(state.game.petName, onAddTreatment)
            } else if (state.treatments.any { !it.asNeeded }) {
                val todayOnly = today.filter { it.occurrence.date == now.toLocalDate() }
                val done = todayOnly.count { it.state == DoseState.TAKEN }
                SectionTitle("Aujourd'hui") {
                    Text(
                        "$done / ${todayOnly.size} prises",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (todayOnly.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / todayOnly.size },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
                if (today.isEmpty()) {
                    Text(
                        "Aucune prise prévue aujourd'hui. ${state.game.petName} profite de sa journée !",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                today.forEach { item -> DoseCard(item, now, onGive, onGiveEarly) }
            }

            val prn = state.treatments.filter { it.asNeeded && (it.endDate == null || !it.endDate.isBefore(now.toLocalDate())) }
            if (prn.isNotEmpty()) PrnCard(state, prn, now, onLogPrn, onOpenPrnIntake)

            val low = state.treatments.filter { it.isLowStock }
            if (low.isNotEmpty()) StockBanner(low, onOpenTreatments)

            val water = state.settings.water
            WaterCard(state.entryFor(now.toLocalDate()).waterGlasses, water.goalGlasses, water.glassMl, onAddWater)

            if (!state.entryFor(now.toLocalDate()).hasJournal) JournalPrompt(state.game.petName, onOpenJournal)

            state.appointments
                .filter { !it.dateTime.isBefore(now.minusHours(1)) && it.dateTime.isBefore(now.plusDays(8)) }
                .minByOrNull { it.dateTime }
                ?.let { a ->
                    SectionTitle("Prochain rendez-vous")
                    com.medipote.app.ui.agenda.AppointmentCard(a, now) { onOpenAppointment(a) }
                }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HealthTag(name: String, condition: PetCondition, modifier: Modifier) {
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
                    "${item.occurrence.dose.timeLabel} · ${t.unit.format(item.occurrence.dose.quantity)}",
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
                        t.unit.format(item.occurrence.dose.quantity),
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
                    "Plus d'une heure en avance, elle rapporte ${com.medipote.app.domain.Rules.POINTS_EARLY} points " +
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
private fun StockBanner(low: List<com.medipote.app.data.Treatment>, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = AppTheme.status.criticalContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Inventory2, null, tint = AppTheme.status.critical)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Pense à racheter", style = MaterialTheme.typography.titleSmall)
                Text(
                    low.joinToString(" · ") { t -> "${t.displayName} : ${t.daysLeft} j" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** As-needed medications: one tap to log an intake, and a nudge to rate the last one. */
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
                            if (glasses >= goal) " · objectif atteint 🎉" else " · +${com.medipote.app.domain.Rules.WATER_GLASS_POINTS} pts par verre",
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

@Composable
private fun NotificationsBanner(onFix: () -> Unit) {
    Card(
        onClick = onFix,
        colors = CardDefaults.cardColors(containerColor = AppTheme.status.warningContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsOff, null, tint = AppTheme.status.warning)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Les notifications sont bloquées", style = MaterialTheme.typography.titleSmall)
                Text("Tu ne recevras pas les rappels. Touche ici pour les autoriser.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
