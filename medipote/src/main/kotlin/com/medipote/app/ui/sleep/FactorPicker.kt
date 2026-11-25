package com.medipote.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.data.SleepSettings
import com.medipote.app.domain.FactorEffect
import com.medipote.app.domain.FactorGroup
import com.medipote.app.domain.SleepFactor
import com.medipote.app.domain.SleepFactors
import com.medipote.app.ui.treatments.SwitchRow
import com.medipote.app.ui.stats.StatCard

/**
 * « Ma soirée »: what the evening held, by theme. A factor with precisions asks for them right under it;
 * the list can be shortened, completed with the person's own words, and the private themes switched on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FactorPicker(
    selected: List<String>,
    settings: SleepSettings,
    onChange: (List<String>) -> Unit,
    onSettings: ((SleepSettings) -> SleepSettings) -> Unit,
) {
    var manage by rememberSaveable { mutableStateOf(false) }
    var newOwn by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val shown = SleepFactors.visible(settings)
        FactorGroup.entries.forEach { group ->
            val factors = shown.filter { it.group == group }
            if (factors.isEmpty()) return@forEach
            Text("${group.emoji}  ${group.label}", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                factors.forEach { f -> FactorChip(f, selected, onChange) }
            }
            factors.forEach { f -> FactorDetails(f, selected, onChange) }
        }
        val own = settings.customFactors
        val ownSelected = selected.filter { SleepFactors.byId(SleepFactors.baseOf(it)) == null }
        if (own.isNotEmpty() || ownSelected.isNotEmpty()) {
            Text("✏️  À moi", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (own + ownSelected.filter { it !in own }).forEach { t ->
                    val on = t in selected
                    FilterChip(
                        selected = on, onClick = { onChange(if (on) selected - t else selected + t) }, label = { Text(t) },
                        leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                        trailingIcon = if (manage && t in own) ({ Icon(Icons.Rounded.Close, "Retirer « $t » de ma liste", Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    )
                }
            }
        }
        TextButton(onClick = { manage = !manage }) { Text(if (manage) "Fermer la personnalisation" else "⚙️  Personnaliser ma liste") }
        if (manage) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ajouter à ma liste", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = newOwn, onValueChange = { newOwn = it.take(30) }, label = { Text("Un facteur à moi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = {
                            val name = newOwn.trim()
                            if (name.isNotEmpty() && name !in settings.customFactors) onSettings { it.copy(customFactors = it.customFactors + name) }
                            newOwn = ""
                        },
                        enabled = newOwn.isNotBlank(),
                    ) { Text("Ajouter") }
                    Text("Ce que je vois", style = MaterialTheme.typography.titleSmall)
                    Text("Touche pour garder ou ranger un facteur : la liste reste courte.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SleepFactors.ALL.filter { !it.group.sensitive || settings.sensitiveFactors }.forEach { f ->
                            val visible = f.id !in settings.hiddenFactors
                            FilterChip(
                                selected = visible,
                                onClick = { onSettings { it.copy(hiddenFactors = if (visible) it.hiddenFactors + f.id else it.hiddenFactors - f.id) } },
                                label = { Text("${f.emoji} ${f.label}") },
                            )
                        }
                    }
                    SwitchRow(
                        "Substances et rapport sexuel", settings.sensitiveFactors,
                        supporting = "Cachés tant que tu ne l'actives pas. Ils restent sur ton téléphone et ne vont pas dans le rapport pour le médecin, sauf si tu le coches.",
                    ) { on -> onSettings { it.copy(sensitiveFactors = on) } }
                    own.forEach { t ->
                        TextButton(onClick = { onSettings { it.copy(customFactors = it.customFactors - t) } }) { Text("Retirer « $t »") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactorChip(f: SleepFactor, selected: List<String>, onChange: (List<String>) -> Unit) {
    val on = selected.any { SleepFactors.baseOf(it) == f.id }
    FilterChip(
        selected = on,
        onClick = { onChange(SleepFactors.toggle(selected, f.id)) },
        label = { Text("${f.emoji} ${f.label}") },
        leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
    )
}

/** The precisions of a chosen factor, right under its theme. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactorDetails(f: SleepFactor, selected: List<String>, onChange: (List<String>) -> Unit) {
    val current = selected.firstOrNull { SleepFactors.baseOf(it) == f.id } ?: return
    if (f.choices.isNotEmpty()) {
        Row(Modifier.padding(start = 8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                f.choices.forEach { (id, label) ->
                    FilterChip(
                        selected = current == "${f.id}=$id",
                        onClick = { onChange(SleepFactors.choose(selected, f.id, id)) },
                        label = { Text("${f.label} : $label") },
                    )
                }
            }
        }
    }
    if (f.askDetail) {
        var text by remember(f.id) { mutableStateOf(SleepFactors.detailOf(current).orEmpty()) }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(40); onChange(SleepFactors.describe(selected, f.id, text)) },
            label = { Text("Laquelle ? (facultatif)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        )
    }
}

/** « Ce qui change tes nuits »: what goes with better or worse nights, or why there is nothing to say yet. */
@Composable
fun FactorEffectsCard(effects: List<FactorEffect>, nights: Int, anyNoted: Boolean) {
    StatCard("🔍  Ce qui change tes nuits") {
        if (effects.isEmpty()) {
            Text(
                when {
                    !anyNoted -> "Note ta soirée (café, repas, sortie…) dans la fiche de nuit : après une dizaine de nuits, je te montre ce qui va avec tes meilleures et tes moins bonnes nuits."
                    nights < SleepFactors.MIN_WITH + SleepFactors.MIN_WITHOUT -> "Encore ${SleepFactors.MIN_WITH + SleepFactors.MIN_WITHOUT - nights} nuit${if (SleepFactors.MIN_WITH + SleepFactors.MIN_WITHOUT - nights > 1) "s" else ""} et je pourrai comparer les nuits avec et sans chaque facteur."
                    else -> "Rien de net pour l'instant : aucun facteur ne se détache vraiment. Il faut parfois plus de nuits, avec et sans."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            effects.forEach { e ->
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text("${e.emoji}  ${e.label}${if (e.auto) " · repéré automatiquement" else ""}", style = MaterialTheme.typography.titleSmall)
                    Text(e.sentence, style = MaterialTheme.typography.bodyMedium, color = if (e.good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(
            "Ce sont des choses qui vont ensemble dans tes notes, pas des causes : d'autres facteurs jouent aussi. Parles-en à ton médecin si ton sommeil te pèse.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The card of the sleep page where the evening is noted, without waiting for the morning. */
@Composable
fun EveningCard(selected: List<String>, settings: SleepSettings, onChange: (List<String>) -> Unit, onSettings: ((SleepSettings) -> SleepSettings) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("🌆  Ma soirée", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (selected.isEmpty()) "Note ce qui a compté ce soir : le matin, c'est déjà dans ta nuit."
                        else selected.joinToString(", ") { SleepFactors.label(it) },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { open = !open }) { Text(if (open) "Fermer" else "Noter") }
            }
            if (open) FactorPicker(selected, settings, onChange, onSettings)
        }
    }
}
