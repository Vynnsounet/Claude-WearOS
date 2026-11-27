package com.medipote.app.ui.motivation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.Awakening
import com.medipote.app.data.DayEntry
import com.medipote.app.data.SleepNight
import com.medipote.app.domain.DoseItem
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Game
import com.medipote.app.domain.PetMood
import com.medipote.app.domain.RitualKind
import com.medipote.app.domain.Rituals
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Sleep
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.pet.PetView
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDateTime

private enum class Step { SLEEP, DOSES, MOOD, JOURNAL, WATER, DONE }

class RitualActions(
    val give: (DoseItem) -> Unit,
    val giveEarly: (DoseItem) -> Unit,
    val catchUp: (DoseItem, LocalDateTime) -> Unit,
    val saveEntry: (DayEntry) -> Unit,
    val addWater: (Int) -> Unit,
    val saveSleep: (SleepNight) -> Unit,
    val complete: (RitualKind) -> Unit,
    val openSleepEditor: () -> Unit,
    val close: () -> Unit,
)

/** Doses the ritual deals with: the morning ones, or what's left of the day in the evening. */
fun ritualDoses(state: AppState, kind: RitualKind, now: LocalDateTime): List<DoseItem> {
    val day = Rituals.day(now, kind)
    val items = Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, day, now)
    return if (kind == RitualKind.MORNING) items.filter { it.occurrence.dose.minuteOfDay < 12 * 60 }
    else items.filter { it.occurrence.dose.minuteOfDay >= 12 * 60 || it.state != DoseState.TAKEN }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RitualScreen(state: AppState, kind: RitualKind, now: LocalDateTime, actions: RitualActions, animate: Boolean = true) {
    val day = Rituals.day(now, kind)
    val steps = remember {
        buildList {
            if (kind == RitualKind.MORNING) {
                if (state.sleep.none { it.date == day }) add(Step.SLEEP)
                if (ritualDoses(state, kind, now).isNotEmpty()) add(Step.DOSES)
                add(Step.MOOD)
            } else {
                if (ritualDoses(state, kind, now).isNotEmpty()) add(Step.DOSES)
                add(Step.JOURNAL)
                add(Step.WATER)
            }
            add(Step.DONE)
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val alreadyDone = remember { Rituals.done(state.game, now, kind) }
    val step = steps[index]
    fun next() {
        if (index < steps.lastIndex) index++
        if (steps[index] == Step.DONE) actions.complete(kind)
    }
    val pet = state.game.petName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${kind.emoji} ${kind.label}") },
                navigationIcon = { IconButton(onClick = actions.close) { Icon(Icons.Rounded.Close, "Fermer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (step != Step.DONE) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = ::next, modifier = Modifier.weight(1f).height(52.dp)) { Text("Passer") }
                    // The "Suivant" buttons of the steps that save something are inside the step.
                    if (step == Step.DOSES || step == Step.WATER) {
                        Button(onClick = ::next, modifier = Modifier.weight(2f).height(52.dp)) { Text("Suivant") }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LinearProgressIndicator(
                progress = { (index + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp).clip(MaterialTheme.shapes.large)) {
                    PetView(
                        if (step == Step.DONE) PetMood.HAPPY else PetMood.CONTENT,
                        state.game.equipped, Modifier.fillMaxSize(), animate = animate,
                        stage = com.medipote.app.domain.Evolution.growth(state).stage,
                        time = com.medipote.app.domain.TimeOfDay.of(now),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Text(
                        when (step) {
                            Step.SLEEP -> "Bien dormi ? Raconte-moi ta nuit."
                            Step.DOSES -> if (kind == RitualKind.MORNING) "On s'occupe des prises du matin ?" else "Il reste ces prises pour aujourd'hui."
                            Step.MOOD -> "Et toi, comment tu te sens ce matin ?"
                            Step.JOURNAL -> "Raconte-moi un petit bout de ta journée."
                            Step.WATER -> "Combien de verres d'eau aujourd'hui ?"
                            Step.DONE -> if (kind == RitualKind.MORNING) "Merci ! Belle journée à toi ☀️" else "Merci, bonne nuit 🌙"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            AnimatedContent(step, label = "step") { s ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (s) {
                        Step.SLEEP -> QuickSleep(state, day, onSave = { actions.saveSleep(it); next() }, onDetails = actions.openSleepEditor)
                        Step.DOSES -> DosesStep(state, kind, now, actions)
                        Step.MOOD -> MoodStep(state.entryFor(day), onSave = { actions.saveEntry(it); next() })
                        Step.JOURNAL -> JournalStep(state.entryFor(day), onSave = { actions.saveEntry(it); next() })
                        Step.WATER -> WaterStep(state, now, actions.addWater)
                        Step.DONE -> DoneStep(kind, pet, rewarded = !alreadyDone, actions.close)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QuickSleep(state: AppState, day: java.time.LocalDate, onSave: (SleepNight) -> Unit, onDetails: () -> Unit) {
    val base = remember { Sleep.newNight(state, day) }
    var bed by rememberSaveable { mutableIntStateOf(Sleep.minuteOf(base.bedtime)) }
    var wake by rememberSaveable { mutableIntStateOf(Sleep.minuteOf(base.wakeTime)) }
    var wakeUps by rememberSaveable { mutableIntStateOf(0) }
    var quality by rememberSaveable { mutableStateOf<Int?>(null) }
    var rested by rememberSaveable { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    val wakeTime = day.atTime(wake / 60, wake % 60)
    val bedtime = Sleep.bedtimeFor(day, bed)
    val night = run {
        val start = bedtime.plusMinutes(base.latencyMinutes.toLong())
        val span = java.time.Duration.between(start, wakeTime).toMinutes()
        val awakenings = (1..wakeUps).map { i -> Awakening(start.plusMinutes(span * i / (wakeUps + 1)), 10) }
        base.copy(bedtime = bedtime, wakeTime = wakeTime, awakenings = awakenings, quality = quality, rested = rested)
    }
    val ok = Sleep.problems(night).isEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeTile("Coucher", bed, Icons.Outlined.Bedtime, Modifier.weight(1f)) { picking = "bed" }
        TimeTile("Réveil", wake, Icons.Outlined.WbSunny, Modifier.weight(1f)) { picking = "wake" }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Réveils dans la nuit", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { wakeUps = (wakeUps - 1).coerceAtLeast(0) }, enabled = wakeUps > 0) { Icon(Icons.Rounded.Remove, "Moins") }
        Text("$wakeUps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp))
        FilledTonalIconButton(onClick = { wakeUps = (wakeUps + 1).coerceAtMost(8) }) { Icon(Icons.Rounded.Add, "Plus") }
    }
    Text("Bonne nuit ?", style = MaterialTheme.typography.titleSmall)
    FacePicker(quality, "Bonne nuit", { quality = it })
    Text("Reposante ?", style = MaterialTheme.typography.titleSmall)
    FacePicker(rested, "Nuit reposante", { rested = it })
    if (ok) Text("${Sleep.format(night.asleepMinutes)} de sommeil", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Text(Sleep.problems(night).first(), style = MaterialTheme.typography.bodyMedium, color = AppTheme.status.critical)
    Button(onClick = { onSave(night) }, enabled = ok, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Enregistrer ma nuit") }
    TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("Détailler (heures des réveils, causes…)") }
    picking?.let { which ->
        TimePickerModal(if (which == "bed") bed else wake, onDismiss = { picking = null }, title = if (which == "bed") "Heure du coucher" else "Heure du réveil") {
            if (which == "bed") bed = it else wake = it
            picking = null
        }
    }
}

@Composable
private fun TimeTile(label: String, minute: Int, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%02d:%02d".format(minute / 60, minute % 60), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun DosesStep(state: AppState, kind: RitualKind, now: LocalDateTime, actions: RitualActions) {
    val items = ritualDoses(state, kind, now)
    if (items.isEmpty()) {
        Text("Rien à prendre pour l'instant 👍", style = MaterialTheme.typography.bodyLarge)
        return
    }
    items.forEach { item ->
        val o = item.occurrence
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(o.dose.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 56.dp).padding(end = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.treatment.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(o.treatment.unit.format(o.quantity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    item.state == DoseState.TAKEN -> Text("✓ Pris", style = MaterialTheme.typography.labelLarge, color = AppTheme.status.good, modifier = Modifier.padding(end = 12.dp))
                    item.canGive -> FilledTonalButton(onClick = { actions.give(item) }) { Text("Pris") }
                    item.canMarkEarly(now) -> TextButton(onClick = { actions.giveEarly(item) }) { Text("Déjà pris") }
                    item.state == DoseState.MISSED && !o.date.isBefore(now.toLocalDate().minusDays(Game.CATCH_UP_DAYS)) ->
                        TextButton(onClick = { actions.catchUp(item, o.scheduled) }) { Text("Je l'ai pris") }
                    else -> Text("Plus tard", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun MoodStep(entry: DayEntry, onSave: (DayEntry) -> Unit) {
    var mental by rememberSaveable { mutableStateOf(entry.mental) }
    var physical by rememberSaveable { mutableStateOf(entry.physical) }
    Text("Moral", style = MaterialTheme.typography.titleSmall)
    FacePicker(mental, "Moral", { mental = it }, height = 56)
    Text("Forme physique", style = MaterialTheme.typography.titleSmall)
    FacePicker(physical, "Forme physique", { physical = it }, height = 56)
    Button(onClick = { onSave(entry.copy(mental = mental, physical = physical)) }, enabled = mental != null || physical != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Suivant")
    }
}

@Composable
private fun JournalStep(entry: DayEntry, onSave: (DayEntry) -> Unit) {
    var mental by rememberSaveable { mutableStateOf(entry.mental) }
    var notes by rememberSaveable { mutableStateOf(entry.notes) }
    Text("Ton humeur ce soir", style = MaterialTheme.typography.titleSmall)
    FacePicker(mental, "Humeur", { mental = it })
    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Un mot sur ta journée ? (facultatif)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = { onSave(entry.copy(mental = mental, notes = notes.trim())) },
        enabled = mental != null || notes.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Suivant") }
}

@Composable
private fun WaterStep(state: AppState, now: LocalDateTime, onAdd: (Int) -> Unit) {
    val water = state.settings.water
    val drunk = com.medipote.app.domain.Water.ml(state, now.toLocalDate())
    val goal = com.medipote.app.domain.Water.goalMl(water)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onAdd(-1) }, enabled = drunk > 0, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Remove, "Retirer la dernière boisson") }
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(com.medipote.app.domain.Water.format(drunk), style = MaterialTheme.typography.displayMedium)
            Text("sur ${com.medipote.app.domain.Water.format(goal)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledIconButton(onClick = { onAdd(1) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Add, "Ajouter un verre") }
    }
    Text(
        if (drunk >= goal) "Objectif atteint, bravo 💧" else "Encore ${com.medipote.app.domain.Water.format(goal - drunk)} pour l'objectif. Un dernier verre avant de dormir ?",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DoneStep(kind: RitualKind, pet: String, rewarded: Boolean, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (kind == RitualKind.MORNING) "🌅" else "🌙", style = MaterialTheme.typography.displayLarge)
        Text("${kind.label} terminé !", style = MaterialTheme.typography.headlineSmall)
        Text(
            (if (rewarded) "+${Rituals.BONUS} pts · " else "") + "$pet adore passer ce moment avec toi.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Terminer") }
    }
}
