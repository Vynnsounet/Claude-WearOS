package com.medipote.app.ui.wellbeing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.domain.DayEffect
import com.medipote.app.ui.stats.StatCard

/** « Ce qui change tes jours »: what goes with worse or better days (mood, form, energy, symptoms), or why there is nothing yet. */
@Composable
fun DayEffectsCard(effects: List<DayEffect>, anyNoted: Boolean) {
    StatCard("🔍  Ce qui change tes jours") {
        if (effects.isEmpty()) {
            Text(
                if (!anyNoted) "Note ton moral, ta forme ou tes symptômes au fil des jours : avec un peu de recul, je te montre ce qui va avec tes bons et tes moins bons jours."
                else "Rien de net pour l'instant : aucun facteur ne se détache de tes notes des trois derniers mois. Plus tu notes (nuits, soirées, eau), plus la comparaison devient fiable.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            effects.forEach { e ->
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text("${e.emoji}  ${e.whenText.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleSmall)
                    Text(e.sentence, style = MaterialTheme.typography.bodyMedium, color = if (e.worse) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(
            "Ce sont des choses qui vont ensemble dans tes notes, pas des causes. Si un symptôme te pèse ou revient, parles-en à ton médecin.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
