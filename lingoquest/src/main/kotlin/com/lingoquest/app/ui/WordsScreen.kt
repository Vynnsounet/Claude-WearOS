package com.lingoquest.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingoquest.engine.Cefr

/** All the words of the game: unlocked ones with their translation, locked ones with the level that opens them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsScreen(state: UiState, rows: List<WordRow>, onBack: () -> Unit) {
    val byCefr = rows.groupBy { it.entry.level }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes mots · ${state.unlockedWords}/${state.totalWords}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            for (cefr in Cefr.entries) {
                val words = byCefr[cefr].orEmpty()
                if (words.isEmpty()) continue
                val open = words.count { it.unlockLevel <= state.level }
                item(key = "header-${cefr.name}") {
                    Text(
                        "${cefr.name} · $open/${words.size}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(words, key = { it.entry.id }) { row -> WordItem(row, unlocked = row.unlockLevel <= state.level) }
            }
        }
    }
}

@Composable
private fun WordItem(row: WordRow, unlocked: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (unlocked) {
                Column(Modifier.weight(1f)) {
                    Text(row.foreign, style = MaterialTheme.typography.titleMedium)
                    Text(row.french, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(row.entry.pos.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            } else {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                Text("• • • •", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                Text("niveau ${row.unlockLevel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
