package com.medipote.app.ui.treatments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Treatment
import com.medipote.app.domain.Precaution
import com.medipote.app.domain.Precautions

/** « À savoir pour bien la prendre »: the habits that matter for one medication, and the gaps to keep with the others. */
@Composable
fun PrecautionsCard(items: List<Precaution>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("À savoir pour bien la prendre", style = MaterialTheme.typography.titleMedium)
            items.forEach { p ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(p.emoji, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(p.text, style = MaterialTheme.typography.bodyMedium, color = if (p.warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(Precautions.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Two treatments taken too close to each other, at the top of the list; touching one opens it to move its time. */
@Composable
fun SpacingWarnings(treatments: List<Treatment>, onEdit: (Treatment) -> Unit) {
    Precautions.problems(treatments).forEach { (t, p) ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth().clickable { onEdit(t) },
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${p.emoji}  ${t.displayName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(p.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
