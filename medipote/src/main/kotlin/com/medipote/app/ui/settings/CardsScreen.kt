package com.medipote.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.ui.HubCatalog

/** "Mes cartes": which cards each tab shows. A hidden card is not lost: it comes back with its switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(state: AppState, onToggle: (String, Boolean) -> Unit, onReset: () -> Unit, onBack: () -> Unit) {
    val hidden = state.settings.hiddenCards
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes cartes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Choisis ce que chaque onglet te montre. Une carte masquée reste dans l'appli, rien n'est perdu : elle revient quand tu la réactives.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HubCatalog.cards(state).forEach { (hub, cards) ->
                Text(hub, style = MaterialTheme.typography.titleMedium)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        cards.forEachIndexed { i, card ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(card.emoji, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(12.dp))
                                Text(card.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = card.key !in hidden,
                                    onCheckedChange = { on -> onToggle(card.key, !on) },
                                    modifier = Modifier.semantics { contentDescription = "Afficher ${card.label}" },
                                )
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onReset) { Text("Tout réafficher") }
        }
    }
}
