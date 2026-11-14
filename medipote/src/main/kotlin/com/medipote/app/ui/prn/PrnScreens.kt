package com.medipote.app.ui.prn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.DoseUnit
import com.medipote.app.data.PrnIntake
import com.medipote.app.data.Treatment
import com.medipote.app.domain.Prn
import com.medipote.app.domain.Reports
import com.medipote.app.domain.TimeSlot
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.dayMonth
import com.medipote.app.ui.components.fullDay
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.stats.BarChart
import com.medipote.app.ui.stats.InsightsCard
import com.medipote.app.ui.stats.PeriodSelector
import com.medipote.app.ui.stats.RankedBars
import com.medipote.app.ui.stats.StatCard
import com.medipote.app.ui.stats.StatTile
import com.medipote.app.ui.stats.decimal
import com.medipote.app.ui.stats.pct
import com.medipote.app.ui.theme.AppTheme
import com.medipote.app.ui.treatments.SwitchRow
import com.medipote.app.ui.treatments.limitsLabel
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private val STEPS = listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0, 40.0, 50.0)

private fun step(q: Double, up: Boolean): Double {
    val i = STEPS.indexOfFirst { it >= q }.let { if (it < 0) STEPS.lastIndex else it }
    return if (up) STEPS[(i + 1).coerceAtMost(STEPS.lastIndex)] else STEPS[(i - 1).coerceAtLeast(0)]
}

/** Labels the user typed before come first, then the built-in ones. */
private fun suggestions(used: List<String>, builtIn: List<String>): List<String> {
    val counts = used.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
    val ranked = counts.entries.sortedByDescending { it.value }.map { it.key }
    return (ranked + builtIn).distinctBy { it.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrnLogScreen(
    state: AppState,
    treatment: Treatment,
    existing: PrnIntake?,
    now: LocalDateTime,
    onSave: (PrnIntake) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val start = existing?.takenAt ?: now.truncatedTo(ChronoUnit.MINUTES)
    var date by rememberSaveable { mutableStateOf(start.toLocalDate()) }
    var minute by rememberSaveable { mutableStateOf(start.hour * 60 + start.minute) }
    var quantity by rememberSaveable { mutableStateOf(existing?.quantity ?: treatment.prnQuantity) }
    var reason by rememberSaveable { mutableStateOf(existing?.reason.orEmpty()) }
    var customReason by rememberSaveable { mutableStateOf("") }
    var intensity by remember { mutableFloatStateOf(existing?.intensity?.toFloat() ?: 0f) }
    val contexts = remember { mutableStateListOf<String>().apply { addAll(existing?.contexts.orEmpty()) } }
    var customContext by rememberSaveable { mutableStateOf("") }
    var relief by rememberSaveable { mutableStateOf(existing?.relief) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val takenAt = date.atTime(LocalTime.of(minute / 60, minute % 60))
    val inFuture = takenAt.isAfter(now.plusMinutes(5))
    val sinceIntake = Duration.between(takenAt, now).toMinutes()
    // Asking an hour later only makes sense for a fresh intake that hasn't been rated yet.
    val canAsk = relief == null && sinceIntake < Prn.RELIEF_CHECK_MINUTES
    var askRelief by rememberSaveable { mutableStateOf(existing?.askRelief ?: true) }
    val showRelief = existing != null || sinceIntake >= 30

    val past = remember(state.prnIntakes) { state.prnIntakes }
    val reasons = remember(past) { suggestions(past.map { it.reason }, Prn.REASONS) }
    val contextOptions = remember(past, contexts.toList()) { suggestions(past.flatMap { it.contexts } + contexts, Prn.CONTEXTS) }
    val warnings = Prn.warnings(state, treatment, takenAt, excludeId = existing?.id)
    val last = Prn.last(state, treatment.id, takenAt)?.takeIf { it.id != existing?.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (existing == null) "Noter une prise" else "Modifier la prise")
                        Text(treatment.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        val r = customReason.trim().ifEmpty { reason }
                        onSave(
                            PrnIntake(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                treatmentId = treatment.id,
                                takenAt = takenAt,
                                quantity = quantity,
                                reason = r,
                                intensity = intensity.roundToInt().takeIf { it > 0 },
                                contexts = contexts.toList(),
                                relief = relief,
                                askRelief = askRelief && canAsk,
                                notes = notes.trim(),
                            ),
                        )
                    },
                    enabled = !inFuture,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text(if (existing == null) "Enregistrer la prise" else "Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            last?.let {
                Text(
                    "Prise précédente : ${Prn.formatDuration(Duration.between(it.takenAt, takenAt))} avant (${it.takenAt.format(dayMonth)} à ${it.takenAt.format(hourMinute)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Quand ?")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DateField("Jour", date, { date = it }, Modifier.weight(1f), maxDate = now.toLocalDate())
                TextButton(onClick = { pickTime = true }) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("%02d:%02d".format(minute / 60, minute % 60), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (existing == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L to "Maintenant", 30L to "Il y a 30 min", 60L to "Il y a 1 h", 120L to "Il y a 2 h").forEach { (ago, label) ->
                        val t = now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(ago)
                        FilterChip(
                            selected = takenAt == t,
                            onClick = { date = t.toLocalDate(); minute = t.hour * 60 + t.minute },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (inFuture) {
                Text("Cette heure est dans le futur.", color = AppTheme.status.critical, style = MaterialTheme.typography.bodySmall)
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quantité", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { quantity = step(quantity, false) }, enabled = quantity > 0.25) { Icon(Icons.Rounded.Remove, "Moins") }
                    Text(treatment.unit.format(quantity), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { quantity = step(quantity, true) }) { Icon(Icons.Rounded.Add, "Plus") }
                }
            }

            if (warnings.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = AppTheme.status.warningContainer)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = AppTheme.status.warning)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            warnings.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text(
                                "En cas de doute, demande conseil à ton pharmacien ou à ton médecin.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            SectionTitle("Pourquoi ?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { r ->
                    val on = customReason.isBlank() && reason.equals(r, ignoreCase = true)
                    FilterChip(
                        selected = on,
                        onClick = { customReason = ""; reason = if (on) "" else r },
                        label = { Text(r) },
                        leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    )
                }
            }
            OutlinedTextField(
                value = customReason,
                onValueChange = { customReason = it.take(40) },
                label = { Text("Autre motif") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Intensité de la gêne", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    if (intensity < 1f) "non notée" else "${intensity.roundToInt()}/10",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (intensity < 1f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            Slider(value = intensity, onValueChange = { intensity = it.roundToInt().toFloat() }, valueRange = 0f..10f, steps = 9)

            SectionTitle("Contexte")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                contextOptions.forEach { c ->
                    val on = c in contexts
                    FilterChip(
                        selected = on,
                        onClick = { if (on) contexts.remove(c) else contexts.add(c) },
                        label = { Text(c) },
                        leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    )
                }
            }
            OutlinedTextField(
                value = customContext,
                onValueChange = { customContext = it.take(40) },
                label = { Text("Ajouter un contexte") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    customContext.trim().takeIf { it.isNotEmpty() && it !in contexts }?.let(contexts::add)
                    customContext = ""
                }),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            customContext.trim().takeIf { it.isNotEmpty() && it !in contexts }?.let(contexts::add)
                            customContext = ""
                        },
                        enabled = customContext.isNotBlank(),
                    ) { Icon(Icons.Rounded.Add, "Ajouter") }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (showRelief) {
                SectionTitle("Est-ce que ça a fait effet ?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prn.RELIEF_LABELS.forEachIndexed { i, label ->
                        val v = i + 1
                        FilterChip(selected = relief == v, onClick = { relief = if (relief == v) null else v }, label = { Text(label) })
                    }
                }
            }
            AnimatedVisibility(canAsk) {
                SwitchRow(
                    "Me demander dans 1 h si ça a marché",
                    askRelief,
                    supporting = "Une notification pour noter l'effet, et des stats d'efficacité plus justes",
                ) { askRelief = it }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (facultatif)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Disclaimer("Les prises « si besoin » ne rapportent pas de points : l'appli ne veut pas t'encourager à en prendre plus.")
        }
    }

    if (pickTime) {
        TimePickerModal(minute, onDismiss = { pickTime = false }, title = "Heure de la prise") {
            minute = it
            pickTime = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette prise ?") },
            text = { Text("Elle disparaîtra des statistiques et sa quantité sera rendue au stock.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private enum class PrnPeriod(val days: Int?, val label: String) {
    MONTH(30, "30 j"), QUARTER(90, "90 j"), YEAR(365, "1 an"), ALL(null, "Tout")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrnStatsScreen(
    state: AppState,
    treatment: Treatment,
    now: LocalDateTime,
    onBack: () -> Unit,
    onLog: () -> Unit,
    onEditIntake: (PrnIntake) -> Unit,
    onEditTreatment: () -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(PrnPeriod.QUARTER) }
    val r = remember(state, now.toLocalDate(), now.hour, period) { Reports.prn(state, treatment.id, now, period.days) }
    val unit: DoseUnit = treatment.unit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(treatment.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Si besoin", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { IconButton(onClick = onEditTreatment) { Icon(Icons.Outlined.Edit, "Modifier le médicament") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onLog, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Noter une prise") })
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodSelector(PrnPeriod.entries.map { it to it.label }, period, { period = it })
            limitsLabel(treatment)?.let {
                Text(
                    "${unit.format(treatment.prnQuantity).capitalized()} par prise · $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Prises",
                    "${r.count}",
                    "${r.daysWithIntake} jour${if (r.daysWithIntake > 1) "s" else ""} sur ${r.days}",
                    Modifier.weight(1f),
                )
                StatTile("Par semaine", decimal(r.perWeek), "en moyenne", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Écart moyen",
                    r.averageIntervalHours?.let { Prn.formatDuration(Duration.ofMinutes((it * 60).roundToInt().toLong())) } ?: "—",
                    "entre deux prises",
                    Modifier.weight(1f),
                )
                StatTile(
                    "Efficacité",
                    pct(r.effectiveShare),
                    if (r.reliefRated == 0) "à noter après une prise" else "${r.reliefRated} prise${if (r.reliefRated > 1) "s" else ""} notée${if (r.reliefRated > 1) "s" else ""}",
                    Modifier.weight(1f),
                )
            }

            if (r.count == 0) {
                StatCard("Pas encore de prise sur cette période") {
                    Text(
                        "Note chaque prise avec son motif et son contexte : tu verras ici à quelle heure et dans quelles situations tu en as besoin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                InsightsCard(r.insights)

                StatCard("À quelle heure ?", subtitle = r.typicalMinuteOfDay?.let { "Heure habituelle : vers ${Reports.formatTime(it)}" } ?: "Prises réparties dans la journée") {
                    val hours = r.hourCounts
                    BarChart(
                        values = hours.map { it.toFloat() },
                        axisLabels = mapOf(0 to "0 h", 6 to "6 h", 12 to "12 h", 18 to "18 h", 23 to "23 h"),
                        describe = { h -> "Entre $h h et ${(h + 1) % 24} h · ${hours[h]} prise${if (hours[h] > 1) "s" else ""}" },
                        initialSelected = hours.indices.maxBy { hours[it] },
                        accessibility = "Prises par heure de la journée",
                    )
                    HorizontalDivider()
                    RankedBars(TimeSlot.entries.map { it.label to (r.slotCounts[it] ?: 0) }, r.count, max = 5)
                }

                StatCard("Quel jour ?") {
                    val days = DayOfWeek.entries
                    BarChart(
                        values = days.map { (r.weekdayCounts[it] ?: 0).toFloat() },
                        axisLabels = days.indices.associateWith { days[it].getDisplayName(TextStyle.SHORT, Locale.FRANCE).trimEnd('.').capitalized() },
                        describe = { i ->
                            val n = r.weekdayCounts[days[i]] ?: 0
                            "${days[i].getDisplayName(TextStyle.FULL, Locale.FRANCE).capitalized()} · $n prise${if (n > 1) "s" else ""}"
                        },
                        initialSelected = days.indices.maxBy { r.weekdayCounts[days[it]] ?: 0 },
                        height = 100.dp,
                    )
                }

                StatCard("Pourquoi ?", subtitle = if (r.reasons.isEmpty()) null else "Motifs notés") {
                    if (r.reasons.isEmpty()) Hint("Ajoute un motif quand tu notes une prise.")
                    else RankedBars(r.reasons, r.count)
                    r.averageIntensity?.let {
                        Text(
                            "Intensité moyenne de la gêne : ${decimal(it)}/10",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                StatCard("Dans quel contexte ?") {
                    if (r.contexts.isEmpty()) Hint("Ajoute le contexte (travail, stress, fatigue…) quand tu notes une prise.")
                    else RankedBars(r.contexts, r.count)
                    r.cycleShare?.let { (during, days) ->
                        Text(
                            "Pendant les règles : ${pct(during)} des prises (pour ${pct(days)} des jours).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (r.reliefRated > 0) {
                    StatCard("Effet ressenti", subtitle = "Sur ${r.reliefRated} prise${if (r.reliefRated > 1) "s" else ""} notée${if (r.reliefRated > 1) "s" else ""}") {
                        val counts = Prn.RELIEF_LABELS.mapIndexed { i, label -> label to r.intakes.count { it.relief == i + 1 } }
                        RankedBars(counts.reversed(), r.reliefRated, max = 5)
                    }
                }
            }

            StatCard("12 dernières semaines") {
                val weeks = r.weekly
                BarChart(
                    values = weeks.map { it.second.toFloat() },
                    axisLabels = mapOf(0 to weeks.first().first.format(dayMonth), 6 to weeks[6].first.format(dayMonth), 11 to "Cette sem."),
                    describe = { i ->
                        val (start, n) = weeks[i]
                        "Semaine du ${start.format(dayMonth)} · $n prise${if (n > 1) "s" else ""}"
                    },
                    height = 100.dp,
                )
            }

            if (r.intakes.isNotEmpty()) {
                SectionTitle("Historique")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column {
                        r.intakes.sortedByDescending { it.takenAt }.take(60).forEachIndexed { i, intake ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            IntakeRow(intake, unit) { onEditIntake(intake) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun IntakeRow(intake: PrnIntake, unit: DoseUnit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${intake.takenAt.format(fullDay).capitalized()} · ${intake.takenAt.format(hourMinute)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            val details = listOfNotNull(
                unit.format(intake.quantity),
                intake.reason.ifBlank { null },
                intake.intensity?.let { "gêne $it/10" },
                intake.contexts.takeIf { it.isNotEmpty() }?.joinToString(", ")?.lowercase(),
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        intake.relief?.let {
            Text(
                Prn.RELIEF_LABELS[it - 1],
                style = MaterialTheme.typography.labelMedium,
                color = if (it >= 4) AppTheme.status.good else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
