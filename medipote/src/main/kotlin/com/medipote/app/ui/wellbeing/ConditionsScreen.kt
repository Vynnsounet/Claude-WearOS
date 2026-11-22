package com.medipote.app.ui.wellbeing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medipote.app.data.MyCondition
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.Wellbeing
import com.medipote.app.domain.WellbeingLibrary
import com.medipote.app.ui.components.Disclaimer

/**
 * "Ce qui m'accompagne": what the person lives with, the symptoms to follow and their own missions.
 * Everything is optional and theirs; nothing of it shows elsewhere in the app until it is turned on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionsScreen(
    settings: WellbeingSettings,
    onChange: ((WellbeingSettings) -> WellbeingSettings) -> Unit,
    onBack: () -> Unit,
    onboarding: Boolean = false,
) {
    // Adding something is a way of saying yes: the tracking turns itself on.
    fun change(t: (WellbeingSettings) -> WellbeingSettings) = onChange { w ->
        t(w).let { if (!it.enabled && (it.conditions.isNotEmpty() || it.symptoms.isNotEmpty() || it.customMissions.isNotEmpty())) it.copy(enabled = true) else it }
    }
    var customCondition by rememberSaveable { mutableStateOf("") }
    var customSymptom by rememberSaveable { mutableStateOf("") }
    var customMission by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    WbPage(if (onboarding) "Ce qui t'accompagne" else "Ce qui m'accompagne", embedded = false, onBack = onBack) {
        Text(
            "Tout est facultatif et reste sur ton téléphone (sauf si tu actives l'IA facultative et l'autorises à le lire). Ce que tu choisis ici donne des idées et des conseils adaptés à toi : suivi de symptômes, journée douce, petites missions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Suivi des symptômes et journée douce", style = MaterialTheme.typography.titleSmall)
                Text("Désactivé, rien de tout ça ne s'affiche.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
                modifier = Modifier.semantics { contentDescription = "Activer le suivi des symptômes et la journée douce" },
            )
        }

        // ── Conditions ──
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ce que je vis", style = MaterialTheme.typography.titleMedium)
            settings.conditions.forEach { c ->
                val info = c.presetId?.let(WellbeingLibrary::condition)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${info?.emoji ?: "🌿"}  ${c.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (info != null) {
                                TextButton(onClick = { openId = if (openId == info.id) null else info.id }) { Text(if (openId == info.id) "Masquer" else "En savoir plus") }
                            }
                            IconButton(onClick = { change { w -> w.copy(conditions = w.conditions - c) } }) { Icon(Icons.Rounded.Close, "Retirer ${c.name}") }
                        }
                        if (info != null && openId == info.id) {
                            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(info.about, style = MaterialTheme.typography.bodyMedium)
                                info.tips.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                                Text("À surveiller", style = MaterialTheme.typography.labelLarge)
                                info.watch.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
            Text("Choisir dans la liste", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WellbeingLibrary.CONDITIONS.filter { p -> settings.conditions.none { it.presetId == p.id } }.forEach { p ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            change { w ->
                                w.copy(
                                    conditions = w.conditions + MyCondition(p.name, p.id),
                                    symptoms = (w.symptoms + p.symptoms).distinct(),
                                )
                            }
                        },
                        label = { Text("${p.emoji}  ${p.name}") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    customCondition, { customCondition = it.take(60) },
                    label = { Text("Une autre condition") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        val name = customCondition.trim()
                        if (name.isNotEmpty()) change { w -> w.copy(conditions = w.conditions + MyCondition(name)) }
                        customCondition = ""
                    },
                    enabled = customCondition.isNotBlank(),
                ) { Text("Ajouter") }
            }
        }

        // ── Symptoms ──
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ce que je veux suivre", style = MaterialTheme.typography.titleMedium)
            Text(
                "Un symptôme choisi se note en un geste chaque jour, et donne des idées pour les jours difficiles.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val ids = WellbeingLibrary.SYMPTOMS.map { it.id } + settings.symptoms.filter { WellbeingLibrary.symptom(it) == null }
                ids.forEach { id ->
                    val on = id in settings.symptoms
                    FilterChip(
                        selected = on,
                        onClick = { change { w -> w.copy(symptoms = if (on) w.symptoms - id else w.symptoms + id) } },
                        label = { Text("${Wellbeing.emoji(id)}  ${Wellbeing.name(id)}") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    customSymptom, { customSymptom = it.take(40) },
                    label = { Text("Un autre symptôme") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        val name = customSymptom.trim()
                        if (name.isNotEmpty()) change { w -> w.copy(symptoms = (w.symptoms + name).distinct()) }
                        customSymptom = ""
                    },
                    enabled = customSymptom.isNotBlank(),
                ) { Text("Ajouter") }
            }
        }

        // ── Own missions ──
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mes petites missions", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ce qui te fait du bien, à toi. Elles apparaissent dans la journée douce, avec les idées de l'appli.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            settings.customMissions.forEach { text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐  $text", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { change { w -> w.copy(customMissions = w.customMissions - text) } }) { Icon(Icons.Rounded.Close, "Retirer « $text »") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    customMission, { customMission = it.take(80) },
                    label = { Text("Par exemple : prendre un bain") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        val text = customMission.trim()
                        if (text.isNotEmpty()) change { w -> w.copy(customMissions = (w.customMissions + text).distinct().take(30)) }
                        customMission = ""
                    },
                    enabled = customMission.isNotBlank(),
                ) { Text("Ajouter") }
            }
        }

        if (onboarding) {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (settings.enabled) "Continuer" else "Passer pour l'instant") }
        }
        Disclaimer(
            "Les fiches et conseils sont des repères généraux de bien-être, écrits pour t'accompagner. Ils ne remplacent pas un médecin, ne posent aucun diagnostic et ne touchent jamais à un traitement.",
            important = !onboarding,
        )
        Spacer(Modifier.width(1.dp))
    }
}
