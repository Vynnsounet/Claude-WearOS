package com.medipote.app.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Festival
import com.medipote.app.data.Season
import com.medipote.app.data.SeasonSettings
import com.medipote.app.domain.Seasons
import com.medipote.app.ui.components.DateField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

/** Seasons and festivals: what the companion's world does through the year, all optional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonsScreen(
    settings: SeasonSettings,
    petName: String,
    petBirthday: LocalDate,
    today: LocalDate,
    onChange: ((SeasonSettings) -> SeasonSettings) -> Unit,
    onBack: () -> Unit,
) {
    val season = Seasons.of(today, settings.southern)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saisons et fêtes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Le monde de $petName change avec l'année : feuilles à l'automne, neige l'hiver, fleurs au printemps, et un petit décor les jours de fête. Discret, et jamais les jours où tu notes une humeur « très mal ».",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Toggle("Saisons et fêtes", "En ce moment : ${season.emoji} ${season.label.lowercase()}", settings.enabled) { v -> onChange { it.copy(enabled = v) } }
                    if (settings.enabled) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Toggle("Teinte de saison", "Un très léger voile sur les fonds de l'appli", settings.tint) { v -> onChange { it.copy(tint = v) } }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Toggle("Hémisphère sud", "Les saisons sont inversées", settings.southern) { v -> onChange { it.copy(southern = v) } }
                    }
                }
            }
            if (settings.enabled) {
                Text("Les fêtes que je garde", style = MaterialTheme.typography.titleMedium)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Festival.entries.forEachIndexed { i, f ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            val on = f in settings.festivals
                            val detail = when (f) {
                                Festival.NEW_YEAR -> "31 décembre et 1er janvier"
                                Festival.VALENTINE -> "14 février"
                                Festival.EASTER -> "Dimanche et lundi de Pâques"
                                Festival.HALLOWEEN -> "31 octobre"
                                Festival.CHRISTMAS -> "24 et 25 décembre"
                                Festival.PET_BIRTHDAY -> "Le ${petBirthday.format(dayMonth)}, le jour où vous vous êtes rencontrés"
                                Festival.USER_BIRTHDAY -> settings.birthday?.let { "Le ${it.format(dayMonth)}" } ?: "Ajoute ta date ci-dessous"
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable { onChange { it.copy(festivals = if (on) it.festivals - f else it.festivals + f) } }.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = on, onCheckedChange = { v -> onChange { it.copy(festivals = if (v) it.festivals + f else it.festivals - f) } })
                                Text(f.emoji, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mon anniversaire", style = MaterialTheme.typography.titleMedium)
                    DateField("Ma date de naissance (facultatif)", settings.birthday, { d -> onChange { it.copy(birthday = d) } }, maxDate = today)
                    if (settings.birthday != null) TextButton(onClick = { onChange { it.copy(birthday = null) } }) { Text("Effacer ma date") }
                    Text(
                        "Seuls le jour et le mois servent, à ton compagnon pour te souhaiter ta fête. Rien ne quitte ton téléphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
