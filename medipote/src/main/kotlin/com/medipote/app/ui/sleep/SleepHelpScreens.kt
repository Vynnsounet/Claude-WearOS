package com.medipote.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.domain.SleepHelp
import com.medipote.app.domain.SleepIdea
import com.medipote.app.domain.SleepTool
import com.medipote.app.domain.SleepTrouble
import com.medipote.app.ui.components.Disclaimer

/** A very dim, warm scheme for the hard nights, whatever the app's theme: little light, no blue. */
@Composable
fun NightTheme(content: @Composable () -> Unit) {
    val night = darkColorScheme(
        primary = Color(0xFFE2B36B), onPrimary = Color(0xFF2A1A00),
        primaryContainer = Color(0xFF3A2A12), onPrimaryContainer = Color(0xFFF3D9AE),
        secondary = Color(0xFFC9A86A), onSecondary = Color(0xFF2A1A00),
        secondaryContainer = Color(0xFF2B2113), onSecondaryContainer = Color(0xFFE6CFA3),
        tertiary = Color(0xFFC9A86A), tertiaryContainer = Color(0xFF2B2113), onTertiaryContainer = Color(0xFFE6CFA3),
        background = Color(0xFF050505), onBackground = Color(0xFFD6C8B2),
        surface = Color(0xFF050505), onSurface = Color(0xFFD6C8B2), onSurfaceVariant = Color(0xFFA59886),
        surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF0F0E0C), surfaceContainer = Color(0xFF14120F),
        surfaceContainerHigh = Color(0xFF1B1813), surfaceContainerHighest = Color(0xFF221E18),
        outline = Color(0xFF6A5F4F), outlineVariant = Color(0xFF2E2820),
    )
    MaterialTheme(colorScheme = night, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NightFrame(title: String, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** What the night help can do; the app wires it to the other screens and to the data. */
data class SleepHelpActions(
    val onBack: () -> Unit = {},
    val onTool: (SleepTool) -> Unit = {},
    val onTips: () -> Unit = {},
    val onLogNight: () -> Unit = {},
    val onSupport: () -> Unit = {},
    /** "I tried it": a small victory for the trouble ("fall" or "back"). */
    val onTried: (SleepTrouble) -> Unit = {},
    val onRecipe: (List<String>) -> Unit = {},
)

/**
 * "Nuit difficile ?": two big choices, then four short ideas, the rule of 20 minutes, and the person's own
 * recipe for going back to sleep. Dark and warm, little text, nothing to win or lose.
 */
@Composable
fun SleepHelpScreen(recipe: List<String>, triedToday: Set<String>, a: SleepHelpActions, initial: SleepTrouble? = null) {
    var trouble by rememberSaveable { mutableStateOf(initial?.id) }
    var draft by rememberSaveable { mutableStateOf("") }
    val chosen = SleepTrouble.entries.firstOrNull { it.id == trouble }
    NightTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            NightFrame("Nuit difficile", a.onBack) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Respire. Tu n'as rien à réussir.", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Quelques idées douces, sans lumière vive. Même rester allongé·e au calme repose.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SleepTrouble.entries.forEach { t ->
                    Card(
                        onClick = { trouble = if (trouble == t.id) null else t.id },
                        colors = CardDefaults.cardColors(containerColor = if (trouble == t.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.emoji, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(14.dp))
                            Text(t.label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                if (chosen != null) {
                    SleepHelp.ideas(chosen).forEach { idea -> IdeaCard(idea, a.onTool) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🕰️  ${SleepHelp.RULE_TITLE}", style = MaterialTheme.typography.titleSmall)
                            Text(SleepHelp.RULE_TEXT, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // The person's own recipe comes up on the next hard night.
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⭐  Ma recette pour me rendormir", style = MaterialTheme.typography.titleSmall)
                            if (recipe.isEmpty()) {
                                Text("Ce qui t'a aidé une nuit, noté ici, te sera rappelé la prochaine fois.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            recipe.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("•  $item", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { a.onRecipe(recipe - item) }) { Icon(Icons.Rounded.Close, "Retirer « $item »") }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(draft, { draft = it.take(100) }, label = { Text("Par exemple : une tisane tiède") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = {
                                        val t = draft.trim()
                                        if (t.isNotEmpty()) a.onRecipe((recipe + t).distinct().take(12))
                                        draft = ""
                                    },
                                    enabled = draft.isNotBlank(),
                                ) { Text("Ajouter") }
                            }
                        }
                    }

                    if (chosen.id in triedToday) {
                        Text("✓ Essayé, bravo 💛", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    } else {
                        FilledTonalButton(onClick = { a.onTried(chosen) }) { Text("✓ J'ai essayé quelque chose") }
                    }
                    Text(
                        "Chaque essai rapporte quelques points. Ne rien essayer ne coûte rien.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedButton(onClick = a.onLogNight, modifier = Modifier.fillMaxWidth()) { Text("🌙  Noter ma nuit et mes réveils") }
                OutlinedButton(onClick = a.onSupport, modifier = Modifier.fillMaxWidth()) { Text("🤗  C'est l'angoisse qui m'éveille") }
                TextButton(onClick = a.onTips, modifier = Modifier.fillMaxWidth()) { Text("🌤️  Pour mieux dormir les prochains soirs") }
                Disclaimer(SleepHelp.NOTE + " " + SleepHelp.CONSULT.first())
            }
        }
    }
}

@Composable
private fun IdeaCard(idea: SleepIdea, onTool: (SleepTool) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${idea.emoji}  ${idea.title}", style = MaterialTheme.typography.titleSmall)
            Text(idea.text, style = MaterialTheme.typography.bodyLarge)
            idea.tool?.let { tool ->
                FilledTonalButton(onClick = { onTool(tool) }) {
                    Text(when (tool) { SleepTool.BREATHE -> "Respirer"; SleepTool.RELAX -> "Commencer"; SleepTool.THOUGHTS -> "Écrire" })
                }
            }
        }
    }
}

/** The guided relaxation: one calm line at a time, in the dark, with a button to go on at one's own pace. */
@Composable
fun RelaxScreen(onBack: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val steps = SleepHelp.RELAX_STEPS
    NightTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            NightFrame("Détendre le corps", onBack) {
                Spacer(Modifier.height(24.dp))
                Text(
                    steps[step], style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
                LinearProgressIndicator(
                    progress = { (step + 1) / steps.size.toFloat() }, drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(androidx.compose.foundation.shape.CircleShape),
                )
                Text("${step + 1} / ${steps.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                if (step < steps.lastIndex) {
                    Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Suivant, quand tu es prêt·e") }
                } else {
                    Text(
                        "C'est fini. Reste là, tranquille. Le sommeil viendra quand il voudra.",
                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Recommencer") }
                }
                if (step > 0 && step < steps.lastIndex) TextButton(onClick = { step-- }, modifier = Modifier.fillMaxWidth()) { Text("Revenir en arrière") }
            }
        }
    }
}

/** For the days before: what makes the nights easier, and the soft evening reminder. */
@Composable
fun SleepTipsScreen(windDown: Boolean, onWindDown: (Boolean) -> Unit, onEvening: () -> Unit, onHelp: () -> Unit, onBack: () -> Unit) {
    com.medipote.app.ui.wellbeing.WbPage("Pour mieux dormir", embedded = false, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Des repères pour les prochains soirs", style = MaterialTheme.typography.titleLarge)
            Text("Pas tous à la fois : choisis-en un ou deux.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SleepHelp.DAY_TIPS.forEach { tip ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${tip.emoji}  ${tip.title}", style = MaterialTheme.typography.titleSmall)
                    Text(tip.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Un petit mot le soir", style = MaterialTheme.typography.titleSmall)
                Text("« Doucement vers la nuit », à l'heure que tu choisis dans les réglages. Jamais pendant les heures calmes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = windDown, onCheckedChange = onWindDown)
        }
        OutlinedButton(onClick = onEvening, modifier = Modifier.fillMaxWidth()) { Text("✨  Mon rituel du soir") }
        OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text("🌙  J'ai du mal à dormir cette nuit") }
        Disclaimer(SleepHelp.NOTE + " " + SleepHelp.CONSULT.joinToString(" "), important = true)
    }
}
