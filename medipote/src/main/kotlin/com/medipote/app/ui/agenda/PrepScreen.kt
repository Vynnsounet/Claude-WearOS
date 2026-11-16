package com.medipote.app.ui.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.domain.AppointmentPrep
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.wellbeing.WbPage

/**
 * "Préparer mon rendez-vous": the person's questions first, then what the last weeks looked like,
 * worked out on the phone. It can be copied or shared as text; the full PDF report is one tap away.
 */
@Composable
fun AppointmentPrepScreen(
    prep: AppointmentPrep,
    dateLabel: String,
    onQuestions: (List<String>) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
) {
    var question by rememberSaveable { mutableStateOf("") }
    WbPage("Préparer mon rendez-vous", embedded = false, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(prep.title, style = MaterialTheme.typography.titleLarge)
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mes questions", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ce que tu veux demander ou dire : écris-le maintenant, tu n'auras pas à y penser pendant le rendez-vous.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            prep.questions.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("•  $q", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onQuestions(prep.questions - q) }) { Icon(Icons.Rounded.Close, "Retirer « $q »") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    question, { question = it.take(200) },
                    label = { Text("Une question") }, modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        val q = question.trim()
                        if (q.isNotEmpty()) onQuestions((prep.questions + q).distinct().take(20))
                        question = ""
                    },
                    enabled = question.isNotBlank(),
                ) { Text("Ajouter") }
            }
        }

        if (prep.sections.isEmpty()) {
            Text(
                "Pas encore assez de données pour un résumé : note tes prises, ton sommeil ou tes symptômes quelques jours.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Ce que je peux dire · ${prep.days} derniers jours", style = MaterialTheme.typography.titleMedium)
            prep.sections.forEach { section ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleSmall)
                        section.lines.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }

        Button(onClick = { onShare(prep.asText()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("📤  Partager ce résumé") }
        OutlinedButton(onClick = { onCopy(prep.asText()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("📋  Copier le texte") }
        OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("📄  Le rapport PDF complet") }
        Disclaimer("Ce résumé reprend ce que tu as noté dans l'appli : ce sont des observations, pas un diagnostic. Il n'est envoyé nulle part tant que tu ne le partages pas.")
    }
}
