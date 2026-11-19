package com.medipote.app.ui.ideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Idea
import com.medipote.app.domain.Ideas
import java.time.format.DateTimeFormatter

private val STAMP = DateTimeFormatter.ofPattern("d/MM/yyyy")

/** « Une idée, un souci ? »: a quiet place to write what could be better. It stays on the phone unless shared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeasScreen(
    ideas: List<Idea>,
    screen: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Une idée, un souci ?") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    if (ideas.isNotEmpty()) IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "Partager mes idées") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Ce qui t'a gêné·e, ce qui manque, ce qui te ferait plaisir. Tout reste sur ton téléphone : rien n'est envoyé, " +
                    "tu choisis si tu le partages.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                text, { text = it.take(2000) }, label = { Text("Ton idée ou ton souci") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            if (screen.isNotBlank()) {
                Text("Noté depuis : $screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onAdd(text); text = "" }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Ajouter à ma liste") }
            if (ideas.isEmpty()) {
                Text("Rien pour l'instant. C'est très bien aussi.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Ma liste (${ideas.size})", style = MaterialTheme.typography.titleMedium)
                ideas.forEach { idea ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(idea.text, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    idea.at.format(STAMP) + if (idea.screen.isNotBlank()) " · ${idea.screen}" else "",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRemove(idea.id) }) { Icon(Icons.Outlined.Delete, "Supprimer cette idée") }
                        }
                    }
                }
            }
        }
    }
}
