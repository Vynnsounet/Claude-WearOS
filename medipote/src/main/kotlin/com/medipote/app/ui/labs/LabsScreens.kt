package com.medipote.app.ui.labs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.LabResult
import com.medipote.app.data.Sex
import com.medipote.app.domain.Labs
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.dayMonthYear
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
private fun levelColor(level: Labs.Level) = when (level) {
    Labs.Level.NORMAL -> MaterialTheme.colorScheme.primary
    Labs.Level.LOW, Labs.Level.HIGH -> AppTheme.status.warning
    Labs.Level.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** « Analyses » (Suivi): the values of the blood tests the person noted, one card per analysis, with the latest value and its trend. */
@Composable
fun LabsScreen(state: AppState, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val sex = state.profile?.sex
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Note les valeurs de tes prises de sang : l'appli trace la courbe, la situe par rapport aux repères et marque les changements de traitement.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Labs.tracked(state).forEach { marker ->
            val series = Labs.series(state, marker)
            val last = series.last()
            val level = Labs.level(last, sex)
            Card(onClick = { onOpen(marker) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${Labs.emoji(marker)}  ${Labs.name(marker)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${Labs.format(last.value, marker)} ${last.unit}", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "${Labs.levelText(level)} · ${last.date.format(dayMonthYear)}",
                        style = MaterialTheme.typography.bodyMedium, color = levelColor(level),
                    )
                    Labs.trend(series)?.let { Text(it.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("➕  Ajouter une analyse") }
    }
}

/** One analysis over time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabDetailScreen(state: AppState, marker: String, onAdd: () -> Unit, onEdit: (String) -> Unit, onBack: () -> Unit) {
    val sex = state.profile?.sex
    val series = Labs.series(state, marker)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("${Labs.emoji(marker)}  ${Labs.name(marker)}"); Text("Suivi › Analyses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val last = series.lastOrNull()
            if (last != null) {
                val level = Labs.level(last, sex)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${Labs.format(last.value, marker)} ${last.unit}", style = MaterialTheme.typography.headlineMedium)
                        Text(Labs.levelText(level), style = MaterialTheme.typography.bodyLarge, color = levelColor(level))
                        val (low, high) = Labs.range(last, sex)
                        if (low != null || high != null) Text("Repères : ${low?.let { Labs.format(it, marker) } ?: "—"} à ${high?.let { Labs.format(it, marker) } ?: "—"} ${last.unit}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Labs.trend(series)?.let { Text(it.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodyMedium) }
                        Labs.marker(marker)?.hint?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            val changes = if (series.size >= 2) Labs.doseChanges(state, series.first().date, series.last().date) else emptyList()
            if (series.size >= 2) {
                LabChart(series, Labs.range(series.last(), sex), changes.map { it.date })
                if (changes.isNotEmpty()) {
                    SectionTitle("Changements de traitement sur cette période")
                    changes.forEach { c -> Text("• ${c.date.format(dayMonthYear)} : ${c.text}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            SectionTitle("Mes valeurs")
            series.asReversed().forEach { r ->
                Card(onClick = { onEdit(r.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.date.format(dayMonthYear), style = MaterialTheme.typography.bodyLarge)
                            if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${Labs.format(r.value, marker)} ${r.unit}", style = MaterialTheme.typography.titleMedium, color = levelColor(Labs.level(r, sex)))
                    }
                }
            }
            FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("➕  Ajouter une valeur") }
            Text("Les repères sont ceux qu'on trouve le plus souvent : le compte rendu de ton laboratoire fait foi. Une seule valeur ne dit pas tout : parles-en à ton médecin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The values over time, the reference band behind them, and a dashed line where a treatment changed. */
@Composable
fun LabChart(series: List<LabResult>, range: Pair<Double?, Double?>, changeDays: List<LocalDate>) {
    val primary = MaterialTheme.colorScheme.primary
    val band = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val change = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val first = series.first().date
    val span = ChronoUnit.DAYS.between(first, series.last().date).coerceAtLeast(1)
    val (low, high) = range
    val values = series.map { it.value } + listOfNotNull(low, high)
    val min = values.min()
    val max = values.max()
    val pad = (max - min).takeIf { it > 0 }?.times(0.15) ?: 1.0
    val lo = min - pad
    val hi = max + pad
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(190.dp).padding(16.dp)
                .semantics { contentDescription = "Courbe de ${series.size} valeurs, de ${series.first().date} à ${series.last().date}" },
        ) {
            fun x(d: LocalDate) = (ChronoUnit.DAYS.between(first, d).toFloat() / span) * size.width
            fun y(v: Double) = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height
            if (low != null || high != null) {
                val top = y(high ?: hi)
                val bottom = y(low ?: lo)
                drawRect(band, Offset(0f, top), Size(size.width, bottom - top))
            }
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            changeDays.forEach { d ->
                val px = x(d)
                drawLine(change, Offset(px, 0f), Offset(px, size.height), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            series.zipWithNext().forEach { (a, b) -> drawLine(primary, Offset(x(a.date), y(a.value)), Offset(x(b.date), y(b.value)), 5f, StrokeCap.Round) }
            series.forEach { r -> drawCircle(primary, 9f, Offset(x(r.date), y(r.value))) }
        }
    }
}

/** Adding or correcting a value. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LabEditorScreen(
    existing: LabResult?,
    initialMarker: String?,
    today: LocalDate,
    onSave: (LabResult) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val startMarker = existing?.marker ?: initialMarker
    var marker by rememberSaveable { mutableStateOf(startMarker?.takeIf { Labs.marker(it) != null }) }
    var custom by rememberSaveable { mutableStateOf(startMarker?.takeIf { Labs.marker(it) == null }.orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(existing?.unit?.takeIf { Labs.marker(existing.marker) == null }.orEmpty()) }
    var date by remember { mutableStateOf(existing?.date ?: today) }
    var value by rememberSaveable { mutableStateOf(existing?.let { Labs.format(it.value, it.marker) }.orEmpty()) }
    var low by rememberSaveable { mutableStateOf(existing?.low?.let { Labs.format(it, existing.marker) }.orEmpty()) }
    var high by rememberSaveable { mutableStateOf(existing?.high?.let { Labs.format(it, existing.marker) }.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var isCustom by rememberSaveable { mutableStateOf(startMarker != null && Labs.marker(startMarker) == null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
    val key = if (isCustom) custom.trim() else marker.orEmpty()
    val unitText = if (isCustom) unit.trim() else Labs.unit(marker.orEmpty())
    val ok = key.isNotEmpty() && num(value) != null && unitText.isNotEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nouvelle analyse" else "Modifier la valeur") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        onSave(
                            LabResult(
                                id = existing?.id ?: java.util.UUID.randomUUID().toString(), marker = key, date = date, value = num(value)!!, unit = unitText,
                                low = num(low), high = num(high), note = note.trim(),
                            ),
                        )
                    },
                    enabled = ok, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Quelle analyse ?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Labs.MARKERS.forEach { m -> FilterChip(selected = !isCustom && marker == m.id, onClick = { isCustom = false; marker = m.id }, label = { Text("${m.emoji} ${m.name}") }) }
                FilterChip(selected = isCustom, onClick = { isCustom = true; marker = null }, label = { Text("Autre…") })
            }
            if (isCustom) {
                OutlinedTextField(custom, { custom = it.take(40) }, label = { Text("Nom de l'analyse") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it.take(16) }, label = { Text("Unité (ex. mg/L)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            DateField("Date de la prise de sang", date, { date = it }, maxDate = today)
            OutlinedTextField(
                value, { value = it.take(12) }, label = { Text("Valeur" + if (unitText.isNotEmpty()) " ($unitText)" else "") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            com.medipote.app.ui.components.MoreOptions(
                if (low.isNotBlank() || high.isNotBlank() || note.isNotBlank()) "repères et note" else "repères de mon labo, note",
                initiallyOpen = low.isNotBlank() || high.isNotBlank() || note.isNotBlank(),
                label = "Repères de mon laboratoire, note",
            ) {
                Text("Si ton compte rendu indique ses propres repères, note-les : ils passent avant les repères habituels.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(low, { low = it.take(10) }, label = { Text("Minimum") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(high, { high = it.take(10) }, label = { Text("Maximum") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("Note (ex. à jeun, nouveau laboratoire)") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette valeur ?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}
