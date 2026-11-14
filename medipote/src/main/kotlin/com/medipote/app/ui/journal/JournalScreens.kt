package com.medipote.app.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.DayEntry
import com.medipote.app.data.Flow
import com.medipote.app.data.Intercourse
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.fullDay
import java.time.LocalDate

val PHYSICAL_SYMPTOMS = listOf(
    "Fatigue", "Maux de tête", "Fièvre", "Toux", "Nez qui coule", "Mal de gorge", "Nausées", "Maux de ventre",
    "Douleurs musculaires", "Douleurs articulaires", "Vertiges", "Troubles du sommeil", "Perte d'appétit",
)
val MENTAL_SYMPTOMS = listOf(
    "Calme", "Motivation", "Stress", "Anxiété", "Tristesse", "Irritabilité", "Fatigue mentale",
    "Difficultés de concentration", "Sautes d'humeur", "Solitude",
)
val ILLNESSES = listOf("Rhume", "Grippe", "Covid-19", "Angine", "Gastro-entérite", "Migraine", "Allergie", "Infection urinaire")
val CYCLE_SYMPTOMS = listOf(
    "Crampes", "Ballonnements", "Seins sensibles", "Maux de dos", "Maux de tête", "Acné", "Fringales",
    "Pertes blanches", "Libido élevée", "Libido basse", "Sautes d'humeur", "Irritabilité", "Tristesse", "Anxiété",
)

private val FACES = listOf("😣", "🙁", "😐", "🙂", "😄")
private val FACE_LABELS = listOf("Très mal", "Pas bien", "Moyen", "Bien", "Très bien")

fun faceFor(score: Int?): String = score?.let { FACES[(it - 1).coerceIn(0, 4)] } ?: "–"

// ─────────────────────────── List ───────────────────────────

@Composable
fun JournalScreen(
    journal: List<DayEntry>,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    onOpenMyDay: (LocalDate) -> Unit = onOpenDay,
    onOpenYear: () -> Unit = {},
) {
    val entries = journal.filter { it.hasJournal || it.hasCycleData }.sortedByDescending { it.date }
    val todayEntry = journal.firstOrNull { it.date == today }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comment te sens-tu aujourd'hui ?", style = MaterialTheme.typography.titleMedium)
                    if (todayEntry?.hasJournal == true) {
                        Text(
                            "Physique ${faceFor(todayEntry.physical)} · Moral ${faceFor(todayEntry.mental)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        Text(
                            "Note ton état physique et psychologique, tes symptômes ou une maladie. +5 pts par jour.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { onOpenDay(today) }) {
                        Icon(Icons.Rounded.EditNote, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (todayEntry?.hasJournal == true) "Modifier" else "Remplir mon journal")
                    }
                }
            }
        }
        item { MoodHeatmap(journal.associateBy { it.date }, today, onOpenMyDay) }
        if (entries.isNotEmpty()) item { SectionTitle("Historique", Modifier.padding(top = 12.dp)) }
        items(entries, key = { it.date.toString() }) { EntryCard(it) { onOpenMyDay(it.date) } }
        item {
            Disclaimer("Ce journal t'aide à suivre ton état et à en parler avec ton médecin. En cas de symptôme inquiétant, consulte un professionnel ou appelle le 15.")
        }
    }
}

/** 14 days × 2 rows; one hue from light (bad) to dark (good). Tap a day to open it. */
@Composable
private fun MoodHeatmap(entries: Map<LocalDate, DayEntry>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    fun cell(score: Int?) = score?.let { base.copy(alpha = 0.15f + 0.85f * (it - 1) / 4f) } ?: empty
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mes 14 derniers jours", style = MaterialTheme.typography.titleMedium)
            listOf("Physique" to { e: DayEntry? -> e?.physical }, "Moral" to { e: DayEntry? -> e?.mental }).forEach { (label, pick) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                    days.forEach { d ->
                        val score = pick(entries[d])
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(1.5.dp).clip(RoundedCornerShape(4.dp)).background(cell(score))
                                .semantics { contentDescription = "$label le ${d.dayOfMonth} : ${score?.let { FACE_LABELS[it - 1] } ?: "non renseigné"}" }
                                .let { if (!d.isAfter(today)) it.clickable { onOpenDay(d) } else it },
                        )
                    }
                }
            }
            Row {
                Spacer(Modifier.width(64.dp))
                days.forEachIndexed { i, d ->
                    Text(
                        if (i % 2 == 1) "${d.dayOfMonth}" else "",
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Très mal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                (1..5).forEach { Box(Modifier.padding(1.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(cell(it))) }
                Spacer(Modifier.width(6.dp))
                Text("Très bien", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One past day: date and faces on one line; symptoms and notes when touched. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(e: DayEntry, onOpen: () -> Unit) {
    var open by rememberSaveable(e.date.toString()) { mutableStateOf(false) }
    val tags = (e.physicalSymptoms + e.mentalSymptoms + e.cycleSymptoms).distinct()
    val details = tags.size + listOf(e.illness, e.notes, e.reflection).count { it.isNotBlank() } + listOfNotNull(e.flow, e.intercourse).size
    Card(onClick = { open = !open }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.date.format(fullDay).capitalized(), style = MaterialTheme.typography.titleSmall)
                    if (!open && details > 0) {
                        Text(
                            listOfNotNull(
                                e.illness.takeIf { it.isNotBlank() },
                                e.flow?.let { "flux ${it.label.lowercase()}" },
                                tags.size.takeIf { it > 0 }?.let { "$it symptôme${if (it > 1) "s" else ""}" },
                                "notes".takeIf { e.notes.isNotBlank() || e.reflection.isNotBlank() },
                            ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (e.physical != null) Text(faceFor(e.physical), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "Physique : ${FACE_LABELS[e.physical - 1]}" })
                Spacer(Modifier.width(6.dp))
                if (e.mental != null) Text(faceFor(e.mental), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "Moral : ${FACE_LABELS[e.mental - 1]}" })
            }
            androidx.compose.animation.AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        listOfNotNull(e.physical?.let { "Physique ${faceFor(it)} ${FACE_LABELS[it - 1].lowercase()}" }, e.mental?.let { "Moral ${faceFor(it)} ${FACE_LABELS[it - 1].lowercase()}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (e.illness.isNotBlank()) {
                        Text("Maladie : ${e.illness}", style = MaterialTheme.typography.bodyMedium, color = com.medipote.app.ui.theme.AppTheme.status.critical)
                    }
                    if (tags.isNotEmpty() || e.flow != null || e.intercourse != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            e.flow?.let { Tag("Flux ${it.label.lowercase()}", com.medipote.app.ui.theme.AppTheme.status.period.copy(alpha = 0.18f)) }
                            e.intercourse?.let { Tag("♥ ${it.label}", com.medipote.app.ui.theme.AppTheme.status.period.copy(alpha = 0.18f)) }
                            tags.forEach { Tag(it, MaterialTheme.colorScheme.secondaryContainer) }
                        }
                    }
                    if (e.reflection.isNotBlank()) {
                        Text("💭 ${e.reflection}", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    }
                    if (e.notes.isNotBlank()) Text(e.notes, style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Voir toute la journée ›") }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color, shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

// ─────────────────────────── Day editor ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEntryScreen(
    entry: DayEntry,
    showCycle: Boolean,
    onSave: (DayEntry) -> Unit,
    onBack: () -> Unit,
) {
    var physical by remember { mutableStateOf(entry.physical) }
    var mental by remember { mutableStateOf(entry.mental) }
    var physicalSymptoms by remember { mutableStateOf(entry.physicalSymptoms) }
    var mentalSymptoms by remember { mutableStateOf(entry.mentalSymptoms) }
    var illness by remember { mutableStateOf(entry.illness) }
    var notes by remember { mutableStateOf(entry.notes) }
    var reflection by remember { mutableStateOf(entry.reflection) }
    var flow by remember { mutableStateOf(entry.flow) }
    var cycleSymptoms by remember { mutableStateOf(entry.cycleSymptoms) }
    var intercourse by remember { mutableStateOf(entry.intercourse) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mon journal")
                        Text(entry.date.format(fullDay).capitalized(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        onSave(
                            entry.copy(
                                physical = physical, mental = mental, physicalSymptoms = physicalSymptoms,
                                mentalSymptoms = mentalSymptoms, illness = illness.trim(), notes = notes.trim(), reflection = reflection.trim(),
                                flow = flow, cycleSymptoms = cycleSymptoms, intercourse = intercourse,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("État physique") {
                FaceSelector(physical) { physical = it }
                Fold("Symptômes", physicalSymptoms.size) {
                    TagPicker(PHYSICAL_SYMPTOMS, physicalSymptoms, { physicalSymptoms = it }, "Autre symptôme")
                }
            }
            SectionCard("État psychologique") {
                FaceSelector(mental) { mental = it }
                Fold("Ressentis", mentalSymptoms.size) {
                    TagPicker(MENTAL_SYMPTOMS, mentalSymptoms, { mentalSymptoms = it }, "Autre ressenti")
                }
            }
            SectionCard("Notes") {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, placeholder = { Text("Ce que tu veux retenir de ta journée…") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            com.medipote.app.ui.components.MoreOptions(
                listOfNotNull(
                    illness.ifBlank { null }?.let { "maladie : ${it.lowercase()}" } ?: "maladie du jour",
                    if (!showCycle) null else flow?.let { "flux ${it.label.lowercase()}" } ?: "cycle",
                ).joinToString(" · "),
                initiallyOpen = entry.illness.isNotBlank() || entry.hasCycleData,
                label = if (showCycle) "Maladie du jour, cycle" else "Maladie du jour",
            ) {
                SectionCard("Maladie du jour") {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ILLNESSES.forEach { name ->
                            FilterChip(selected = illness.equals(name, true), onClick = { illness = if (illness.equals(name, true)) "" else name }, label = { Text(name) })
                        }
                    }
                    OutlinedTextField(
                        value = illness, onValueChange = { illness = it.take(60) },
                        label = { Text("Maladie (facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                if (showCycle) {
                    SectionCard("Cycle") {
                        Text("Flux", style = MaterialTheme.typography.labelLarge)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = flow == null, onClick = { flow = null }, label = { Text("Aucun") })
                            Flow.entries.forEach { f -> FilterChip(selected = flow == f, onClick = { flow = f }, label = { Text(f.label) }) }
                        }
                        Text("Symptômes liés au cycle", style = MaterialTheme.typography.labelLarge)
                        TagPicker(CYCLE_SYMPTOMS, cycleSymptoms, { cycleSymptoms = it }, "Autre symptôme")
                        Text("Rapport sexuel", style = MaterialTheme.typography.labelLarge)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = intercourse == null, onClick = { intercourse = null }, label = { Text("Aucun") })
                            Intercourse.entries.forEach { i -> FilterChip(selected = intercourse == i, onClick = { intercourse = i }, label = { Text(i.label) }) }
                        }
                    }
                }
            }
        }
    }
}

/** Details inside a card, folded until needed (open when something is already chosen). */
@Composable
private fun Fold(label: String, count: Int, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(count > 0) }
    androidx.compose.material3.TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
        Text(if (count > 0) "$label ($count)" else label)
        Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
    }
    if (open) content()
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Five faces from "very bad" to "very good"; tapping the selected one clears it. */
@Composable
fun FaceSelector(value: Int?, onChange: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        FACES.forEachIndexed { i, face ->
            val score = i + 1
            val selected = value == score
            Surface(
                onClick = { onChange(if (selected) null else score) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.weight(1f).padding(horizontal = 3.dp).semantics { contentDescription = FACE_LABELS[i] },
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(face, style = MaterialTheme.typography.headlineSmall)
                    Text(FACE_LABELS[i], style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(suggestions: List<String>, selected: List<String>, onChange: (List<String>) -> Unit, placeholder: String) {
    var custom by remember { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (suggestions + selected).distinct().forEach { tag ->
            val on = tag in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - tag else selected + tag) },
                label = { Text(tag) },
                leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
            )
        }
    }
    fun add() {
        val t = custom.trim()
        if (t.isNotEmpty() && selected.none { it.equals(t, true) }) onChange(selected + t.replaceFirstChar { it.uppercase() })
        custom = ""
    }
    OutlinedTextField(
        value = custom, onValueChange = { custom = it }, placeholder = { Text(placeholder) }, singleLine = true,
        trailingIcon = { IconButton(onClick = ::add, enabled = custom.isNotBlank()) { Icon(Icons.Rounded.Add, "Ajouter") } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        modifier = Modifier.fillMaxWidth(),
    )
}
