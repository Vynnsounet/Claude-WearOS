package com.medipote.app.ui.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Appointment
import com.medipote.app.ui.components.SectionTitle
import com.medipote.app.ui.components.dayMonthYear

/**
 * « Après le rendez-vous »: what was said and decided, the treatments that changed, the next appointment. It closes the loop
 * opened by « Préparer mon rendez-vous ». Reached from the appointment itself, and from the home screen the day after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterAppointmentScreen(
    appointment: Appointment,
    onSaveNotes: (String) -> Unit,
    onAttended: () -> Unit,
    onTreatments: () -> Unit,
    onLabs: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    var notes by rememberSaveable(appointment.id) { mutableStateOf(appointment.afterNotes) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Après le rendez-vous"); Text("${appointment.title} · ${appointment.dateTime.toLocalDate().format(dayMonthYear)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Quelques mots maintenant, c'est un souvenir de moins à retenir.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("Ce qui a été dit ou décidé")
            OutlinedTextField(
                value = notes, onValueChange = { notes = it.take(2000) }, label = { Text("Diagnostic, conseils, examens à faire…") },
                minLines = 4, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSaveNotes(notes) }, enabled = notes.trim() != appointment.afterNotes.trim(), modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }

            SectionTitle("Et ensuite ?")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onTreatments, modifier = Modifier.fillMaxWidth()) { Text("💊  Un traitement a changé : mes traitements") }
                    OutlinedButton(onClick = onLabs, modifier = Modifier.fillMaxWidth()) { Text("🧪  Des résultats d'analyses : mes analyses") }
                    OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("📅  Programmer le prochain rendez-vous") }
                }
            }
            if (!appointment.attended) {
                OutlinedButton(onClick = onAttended, modifier = Modifier.fillMaxWidth()) { Text("✓  J'y suis allé·e") }
            } else {
                Text("✓ Noté comme fait", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
