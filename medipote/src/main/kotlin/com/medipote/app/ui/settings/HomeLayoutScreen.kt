package com.medipote.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import com.medipote.app.data.HomeSection
import com.medipote.app.data.Look
import com.medipote.app.data.Settings

/** "Mon accueil": which blocks the home screen shows, and in what order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLayoutScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    val look = settings.look
    fun updateLook(f: (Look) -> Look) = onChange { it.copy(look = f(it.look)) }
    // Challenges and garden have their own place in the companion space.
    val order = look.fullOrder.filter { it != HomeSection.CHALLENGES && it != HomeSection.GARDEN }
    val shown = order.count { it !in look.hiddenSections }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mon accueil") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Two ways in one tap: the essentials, or everything. The detailed choices stay below.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = look.isSimple, onClick = { updateLook { it.simple() } },
                    label = { Text("🌿  Accueil simple") },
                )
                androidx.compose.material3.FilterChip(
                    selected = look.isComplete, onClick = { updateLook { it.complete() } },
                    label = { Text("🧰  Accueil complet") },
                )
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    com.medipote.app.ui.treatments.SwitchRow(
                        "🕐  Accueil du moment", look.moment,
                        supporting = "Le matin, la journée et le soir ne montrent pas la même chose. Le reste attend sous « Voir plus ».",
                    ) { on -> updateLook { it.copy(moment = on) } }
                }
            }
            Text(
                "$shown bloc${if (shown > 1) "s" else ""} affiché${if (shown > 1) "s" else ""}. Un bloc masqué reste dans son onglet : rien n'est perdu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    order.forEachIndexed { i, section ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(section.emoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(section.label, style = MaterialTheme.typography.bodyLarge)
                                if (section.hint.isNotBlank()) {
                                    Text(section.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { updateLook { it.copy(homeOrder = order.move(i, i - 1)) } }, enabled = i > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Monter ${section.label}")
                            }
                            IconButton(onClick = { updateLook { it.copy(homeOrder = order.move(i, i + 1)) } }, enabled = i < order.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Descendre ${section.label}")
                            }
                            Switch(
                                checked = section !in look.hiddenSections,
                                onCheckedChange = { on -> updateLook { it.copy(hiddenSections = if (on) it.hiddenSections - section else it.hiddenSections + section) } },
                                modifier = Modifier.semantics { contentDescription = "Afficher ${section.label}" },
                            )
                        }
                    }
                }
            }
            Text(
                "Les alertes importantes (stock, ordonnance, prise oubliée, notifications bloquées) s'affichent toujours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { updateLook { it.copy(homeOrder = HomeSection.entries, hiddenSections = emptySet()) } }) {
                Text("Revenir à l'accueil d'origine")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
