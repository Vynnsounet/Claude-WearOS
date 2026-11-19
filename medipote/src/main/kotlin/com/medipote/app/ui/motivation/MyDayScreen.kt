package com.medipote.app.ui.motivation

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.DoseState
import com.medipote.app.domain.Schedule
import com.medipote.app.domain.Sleep
import com.medipote.app.ui.components.Pill
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.fullDay
import com.medipote.app.ui.components.hourMinute
import com.medipote.app.ui.home.dayTitle
import com.medipote.app.ui.sleep.NightTimeline
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Everything noted on one day, in one place: a page of the health diary. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyDayScreen(
    state: AppState,
    date: LocalDate,
    now: LocalDateTime,
    onDate: (LocalDate) -> Unit,
    onEditJournal: () -> Unit,
    onEditSleep: () -> Unit,
    onOpenDoses: () -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    val today = now.toLocalDate()
    val entry = state.entryFor(date)
    val doses = Schedule.itemsOn(state.treatments, state.doseLogs.associateBy { it.key }, date, now)
    val night = state.sleep.firstOrNull { it.date == date }
    val intakes = state.prnIntakes.filter { it.takenAt.toLocalDate() == date }.sortedBy { it.takenAt }
    val measures = state.measurements.filter { it.at.toLocalDate() == date }.sortedBy { it.at }
    val appointments = state.appointments.filter { it.dateTime.toLocalDate() == date }
    val status = AppTheme.status

    Scaffold(
        contentWindowInsets = if (embedded) androidx.compose.foundation.layout.WindowInsets(0) else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!embedded) TopAppBar(
                title = { Text("Ma journée") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDate(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Jour précédent") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayTitle(date, today), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    if (ChronoUnit.DAYS.between(date, today) in 0..2) {
                        Text(date.format(fullDay).capitalized(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { onDate(date.plusDays(1)) }, enabled = date.isBefore(today)) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Jour suivant") }
            }

            Section("😊 Humeur et journal", "Modifier", onEditJournal) {
                if (!entry.hasJournal) {
                    Muted("Rien de noté ce jour-là.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        entry.mental?.let { Text("Moral ${FACES[it - 1]} ${MOOD_LABELS[it - 1].lowercase()}", style = MaterialTheme.typography.bodyLarge) }
                        entry.physical?.let { Text("Forme ${FACES[it - 1]}", style = MaterialTheme.typography.bodyLarge) }
                    }
                    if (entry.reflection.isNotBlank()) {
                        Text("💭 ${entry.reflection}", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic))
                    }
                    val tags = entry.physicalSymptoms + entry.mentalSymptoms + listOfNotNull(entry.illness.ifBlank { null })
                    if (tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { Pill(it, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                    if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (doses.isNotEmpty()) {
                val taken = doses.count { it.state == DoseState.TAKEN }
                Section("💊 Prises · $taken / ${doses.size}", "Détails", onOpenDoses) {
                    doses.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.occurrence.dose.timeLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
                            Text(item.occurrence.treatment.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                when (item.state) {
                                    DoseState.TAKEN -> "✓ ${item.log!!.takenAt.format(hourMinute)}"
                                    DoseState.MISSED -> "non notée"
                                    DoseState.UPCOMING -> "à venir"
                                    else -> "à prendre"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = when (item.state) {
                                    DoseState.TAKEN -> status.good
                                    DoseState.MISSED -> status.critical
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            if (intakes.isNotEmpty()) {
                Section("🩹 Si besoin") {
                    intakes.forEach { i ->
                        val name = state.treatments.firstOrNull { it.id == i.treatmentId }?.displayName ?: "?"
                        Text("${i.takenAt.format(hourMinute)} · $name" + (i.reason.takeIf { it.isNotBlank() }?.let { " · ${it.lowercase()}" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val symptomNotes = com.medipote.app.domain.Wellbeing.notesOn(state, date)
            if (symptomNotes.isNotEmpty()) {
                Section("🌡️ Symptômes") {
                    symptomNotes.forEach { n ->
                        Text("${com.medipote.app.domain.Wellbeing.emoji(n.symptom)} ${com.medipote.app.domain.Wellbeing.name(n.symptom)} · ${n.intensity}/5", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Section("🌙 Nuit", if (night == null) "Noter" else "Modifier", onEditSleep) {
                com.medipote.app.domain.Naps.on(state, date).forEach { nap ->
                    Text(
                        "😴 Sieste · ${nap.start.format(hourMinute)} · ${Sleep.format(nap.minutes)}" + (nap.feeling?.let { " · ${it.label.lowercase()}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (night == null) Muted("Nuit pas notée.")
                else {
                    Text(
                        if (night.noSleep) "Nuit blanche" else "${Sleep.format(night.asleepMinutes)} de sommeil · ${night.awakenings.size} réveil${if (night.awakenings.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    NightTimeline(night)
                    night.quality?.let { Text("${FACES[it - 1]} ${Sleep.QUALITY_LABELS[it - 1].lowercase()}", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            val water = entry.waterGlasses
            if (water > 0 || entry.hasCycleData || com.medipote.app.domain.PeriodCare.usesOn(state, date) > 0) {
                Section("💧 Eau et cycle") {
                    if (water > 0) Text("$water verre${if (water > 1) "s" else ""} d'eau sur ${state.settings.water.goalGlasses}", style = MaterialTheme.typography.bodyMedium)
                    entry.flow?.let { Text("Règles : flux ${it.label.lowercase()}", style = MaterialTheme.typography.bodyMedium) }
                    if (entry.cycleSymptoms.isNotEmpty()) Text(entry.cycleSymptoms.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                    com.medipote.app.domain.PeriodCare.usesOn(state, date).takeIf { it > 0 }?.let {
                        Text("Protections : $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (measures.isNotEmpty()) {
                Section("🩺 Mesures") {
                    measures.forEach { m -> Text("${m.at.format(hourMinute)} · ${m.type.label} : ${m.display} ${m.type.unit}", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (appointments.isNotEmpty()) {
                Section("📅 Rendez-vous") {
                    appointments.forEach { a -> Text("${a.dateTime.format(hourMinute)} · ${a.title}" + (a.doctor.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Section(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(top = 8.dp))
                if (action != null) TextButton(onClick = onAction) { Text(action) }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        }
    }
}
