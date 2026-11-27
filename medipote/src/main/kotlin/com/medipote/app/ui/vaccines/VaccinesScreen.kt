package com.medipote.app.ui.vaccines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.VaccineCard
import com.medipote.app.domain.VaccineStatus
import com.medipote.app.domain.Vaccines
import com.medipote.app.ui.components.DateField
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.treatments.SwitchRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/** « Vaccins »: what the French calendar advises at this age, where the person is with each, and what they had. */
@Composable
fun VaccinesScreen(
    state: AppState,
    today: LocalDate,
    onAdd: (vaccine: String, date: LocalDate, note: String) -> Unit,
    onRemove: (String) -> Unit,
    onProfile: (birthYear: Int?, risk: Boolean) -> Unit,
    onReminders: (Boolean) -> Unit,
) {
    val profile = state.profile
    val cards = remember(state, today) { Vaccines.cards(state, today) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    var birth by rememberSaveable(profile?.birthYear) { mutableStateOf(profile?.birthYear?.toString().orEmpty()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val age = Vaccines.age(state, today)
                Text(if (age != null) "Ton calendrier à ${age} ans" else "Ton calendrier", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Le calendrier dépend de ton âge : donne ton année de naissance pour qu'il reste juste d'une année à l'autre (à un an près).",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = birth, onValueChange = { birth = it.filter(Char::isDigit).take(4) },
                        label = { Text("Année de naissance") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { onProfile(birth.toIntOrNull(), profile?.vaccineRisk == true) },
                        enabled = profile != null && birth.length == 4 && birth.toIntOrNull() != profile.birthYear,
                    ) { Text("Enregistrer") }
                }
                SwitchRow(
                    "Je suis concerné·e par les recommandations « personnes à risque »", profile?.vaccineRisk == true,
                    supporting = "Maladie chronique, grossesse, défenses immunitaires fragiles… : ton médecin te le dit. Ça ajoute la grippe, le Covid-19, le pneumocoque et le zona.",
                ) { onProfile(profile?.birthYear, it) }
                SwitchRow("Me rappeler à l'automne", state.settings.vaccineReminders, supporting = "Un mot doux le 15 octobre pour la grippe et le Covid-19, et sur l'accueil quand un vaccin est à faire.") { onReminders(it) }
            }
        }

        if (profile == null || Vaccines.birthYear(state, today) == null) {
            Text("Donne ton année de naissance ci-dessus pour voir ce qui est conseillé à ton âge.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (cards.isEmpty()) {
            Text("À cet âge, aucun vaccin du calendrier simplifié n'est à suivre ici. Tu peux quand même noter tes vaccins plus bas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        cards.forEach { c ->
            VaccineCardView(c, open = adding == c.id, onOpen = { adding = if (adding == c.id) null else c.id }) { date, note ->
                onAdd(c.id, date, note); adding = null
            }
        }

        val own = state.vaccinations
        if (own.isNotEmpty()) {
            Text("Mes vaccins notés", style = MaterialTheme.typography.titleMedium)
            own.forEach { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Vaccines.name(v.vaccine), style = MaterialTheme.typography.bodyLarge)
                        Text(v.date.format(DAY) + if (v.note.isNotBlank()) " · ${v.note}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(v.id) }) { Icon(Icons.Rounded.Close, "Retirer ${Vaccines.name(v.vaccine)} du ${v.date.format(DAY)}") }
                }
            }
        }

        OtherVaccine { name, date, note -> onAdd(name, date, note) }
        Disclaimer(Vaccines.DISCLAIMER, important = true)
    }
}

@Composable
private fun VaccineCardView(c: VaccineCard, open: Boolean, onOpen: () -> Unit, onSave: (LocalDate, String) -> Unit) {
    val (label, container) = when (c.status) {
        VaccineStatus.DUE -> "À faire" to MaterialTheme.colorScheme.errorContainer
        VaccineStatus.SOON -> "Bientôt" to MaterialTheme.colorScheme.tertiaryContainer
        VaccineStatus.CHECK -> "À vérifier" to MaterialTheme.colorScheme.secondaryContainer
        VaccineStatus.OK -> "À jour" to MaterialTheme.colorScheme.primaryContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${c.emoji}  ${c.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Surface(color = container, shape = MaterialTheme.shapes.small) { Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
            }
            Text(c.headline, style = MaterialTheme.typography.bodyMedium)
            Text(c.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            c.last?.let { Text("Dernière dose notée : ${it.format(DAY)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = onOpen) { Text(if (open) "Fermer" else "Noter ce vaccin") }
            if (open) NoteForm(null, onSave)
        }
    }
}

/** Date and a word, right in the card. */
@Composable
private fun NoteForm(name: String?, onSave: (LocalDate, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var note by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField("Date du vaccin", LocalDate.parse(date), { date = it.toString() }, Modifier.fillMaxWidth(), maxDate = LocalDate.now())
        OutlinedTextField(value = note, onValueChange = { note = it.take(100) }, label = { Text("Un mot (facultatif) : lieu, lot…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSave(LocalDate.parse(date), note) }) { Text("Enregistrer") }
    }
}

@Composable
private fun OtherVaccine(onSave: (String, LocalDate, String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { open = !open }) { Text(if (open) "Fermer" else "➕  Noter un autre vaccin (voyage, hépatite…)") }
        if (open) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Nom du vaccin") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (name.isNotBlank()) NoteForm(name) { d, n -> onSave(name, d, n); name = ""; open = false }
        }
    }
}
