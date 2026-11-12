package com.medipote.app.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Appointment
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.TimePickerModal
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.hourMinute
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

val SPECIALTIES = listOf(
    "Médecin généraliste", "Dentiste", "Gynécologue", "Sage-femme", "Ophtalmologue", "Dermatologue", "Cardiologue",
    "Kinésithérapeute", "Psychologue", "Psychiatre", "Prise de sang", "Radiologie", "Vaccination",
)

private val REMINDER_CHOICES = listOf(7 * 24 * 60 to "1 semaine avant", 24 * 60 to "La veille", 2 * 60 to "2 h avant", 60 to "1 h avant", 30 to "30 min avant")

private val dayLong = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)

fun reminderLabel(minutes: Int): String = REMINDER_CHOICES.firstOrNull { it.first == minutes }?.second ?: "$minutes min avant"

/** Embedded content of the "Agenda" tab: month view, then upcoming and past appointments. */
@Composable
fun AgendaScreen(appointments: List<Appointment>, now: LocalDateTime, onNew: (LocalDate?) -> Unit, onOpen: (Appointment) -> Unit) {
    val today = now.toLocalDate()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = selected?.let(LocalDate::parse)
    val byDay = remember(appointments) { appointments.groupBy { it.dateTime.toLocalDate() } }
    val upcoming = appointments.filter { !it.dateTime.isBefore(now.minusHours(1)) }.sortedBy { it.dateTime }
    val past = appointments.filter { it.dateTime.isBefore(now.minusHours(1)) }.sortedByDescending { it.dateTime }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onNew(selectedDate) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Rendez-vous") })
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthCard(YearMonth.parse(month), today, selectedDate, byDay.keys, { month = it.toString() }) {
                selected = if (selected == it.toString()) null else it.toString()
            }
            if (selectedDate != null) {
                SectionTitle(selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)).capitalized())
                val list = byDay[selectedDate].orEmpty().sortedBy { it.dateTime }
                if (list.isEmpty()) {
                    Text("Aucun rendez-vous ce jour-là.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                list.forEach { AppointmentCard(it, now) { onOpen(it) } }
            }
            SectionTitle("À venir")
            if (upcoming.isEmpty()) {
                Text(
                    "Aucun rendez-vous prévu. Ajoute-les ici pour recevoir un rappel.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            upcoming.forEach { AppointmentCard(it, now) { onOpen(it) } }
            if (past.isNotEmpty()) {
                SectionTitle("Passés")
                past.take(10).forEach { AppointmentCard(it, now, past = true) { onOpen(it) } }
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun MonthCard(
    ym: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    marked: Set<LocalDate>,
    onMonth: (YearMonth) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(ym.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mois précédent") }
                Text(
                    ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)).capitalized(),
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMonth(ym.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Mois suivant") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val lead = ym.atDay(1).dayOfWeek.value - 1
            val rows = (lead + ym.lengthOfMonth() + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val n = r * 7 + c - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (n in 1..ym.lengthOfMonth()) {
                                val date = ym.atDay(n)
                                val isSel = date == selected
                                var m = Modifier.fillMaxSize().clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { onDay(date) }
                                if (date == today && !isSel) m = m.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                Box(m, contentAlignment = Alignment.Center) {
                                    Text(
                                        "$n", style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (date in marked) {
                                        Box(
                                            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(6.dp).clip(CircleShape)
                                                .background(if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(a: Appointment, now: LocalDateTime, past: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (past) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.width(58.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(a.dateTime.format(DateTimeFormatter.ofPattern("MMM", Locale.FRANCE)).uppercase().trimEnd('.'), style = MaterialTheme.typography.labelSmall)
                    Text("${a.dateTime.dayOfMonth}", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${a.dateTime.format(dayLong).capitalized()} à ${a.dateTime.format(hourMinute)}" + relative(a.dateTime, now),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val details = listOf(a.doctor, a.location).filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun relative(t: LocalDateTime, now: LocalDateTime): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), t.toLocalDate())
    return when {
        days == 0L -> " · aujourd'hui"
        days == 1L -> " · demain"
        days in 2..14 -> " · dans $days jours"
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppointmentEditorScreen(
    existing: Appointment?,
    initialDate: LocalDate,
    onSave: (Appointment) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var doctor by remember { mutableStateOf(existing?.doctor.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var date by remember { mutableStateOf(existing?.dateTime?.toLocalDate() ?: initialDate) }
    var minute by remember { mutableStateOf(existing?.dateTime?.let { it.hour * 60 + it.minute } ?: (9 * 60)) }
    var reminders by remember { mutableStateOf(existing?.reminderMinutes?.toSet() ?: setOf(24 * 60, 60)) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nouveau rendez-vous" else "Rendez-vous") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        onSave(
                            Appointment(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                title = title.trim(), doctor = doctor.trim(), location = location.trim(),
                                dateTime = date.atTime(minute / 60, minute % 60), notes = notes.trim(),
                                reminderMinutes = reminders.sortedDescending(),
                            ),
                        )
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Motif")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPECIALTIES.forEach { s -> FilterChip(selected = title == s, onClick = { title = s }, label = { Text(s) }) }
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(60) }, label = { Text("Motif ou spécialité *") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = doctor, onValueChange = { doctor = it.take(60) }, label = { Text("Praticien") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = location, onValueChange = { location = it.take(100) }, label = { Text("Lieu") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.LocationOn, null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("Date et heure")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DateField("Date", date, { date = it }, Modifier.weight(1f))
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Rounded.Schedule, null)
                    Spacer(Modifier.width(6.dp))
                    Text("%02d:%02d".format(minute / 60, minute % 60))
                }
            }
            SectionTitle("Rappels")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Choisis quand être prévenu·e", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_CHOICES.forEach { (m, label) ->
                    FilterChip(selected = m in reminders, onClick = { reminders = if (m in reminders) reminders - m else reminders + m }, label = { Text(label) })
                }
            }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes (questions à poser, documents à apporter…)") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
    if (pickTime) {
        TimePickerModal(minute, onDismiss = { pickTime = false }) { minute = it; pickTime = false }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce rendez-vous ?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}
