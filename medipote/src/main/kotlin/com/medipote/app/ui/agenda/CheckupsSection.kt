package com.medipote.app.ui.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.data.RegularCheckup
import com.medipote.app.domain.Checkups
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.NumberStepper
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate

/**
 * « Suivi régulier »: what comes back from time to time (dentist, blood test…), with the day it is due. The person sets the
 * rhythm; one tap says it was done. It lives in the Agenda, next to the appointments.
 */
@Composable
fun CheckupsSection(
    checkups: List<RegularCheckup>,
    today: LocalDate,
    onDone: (String) -> Unit,
    onSave: (RegularCheckup) -> Unit,
    onAdd: (title: String, months: Int, lastDone: LocalDate?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<RegularCheckup?>(null) }
    var adding by remember { mutableStateOf(false) }
    SectionTitle("Suivi régulier")
    if (checkups.isEmpty()) {
        Text(
            "Dentiste, prise de sang, contrôle… : note ce qui revient de temps en temps, l'appli te prévient quand c'est le moment.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Checkups.sorted(checkups, today).forEach { c ->
        val state = Checkups.state(c, today)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    Checkups.label(c, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state) {
                        Checkups.State.LATE -> AppTheme.status.warning
                        Checkups.State.SOON -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onDone(c.id) }) { Text("✓  Fait aujourd'hui") }
                    TextButton(onClick = { editing = c }) { Text("Modifier") }
                }
            }
        }
    }
    editing?.let { c ->
        CheckupForm(
            c, today, onCancel = { editing = null },
            onSave = { title, months, last, remind -> onSave(c.copy(title = title.trim(), everyMonths = months, lastDone = last, remind = remind)); editing = null },
            onDelete = { onDelete(c.id); editing = null },
        )
    }
    if (adding) {
        CheckupForm(null, today, onCancel = { adding = false }, onSave = { title, months, last, _ -> onAdd(title, months, last); adding = false }, onDelete = null)
    } else if (editing == null) {
        TextButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("➕  Ajouter un suivi régulier") }
    }

}

/** The form of a check-up, right in the page (a text field does not belong in a dialog). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckupForm(
    existing: RegularCheckup?,
    today: LocalDate,
    onCancel: () -> Unit,
    onSave: (title: String, months: Int, lastDone: LocalDate?, remind: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var months by remember { mutableStateOf(existing?.everyMonths ?: 12) }
    var last by remember { mutableStateOf(existing?.lastDone) }
    var remind by remember { mutableStateOf(existing?.remind ?: true) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (existing == null) "Un suivi régulier" else "Modifier", style = MaterialTheme.typography.titleMedium)
            if (existing == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkups.SUGGESTIONS.forEach { s ->
                        FilterChip(selected = title == s.title, onClick = { title = s.title; months = s.everyMonths }, label = { Text("${s.emoji} ${s.title}") })
                    }
                }
            }
            OutlinedTextField(title, { title = it.take(60) }, label = { Text("Quoi ?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumberStepper("Tous les", months, { months = it }, 1..120, display = { if (it % 12 == 0) "${it / 12} an${if (it > 12) "s" else ""}" else "$it mois" })
            DateField("Dernière fois", last, { last = it }, maxDate = today)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Me prévenir le jour venu", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = remind, onCheckedChange = { remind = it })
            }
            Text("Ces rythmes sont des idées : ton médecin sait ce qui te convient.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Button(onClick = { onSave(title, months, last, remind) }, enabled = title.isNotBlank()) { Text("Enregistrer") }
                TextButton(onClick = onCancel) { Text("Annuler") }
                onDelete?.let { TextButton(onClick = it) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}
