package com.medipote.app.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medipote.app.domain.FeelingGuide
import com.medipote.app.domain.SupportTool
import com.medipote.app.ui.components.Disclaimer

/** What the "Ce que je ressens" screen can do; the app wires it to the other screens, the dialer and the data. */
data class FeelActions(
    val onBack: () -> Unit = {},
    val onTool: (SupportTool) -> Unit = {},
    val onCall: (String) -> Unit = {},
    /** Notes the symptoms of the chosen signs for today, with a strength from 1 to 5. */
    val onNote: (List<String>, Int) -> Unit = { _, _ -> },
)

/**
 * "Ce que je ressens": the person touches what they feel, in the body or in the head (anxiety, panic,
 * low mood), and gets what can be tried right now, a word on why it happens, and when to talk to
 * someone. Nothing is diagnosed; a chest sign always reminds when to call.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeelScreen(a: FeelActions, initial: List<String> = emptyList()) {
    var chosen by rememberSaveable(stateSaver = listSaver(save = { it }, restore = { it })) { mutableStateOf(initial) }
    var strength by rememberSaveable { mutableIntStateOf(3) }
    val signs = chosen.mapNotNull(FeelingGuide::sign)
    val urgent = signs.any { it.urgent }
    val tools = signs.flatMap { it.tools }.distinct()
    val symptoms = FeelingGuide.symptomsOf(chosen)
    val consult = FeelingGuide.feelingsOf(chosen).flatMap { it.consult }.distinct()
    val chest = signs.any { it.chest }
    val help = com.medipote.app.domain.Support.HELP_LINES

    SupportFrame("Ce que je ressens", a.onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Touche ce qui te ressemble.", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Dans le corps comme dans la tête. Tout ça est fréquent quand ça ne va pas, et ça passe souvent plus vite qu'on ne le croit.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FeelingGuide.FEELINGS.forEach { f ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${f.emoji}  ${f.label}", style = MaterialTheme.typography.titleMedium)
                Text(f.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeelingGuide.signsOf(f.id).forEach { s ->
                        FilterChip(
                            selected = s.id in chosen,
                            onClick = { chosen = if (s.id in chosen) chosen - s.id else chosen + s.id },
                            label = { Text("${s.emoji}  ${s.label}") },
                        )
                    }
                }
            }
        }

        if (chosen.isEmpty()) {
            Text(
                "Choisis une ou plusieurs choses : tu verras ce qui peut aider, tout de suite.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SupportFrame
        }

        // Dark thoughts come first: one tap to the line that answers day and night.
        if (urgent) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tu n'as pas à rester seul·e avec ça", style = MaterialTheme.typography.titleMedium)
                    Text(help.first().detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { a.onCall(help.first().number) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("📞 Appeler le ${help.first().number}") }
                }
            }
        }

        Text("Ce qui peut aider maintenant", style = MaterialTheme.typography.titleMedium)
        signs.filter { !it.urgent }.forEach { s ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${s.emoji}  ${s.label}", style = MaterialTheme.typography.titleSmall)
                    Text(s.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.tryNow.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }

        if (tools.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pour t'y aider", style = MaterialTheme.typography.titleMedium)
                tools.forEach { t ->
                    FilledTonalButton(onClick = { a.onTool(t) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("${t.emoji}  ${t.label}") }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quand en parler", style = MaterialTheme.typography.titleMedium)
                if (chest) {
                    Text(FeelingGuide.CHEST_ALERT, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { a.onCall("15") }, modifier = Modifier.fillMaxWidth()) { Text("📞 Appeler le 15") }
                }
                consult.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        if (symptoms.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📝  Les noter dans mes symptômes", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pour t'en souvenir et le montrer à ton médecin. Cela ajoute ${symptoms.joinToString(", ") { com.medipote.app.domain.Wellbeing.name(it).lowercase() }} à ton suivi.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Aujourd'hui, c'est…", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "léger", 2 to "gênant", 3 to "pesant", 4 to "fort", 5 to "très fort").forEach { (v, label) ->
                            FilterChip(selected = strength == v, onClick = { strength = v }, label = { Text("$v · $label") })
                        }
                    }
                    Button(onClick = { a.onNote(symptoms, strength) }, modifier = Modifier.fillMaxWidth()) { Text("Noter") }
                }
            }
        }

        Disclaimer(
            "MédiPote est un soutien, pas un soin : il ne pose aucun diagnostic et ne remplace ni un médecin ni un psy. Si tu penses à te faire du mal, appelle le 3114 ou le 15 maintenant.",
            important = true,
        )
    }
}
