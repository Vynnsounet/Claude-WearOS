package com.medipote.app.ui.wellbeing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.Mission
import com.medipote.app.domain.Wellbeing
import com.medipote.app.domain.WellbeingLibrary
import com.medipote.app.ui.components.Disclaimer
import java.time.LocalDateTime

/** A page of the wellbeing screens: scrolls in place inside a hub, or with its own bar and back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WbPage(title: String, embedded: Boolean, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (embedded) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private val INTENSITY_WORDS = listOf("très léger", "léger", "modéré", "fort", "très fort")

// ─────────────────────────── Symptoms of the day ───────────────────────────

/** "Qu'est-ce que tu ressens aujourd'hui ?": one tap per symptom, 1 (a little) to 5 (very strongly). */
@Composable
fun SymptomsScreen(
    state: AppState,
    now: LocalDateTime,
    onSet: (String, Int?) -> Unit,
    onAdvice: (String) -> Unit,
    onManage: () -> Unit,
    onSoftDay: () -> Unit,
    embedded: Boolean = true,
    onBack: () -> Unit = {},
) {
    val today = now.toLocalDate()
    val tracked = Wellbeing.tracked(state)
    val mentioned = Wellbeing.mentionedInJournal(state, today)
    val insights = androidx.compose.runtime.remember(state.symptomLog, state.sleep, today) { Wellbeing.insights(state, today) }
    WbPage("Symptômes", embedded, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Qu'est-ce que tu ressens aujourd'hui ?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Touche un chiffre : 1 = un peu, 5 = très fort. Touche-le encore pour l'enlever.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (tracked.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Aucun symptôme suivi pour l'instant.", style = MaterialTheme.typography.titleMedium)
                    Text("Choisis ceux que tu veux garder à l'œil : fatigue, anxiété, douleur…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onManage) { Text("Choisir mes symptômes") }
                }
            }
        }

        tracked.forEach { id ->
            val value = Wellbeing.intensityOn(state, today, id)
            val week = Wellbeing.summary(state, id, today).week
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Wellbeing.emoji(id), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Wellbeing.name(id), style = MaterialTheme.typography.titleMedium)
                            Text(
                                value?.let { INTENSITY_WORDS[it - 1] } ?: "pas aujourd'hui",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SymptomPicker(id, value) { onSet(id, it) }
                    if (id in mentioned) {
                        Text("📖  Noté dans ton journal : choisis la force.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (week.any { it != null }) WeekDots(week)
                    if ((value ?: 0) >= 3) {
                        FilledTonalButton(onClick = { onAdvice(id) }, modifier = Modifier.fillMaxWidth()) { Text("💛  Voir ce qui peut aider") }
                    }
                }
            }
        }

        if (tracked.isNotEmpty()) {
            TextButton(onClick = onManage, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Gérer mes symptômes") }
        }

        if (insights.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔎  Ce que je remarque", style = MaterialTheme.typography.titleMedium)
                    insights.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "Ce sont des observations de tes notes, pas un diagnostic : montre-les à ton médecin si tu veux.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (tracked.isNotEmpty() && Wellbeing.heavyDay(state, today)) {
            Surface(onClick = onSoftDay, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💛", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Journée lourde ?", style = MaterialTheme.typography.titleSmall)
                        Text("De toutes petites missions, à ton rythme.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Disclaimer("Ces infos sont là pour t'accompagner : elles ne remplacent pas ton médecin. Tout reste sur ton téléphone.")
    }
}

/** Five dots from "a little" to "very strongly"; touching the chosen one again removes it. */
@Composable
fun SymptomPicker(symptom: String, value: Int?, onSet: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..5).forEach { level ->
            IntensityDot(level, selected = value == level, label = "${Wellbeing.name(symptom)}, intensité $level sur 5") {
                onSet(if (value == level) null else level)
            }
        }
    }
}

@Composable
private fun IntensityDot(level: Int, selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$level", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The last seven days of a symptom: one dot a day, darker when stronger, empty when not noted. */
@Composable
private fun WeekDots(week: List<Int?>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("7 derniers jours", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        week.forEach { v ->
            Surface(
                shape = CircleShape,
                color = if (v == null) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f + 0.16f * v),
                modifier = Modifier.size(14.dp),
            ) {}
        }
    }
}

// ─────────────────────────── What can help ───────────────────────────

/** Gentle advice for a symptom, and when to talk to a doctor: general wellbeing information. */
@Composable
fun SymptomAdviceScreen(
    symptom: String,
    onSoftDay: () -> Unit,
    onSupport: () -> Unit,
    onBack: () -> Unit,
) {
    val info = WellbeingLibrary.symptom(symptom)
    WbPage("${Wellbeing.emoji(symptom)}  ${Wellbeing.name(symptom)}", embedded = false, onBack = onBack) {
        Text(
            if (info?.kind == true) "Ce que je te souhaite d'entendre d'abord :" else "Ce qui aide souvent, à ton rythme :",
            style = MaterialTheme.typography.titleMedium,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                (info?.tips ?: listOf(
                    "Note ce que tu ressens et ce qui précède : c'est utile pour ton médecin.",
                    "Repose-toi, bois un verre d'eau, fais une pause sans écran.",
                    "Si ça te pèse, parles-en à une personne de confiance.",
                )).forEach { tip ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge)
                        Text(tip, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Button(onClick = onSoftDay, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("💛  Des petites missions pour aujourd'hui") }
        if (symptom in setOf("anxiety", "lowmood", "body")) {
            OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("🤗  Ça ne va pas : respirer, se poser") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quand en parler à un médecin", style = MaterialTheme.typography.titleMedium)
                (info?.consult ?: listOf("Si ça dure, s'aggrave ou t'inquiète : parles-en à ton médecin.")).forEach { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Disclaimer(
            "Ce sont des repères généraux de bien-être, pas un avis médical : ils ne remplacent pas ton médecin et ne touchent jamais à ton traitement. En cas de doute ou de signe inquiétant, appelle le 15.",
            important = true,
        )
    }
}

// ─────────────────────────── The gentle day ───────────────────────────

private val ENERGY = listOf(
    Triple(1, "💤", "Très peu"),
    Triple(2, "🔋", "Un peu"),
    Triple(3, "⚡", "Presque comme d'habitude"),
)

/**
 * "Journée douce": the energy left, a few tiny missions to match it, and tasks cut into small steps.
 * Missions pay a few points when done; skipping one costs nothing and shows nowhere.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoftDayScreen(
    state: AppState,
    now: LocalDateTime,
    onEnergy: (Int) -> Unit,
    onDo: (Mission) -> Unit,
    onNewTask: () -> Unit,
    onOpenTask: (String) -> Unit,
    onSupport: () -> Unit,
    onMine: () -> Unit,
    embedded: Boolean = true,
    onBack: () -> Unit = {},
) {
    val today = now.toLocalDate()
    val energy = Wellbeing.energyOn(state, today)
    var shuffle by rememberSaveable { mutableIntStateOf(0) }
    val missions = Wellbeing.suggest(state, today, energy ?: 2, shuffle)
    val mine = Wellbeing.custom(state)
    val wins = Wellbeing.winsOn(state, today)
    val tasks = Wellbeing.activeTasks(state)
    val strong = Wellbeing.strongToday(state, today)

    WbPage("Journée douce", embedded, onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Une journée à ton rythme", style = MaterialTheme.typography.titleLarge)
            Text(
                if (strong.isNotEmpty()) "Tu as noté ${strong.take(2).joinToString(" et ") { Wellbeing.name(it).lowercase() }}. Pas de mauvaise réponse : se reposer compte aussi."
                else "Pas de mauvaise réponse : se reposer compte aussi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Comment est ton énergie ?", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ENERGY.forEach { (level, emoji, label) ->
                    FilterChip(selected = energy == level, onClick = { onEnergy(level) }, label = { Text("$emoji  $label") })
                }
            }
        }

        if (wins.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "💛  ${wins.size} petite${if (wins.size > 1) "s" else ""} victoire${if (wins.size > 1) "s" else ""} aujourd'hui",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    wins.take(4).forEach { Text("✓ ${it.label}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pour toi, aujourd'hui", style = MaterialTheme.typography.titleMedium)
            if (missions.isEmpty()) {
                Text("Tu as fait toutes les idées du moment. Bravo, et repose-toi si tu en as envie. 💛", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            missions.forEach { m -> MissionRow(m) { onDo(m) } }
            TextButton(onClick = { shuffle++ }) { Text("🔄  D'autres idées") }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mes missions", style = MaterialTheme.typography.titleMedium)
            if (mine.isEmpty()) {
                Text("Ajoute ce qui te fait du bien, à toi : un bain, appeler ta sœur…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            mine.filter { !Wellbeing.done(state, today, it.id) }.forEach { m -> MissionRow(m) { onDo(m) } }
            TextButton(onClick = onMine) { Text("✏️  Ajouter ou modifier mes missions") }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Une tâche à faire ?", style = MaterialTheme.typography.titleMedium)
            tasks.forEach { t ->
                Surface(onClick = { onOpenTask(t.id) }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.titleSmall)
                            Text("${t.doneCount} / ${t.steps.size} étapes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = onNewTask, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("🧩  La découper en petites étapes") }
        }

        Surface(onClick = onSupport, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🤗", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("C'est plus que le corps ?", style = MaterialTheme.typography.titleSmall)
                    Text("Respirer, se poser, parler à quelqu'un.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            "Les missions rapportent quelques points quand tu les fais. Ne pas en faire ne coûte rien et ne se voit nulle part.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MissionRow(m: Mission, onDone: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(m.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
                if (m.minutes > 0) Text("environ ${m.minutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onDone) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Fait")
            }
        }
    }
}
