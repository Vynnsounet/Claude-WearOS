package com.radaroccaz.app.ui.settings

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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.radaroccaz.app.data.Settings
import com.radaroccaz.app.data.ThemeMode
import com.radaroccaz.app.ui.components.SectionCard
import com.radaroccaz.app.ui.components.SourceBadge

data class SourceItem(val id: String, val name: String, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    sources: List<SourceItem>,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("Sites consultés", icon = Icons.Rounded.Public) {
                sources.forEach { s ->
                    val on = s.id in settings.enabledSources
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SourceBadge(s.id)
                            Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = on,
                            enabled = !settings.demoMode,
                            onCheckedChange = { checked ->
                                onChange { st -> st.copy(enabledSources = if (checked) st.enabledSources + s.id else st.enabledSources - s.id) }
                            },
                        )
                    }
                }
            }

            SectionCard("Mode démo", icon = Icons.Rounded.Science) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Affiche des annonces fictives générées sur le téléphone, pour essayer l'application sans connexion ou quand les sites bloquent l'accès.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = settings.demoMode, onCheckedChange = { v -> onChange { it.copy(demoMode = v) } })
                }
            }

            SectionCard("Apparence", icon = Icons.Rounded.Palette) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { onChange { it.copy(themeMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            label = { Text(mode.label) },
                        )
                    }
                }
            }

            SectionCard("Bon à savoir", icon = Icons.Rounded.Info) {
                Text(
                    "Radar Occaz lit les pages de résultats publiques des sites, comme un navigateur, à votre demande ou pour vos alertes. " +
                        "Certains sites protègent leurs pages contre les accès automatisés : quand c'est le cas, l'application l'indique " +
                        "et propose d'ouvrir la recherche directement sur le site. Les sites peuvent aussi changer leur présentation ; " +
                        "une source peut alors cesser de fonctionner jusqu'à une mise à jour.\n\n" +
                        "La note sur 100 compare chaque annonce aux autres résultats : prix (et écart avec le prix estimé du marché pour " +
                        "son année et son kilométrage), kilométrage, année et options trouvées dans le texte de l'annonce. " +
                        "Vérifiez toujours l'annonce et le véhicule avant d'acheter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
