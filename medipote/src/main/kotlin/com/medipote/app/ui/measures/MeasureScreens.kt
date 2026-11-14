package com.medipote.app.ui.measures

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Measurement
import com.medipote.app.domain.Assessment
import com.medipote.app.domain.Level
import com.medipote.app.domain.Measures
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.dayMonth
import com.medipote.app.ui.components.fullDay
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.stats.InsightsCard
import com.medipote.app.ui.stats.LineChart
import com.medipote.app.ui.stats.LinePoint
import com.medipote.app.ui.stats.PeriodSelector
import com.medipote.app.ui.stats.StatCard
import com.medipote.app.ui.stats.StatTile
import com.medipote.app.ui.stats.pct
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun levelColor(level: Level?): Color = when (level) {
    Level.NORMAL -> AppTheme.status.good
    Level.HIGH, Level.LOW -> AppTheme.status.warning
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun whenLabel(at: LocalDateTime, now: LocalDateTime): String {
    val days = ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())
    val time = at.format(hourMinute)
    return when (days) {
        0L -> "aujourd'hui à $time"
        1L -> "hier à $time"
        else -> "le ${at.format(dayMonth)} à $time"
    }
}

private fun valueWithUnit(m: Measurement) = "${m.display} ${m.type.unit}"

/** The "Mesures" tab: one card per measurement kind shown in the settings. */
@Composable
fun MeasuresScreen(state: AppState, now: LocalDateTime, onAdd: (MeasureType) -> Unit, onOpen: (MeasureType) -> Unit, onSettings: () -> Unit) {
    val types = MeasureType.entries.filter { it in state.settings.measures }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        types.forEach { type -> MeasureCard(state, type, now, { onAdd(type) }, { onOpen(type) }) }
        TextButton(onClick = onSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Choisir les mesures affichées")
        }
        Disclaimer("Les repères affichés sont indicatifs. Seul ton médecin peut interpréter tes mesures.")
    }
}

@Composable
private fun MeasureCard(state: AppState, type: MeasureType, now: LocalDateTime, onAdd: () -> Unit, onOpen: () -> Unit) {
    val last = Measures.latest(state, type)
    val assessment = last?.let { Measures.assess(it, state.profile) }
    val recent = remember(state.measurements, type) {
        state.measurements.filter { it.type == type && it.at.isAfter(now.minusDays(30)) }.sortedBy { it.at }
    }
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(type.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Text(type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ajouter")
                }
            }
            if (last == null) {
                Text("Aucune mesure pour l'instant.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(last.display, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(6.dp))
                    Text(type.unit, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                    last.pulse?.let { Text(" · pouls $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp)) }
                }
                Text(
                    whenLabel(last.at, now).capitalized() + (last.context.takeIf { it.isNotBlank() }?.let { " · ${it.lowercase()}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                assessment?.let { AssessmentLine(it) }
                if (recent.size >= 2) {
                    LineChart(
                        points = recent.map { LinePoint(it.at, listOfNotNull(it.value, it.value2)) },
                        describe = { i -> "${recent[i].at.format(dayMonth)} · ${valueWithUnit(recent[i])}" },
                        format = { type.format(it) },
                        height = 72.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssessmentLine(a: Assessment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("●", color = levelColor(a.level), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(6.dp))
        Text(a.text, style = MaterialTheme.typography.bodySmall)
    }
}

private enum class MeasurePeriod(val days: Int, val label: String) { MONTH(30, "30 j"), QUARTER(90, "90 j"), YEAR(365, "1 an") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureDetailScreen(
    state: AppState,
    type: MeasureType,
    now: LocalDateTime,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Measurement) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(MeasurePeriod.MONTH) }
    val r = remember(state.measurements, state.profile, now.toLocalDate(), period) { Measures.report(state, type, now.toLocalDate(), period.days) }
    val band = when (type) {
        MeasureType.BLOOD_PRESSURE -> 60.0..135.0
        MeasureType.HEART_RATE -> 50.0..100.0
        MeasureType.GLUCOSE -> 0.70..1.10
        MeasureType.TEMPERATURE -> 35.5..37.9
        MeasureType.OXYGEN -> 95.0..100.0
        MeasureType.WEIGHT -> null
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${type.emoji} ${type.label}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Ajouter") }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodSelector(MeasurePeriod.entries.map { it to it.label }, period, { period = it })
            if (r.count == 0) {
                StatCard("Pas de mesure sur cette période") {
                    Text("Ajoute une mesure pour suivre son évolution ici.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Moyenne",
                        if (type == MeasureType.BLOOD_PRESSURE) "${r.average!!.roundToInt()}/${r.average2?.roundToInt() ?: "?"}" else type.format(r.average!!),
                        "${type.unit} · ${r.count} mesure${if (r.count > 1) "s" else ""}",
                        Modifier.weight(1f),
                    )
                    if (type == MeasureType.WEIGHT) {
                        StatTile(
                            "Évolution",
                            r.change?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + type.format(kotlin.math.abs(it)) } ?: "—",
                            "kg sur la période",
                            Modifier.weight(1f),
                        )
                    } else {
                        StatTile("Dans les repères", pct(r.inRange), "des mesures", Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Minimum", r.min?.display ?: "—", r.min?.at?.format(dayMonth), Modifier.weight(1f))
                    StatTile("Maximum", r.max?.display ?: "—", r.max?.at?.format(dayMonth), Modifier.weight(1f))
                }
                InsightsCard(r.insights)
                StatCard(
                    "Évolution",
                    subtitle = when (type) {
                        MeasureType.BLOOD_PRESSURE -> "Systolique et diastolique · zone verte : repères"
                        MeasureType.WEIGHT -> null
                        else -> "Zone verte : repères"
                    },
                ) {
                    LineChart(
                        points = r.values.map { LinePoint(it.at, listOfNotNull(it.value, it.value2)) },
                        describe = { i ->
                            val m = r.values[i]
                            "${m.at.format(dayMonth)} ${m.at.format(hourMinute)} · ${valueWithUnit(m)}" + (m.pulse?.let { " · pouls $it" } ?: "")
                        },
                        format = { type.format(it) },
                        band = band,
                    )
                }
                SectionTitle("Historique")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column {
                        r.values.reversed().take(100).forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            val a = Measures.assess(m, state.profile)
                            Row(
                                Modifier.fillMaxWidth().clickable { onEdit(m) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.at.format(fullDay).capitalized()} · ${m.at.format(hourMinute)}", style = MaterialTheme.typography.bodyLarge)
                                    val details = listOfNotNull(m.context.ifBlank { null }, m.pulse?.let { "pouls $it" }, m.notes.ifBlank { null })
                                    if (details.isNotEmpty()) {
                                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Text(valueWithUnit(m), style = MaterialTheme.typography.titleSmall, color = if (a?.level == Level.NORMAL || a == null) MaterialTheme.colorScheme.onSurface else AppTheme.status.warning)
                            }
                        }
                    }
                }
            }
            Disclaimer("Les repères affichés sont indicatifs. Seul ton médecin peut interpréter tes mesures.")
            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun parse(text: String): Double? = text.replace(',', '.').trim().toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeasureEditorScreen(
    state: AppState,
    type: MeasureType,
    existing: Measurement?,
    now: LocalDateTime,
    onSave: (Measurement) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val start = existing?.at ?: now.truncatedTo(ChronoUnit.MINUTES)
    var date by rememberSaveable { mutableStateOf(start.toLocalDate()) }
    var minute by rememberSaveable { mutableStateOf(start.hour * 60 + start.minute) }
    fun fmt(v: Double?) = v?.let { type.format(it) }.orEmpty()
    var valueText by rememberSaveable { mutableStateOf(fmt(existing?.value)) }
    var value2Text by rememberSaveable { mutableStateOf(existing?.value2?.toInt()?.toString().orEmpty()) }
    var pulseText by rememberSaveable { mutableStateOf(existing?.pulse?.toString().orEmpty()) }
    var context by rememberSaveable { mutableStateOf(existing?.context ?: if (type == MeasureType.GLUCOSE && start.hour < 10) "À jeun" else "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val value = parse(valueText)
    val value2 = parse(value2Text)
    val pulse = pulseText.trim().toIntOrNull()
    val bp = type == MeasureType.BLOOD_PRESSURE
    val valueOk = value != null && value in type.min..type.max
    val value2Ok = !bp || (value2 != null && value2 in 30.0..160.0 && value != null && value2 < value)
    val pulseOk = pulseText.isBlank() || (pulse != null && pulse in 25..250)
    val at = date.atTime(LocalTime.of(minute / 60, minute % 60))
    val draft = if (valueOk && value2Ok) Measurement(
        id = existing?.id ?: UUID.randomUUID().toString(), type = type, at = at, value = value!!,
        value2 = if (bp) value2 else null, pulse = if (pulseOk) pulse else null,
        context = context, notes = notes.trim(),
    ) else null
    val assessment = draft?.let { Measures.assess(it, state.profile) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "${type.label} : nouvelle mesure" else "Modifier la mesure", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { draft?.let(onSave) },
                    enabled = draft != null && pulseOk && !at.isAfter(now.plusMinutes(5)),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DateField("Jour", date, { date = it }, Modifier.weight(1f), maxDate = now.toLocalDate())
                TextButton(onClick = { pickTime = true }) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("%02d:%02d".format(minute / 60, minute % 60), style = MaterialTheme.typography.titleMedium)
                }
            }
            val decimalKeyboard = KeyboardOptions(keyboardType = if (type.decimals > 0) KeyboardType.Decimal else KeyboardType.Number)
            if (bp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = valueText, onValueChange = { valueText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Systolique") }, supportingText = { Text("le grand chiffre") },
                        suffix = { Text("mmHg") }, singleLine = true, keyboardOptions = decimalKeyboard,
                        isError = valueText.isNotEmpty() && !valueOk, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = value2Text, onValueChange = { value2Text = it.filter(Char::isDigit).take(3) },
                        label = { Text("Diastolique") }, supportingText = { Text("le petit chiffre") },
                        suffix = { Text("mmHg") }, singleLine = true, keyboardOptions = decimalKeyboard,
                        isError = value2Text.isNotEmpty() && !value2Ok, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = pulseText, onValueChange = { pulseText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Pouls (facultatif)") }, suffix = { Text("bpm") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !pulseOk, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { v -> valueText = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = { Text(type.label) },
                    suffix = { Text(type.unit) },
                    singleLine = true,
                    keyboardOptions = decimalKeyboard,
                    isError = valueText.isNotEmpty() && !valueOk,
                    supportingText = if (valueText.isNotEmpty() && !valueOk) ({ Text("Valeur attendue entre ${type.format(type.min)} et ${type.format(type.max)}") }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == MeasureType.GLUCOSE) {
                    SectionTitle("Moment")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Measures.GLUCOSE_CONTEXTS.forEach { c ->
                            FilterChip(selected = context == c, onClick = { context = if (context == c) "" else c }, label = { Text(c) })
                        }
                    }
                }
            }
            assessment?.let {
                Card(colors = CardDefaults.cardColors(containerColor = if (it.level == Level.NORMAL) MaterialTheme.colorScheme.surfaceContainerLow else AppTheme.status.warningContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = levelColor(it.level))
                        Spacer(Modifier.width(8.dp))
                        Text(it.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (facultatif)") },
                placeholder = { Text(if (bp) "Bras gauche, après le café…" else "Contexte, symptômes…") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Disclaimer("Les repères affichés sont indicatifs. Seul ton médecin peut interpréter tes mesures.")
        }
    }
    if (pickTime) {
        TimePickerModal(minute, onDismiss = { pickTime = false }, title = "Heure de la mesure") {
            minute = it
            pickTime = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette mesure ?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

/** Home: the last value of each shown measurement, with a quick "+". */
@Composable
fun HomeMeasuresCard(state: AppState, now: LocalDateTime, onAdd: (MeasureType) -> Unit, onOpen: (MeasureType) -> Unit) {
    val types = MeasureType.entries.filter { it in state.settings.measures }
    if (types.isEmpty()) return
    SectionTitle("Mes mesures")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            types.forEachIndexed { i, type ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                val last = Measures.latest(state, type)
                val level = last?.let { Measures.assess(it, state.profile)?.level }
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(type) }.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(type.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(type.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            last?.let { "${valueWithUnit(it)} · ${whenLabel(it.at, now)}" } ?: "Pas encore de mesure",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (level == Level.HIGH || level == Level.LOW) AppTheme.status.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = { onAdd(type) }) { Icon(Icons.Rounded.Add, "Ajouter : ${type.label}") }
                }
            }
        }
    }
}
