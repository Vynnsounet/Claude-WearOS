package com.medipote.app.ui.support

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.data.SupportBox
import com.medipote.app.data.SupportSettings
import com.medipote.app.data.ThoughtNote
import com.medipote.app.data.TrustedContact
import com.medipote.app.domain.BreathPhase
import com.medipote.app.domain.BreathPattern
import com.medipote.app.domain.Support
import com.medipote.app.ui.components.Disclaimer
import com.medipote.app.ui.components.MoreOptions
import com.medipote.app.ui.motivation.FacePicker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val noteDate = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.FRANCE)

/** A calm frame for every support screen: nothing flashy, nothing to win. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportFrame(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** What the support screen can do; the app wires it to navigation, the phone and the data. */
data class SupportActions(
    val onBack: () -> Unit = {},
    val onBreathe: () -> Unit = {},
    val onGround: () -> Unit = {},
    val onThoughts: () -> Unit = {},
    val onComfort: () -> Unit = {},
    val onPlan: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onCall: (String) -> Unit = {},
    val onMessage: (TrustedContact) -> Unit = {},
    val onMood: (Int?) -> Unit = {},
    val onAskAgain: () -> Unit = {},
)

/** "Ça ne va pas": a few big, quiet ways to get through a hard moment. */
@Composable
fun SupportScreen(state: AppState, now: LocalDateTime, a: SupportActions) {
    val contacts = Support.contacts(state)
    val pro = state.settings.support.let { if (Support.validPhone(it.professionalPhone)) it else null }
    val entry = state.entryFor(now.toLocalDate())
    SupportFrame(
        "Ça ne va pas", a.onBack,
        actions = { IconButton(onClick = a.onSettings) { Icon(Icons.Outlined.Settings, "Régler le soutien") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Je suis là.", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Tu n'as pas besoin de tout faire. Juste un petit pas, à ton rythme.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // If it is too heavy: always at the top, one tap from the dialer.
        val main = Support.HELP_LINES.first()
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Si c'est trop lourd", style = MaterialTheme.typography.titleMedium)
                Text(main.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { a.onCall(main.number) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("📞 Appeler le ${main.number}") }
                MoreOptions("15, 112" + if (pro != null) ", ${pro.professionalName.ifBlank { "mon médecin" }}" else "", label = "Autres numéros") {
                    Support.HELP_LINES.drop(1).forEach { line -> CallRow(line.name, line.detail) { a.onCall(line.number) } }
                    pro?.let { CallRow(it.professionalName.ifBlank { "Mon médecin ou mon psy" }, it.professionalPhone) { a.onCall(it.professionalPhone) } }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Qu'est-ce qui t'aiderait ?", style = MaterialTheme.typography.titleMedium)
            ToolRow("🌬️", "Respirer", "Un cercle pour caler ton souffle", a.onBreathe)
            ToolRow("🖐️", "Me poser", "5 choses que je vois, 4 que je touche…", a.onGround)
            ToolRow("✍️", "Vider ma tête", "Écrire sans filtre, rien que pour moi", a.onThoughts)
            ToolRow("🧸", "Ma boîte à réconfort", "Ce que je sais qui me fait du bien", a.onComfort)
            ToolRow("📝", "Mon plan", "Mes signes, ce qui m'aide, qui appeler", a.onPlan)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Parler à quelqu'un", style = MaterialTheme.typography.titleMedium)
            if (contacts.isEmpty()) {
                Text(
                    "Ajoute une ou deux personnes de confiance, quand tu te sens mieux : un appel ou un message tout prêt, en un geste.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = a.onSettings) { Text("Choisir mes personnes de confiance") }
            }
            contacts.forEach { c ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name.ifBlank { c.phone }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { a.onCall(c.phone) }) { Text("Appeler") }
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { a.onMessage(c) }) { Text("Message") }
                    }
                }
            }
        }

        MoreOptions("mâchoire, épaules, eau", label = "Gorge nouée ?") {
            Support.THROAT_TIPS.forEach { tip ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                    Text(tip, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Et maintenant ?", style = MaterialTheme.typography.titleMedium)
            Text("Comment tu te sens ?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FacePicker(entry.mental, "Humeur", a.onMood)
            if (state.supportFollowUp == null) {
                TextButton(onClick = a.onAskAgain) { Text("🤗 Me redemander dans 2 h") }
            } else {
                Text(
                    "Je te redemande à ${state.supportFollowUp.format(DateTimeFormatter.ofPattern("HH:mm"))}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Disclaimer(
            "MédiPote est un soutien, pas un soin : il ne remplace ni un médecin ni un psy. Si tu penses à te faire du mal, appelle le 3114 ou le 15 maintenant. Tout ce que tu écris ici reste sur ton téléphone.",
            important = true,
        )
    }
}

@Composable
private fun ToolRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CallRow(name: String, detail: String, onCall: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onCall) { Text("Appeler") }
    }
}

// ─────────────────────────── Breathing ───────────────────────────

/** A circle that swells and empties; the breath out is longer than the breath in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreathingScreen(vibrate: Boolean, onBack: () -> Unit, animate: Boolean = true) {
    val context = LocalContext.current
    var patternId by rememberSaveable { mutableStateOf(Support.PATTERNS.first().id) }
    val pattern: BreathPattern = Support.PATTERNS.first { it.id == patternId }
    var elapsed by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableIntStateOf(0) }

    // The clock of the exercise, restarted when the way of breathing changes.
    LaunchedEffect(patternId, animate) {
        elapsed = 0L
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameMillis { t ->
                if (last == 0L) last = t
                elapsed += t - last
                last = t
            }
        }
    }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            seconds++
        }
    }
    val state = pattern.at(elapsed)

    // A soft tick at the start of each breath in and out.
    LaunchedEffect(state.phase, patternId) {
        if (!animate || !vibrate || (state.phase != BreathPhase.INHALE && state.phase != BreathPhase.EXHALE)) return@LaunchedEffect
        runCatching { context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(40, 60)) }
    }

    // 0.55 when empty, 1 when full.
    val fill = when (state.phase) {
        BreathPhase.INHALE -> 0.55f + 0.45f * state.progress
        BreathPhase.HOLD_IN -> 1f
        BreathPhase.EXHALE -> 1f - 0.45f * state.progress
        BreathPhase.HOLD_OUT -> 0.55f
    }
    SupportFrame("Respirer", onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(260.dp).scale(fill).clip(CircleShape).let {
                        it.then(Modifier.semantics { contentDescription = "${state.phase.label}, encore ${state.secondsLeft} secondes" })
                    },
                ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.phase.label, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${state.secondsLeft}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(
                "Pose tes épaules. Suis le cercle, à ton rythme. ${pattern.detail}.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            Support.PATTERNS.forEach { p -> FilterChip(selected = p.id == patternId, onClick = { patternId = p.id }, label = { Text(p.label) }) }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ça va un peu mieux") }
    }
}

// ─────────────────────────── Grounding ───────────────────────────

/** 5-4-3-2-1: bringing attention back to the room, one sense at a time. */
@Composable
fun GroundingScreen(onBack: () -> Unit, onBreathe: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val steps = Support.GROUNDING
    SupportFrame("Me poser", onBack) {
        if (step < steps.size) {
            val s = steps[step]
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(24.dp))
                Text("${s.count}", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text(s.sense, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(s.hint, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Text("${step + 1} / ${steps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (step == steps.lastIndex) "J'ai fini" else "Suivant")
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(24.dp))
                Text("🌿", style = MaterialTheme.typography.displayMedium)
                Text("Bravo.", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Prends une grande inspiration, et laisse l'air sortir doucement. Tu es ici, maintenant.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onBreathe, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("🌬️ Respirer un peu") }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Retour") }
            TextButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Recommencer") }
        }
    }
}

// ─────────────────────────── Thoughts ───────────────────────────

/** Writing it all down. Not kept unless the user says so. */
@Composable
fun ThoughtsScreen(box: SupportBox, onKeep: (String) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var kept by rememberSaveable { mutableStateOf(false) }
    SupportFrame("Vider ma tête", onBack) {
        Text(
            "Écris tout ce qui vient, comme ça vient. Personne ne lira. Rien n'est gardé si tu ne le demandes pas.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it; kept = false },
            placeholder = { Text("Ce que je ressens…") },
            minLines = 8, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { text = ""; kept = false }, enabled = text.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Tout effacer") }
            Button(onClick = { onKeep(text); text = ""; kept = true }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Garder en privé") }
        }
        if (kept) Text("Gardé. Merci d'avoir posé ça 💚", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (box.thoughts.isNotEmpty()) {
            MoreOptions("${box.thoughts.size} gardée${if (box.thoughts.size > 1) "s" else ""}", label = "Ce que j'ai gardé") {
                box.thoughts.forEach { ThoughtCard(it) { onDelete(it.id) } }
            }
        }
    }
}

@Composable
private fun ThoughtCard(note: ThoughtNote, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(note.at.format(noteDate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(note.text, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, "Supprimer cette note") }
        }
    }
}

// ─────────────────────────── Comfort box and plan ───────────────────────────

/** A short list the user can add to and remove from. */
@Composable
private fun ListEditor(title: String, hint: String, items: List<String>, onChange: (List<String>) -> Unit) {
    var draft by rememberSaveable(title) { mutableStateOf("") }
    fun add() {
        val t = draft.trim()
        if (t.isNotEmpty()) onChange(items + t)
        draft = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        items.forEachIndexed { i, item ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Rounded.Close, "Retirer : $item") }
                }
            }
        }
        OutlinedTextField(
            value = draft, onValueChange = { draft = it }, placeholder = { Text(hint) }, singleLine = true,
            trailingIcon = { IconButton(onClick = ::add, enabled = draft.isNotBlank()) { Icon(Icons.Rounded.Add, "Ajouter") } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Written on a good day, for the hard ones: a message to oneself, and what helps. */
@Composable
fun ComfortScreen(box: SupportBox, onChange: ((SupportBox) -> SupportBox) -> Unit, onBack: () -> Unit) {
    SupportFrame("Ma boîte à réconfort", onBack) {
        Text(
            "Remplis-la quand tu te sens mieux : le jour où ça ira moins bien, tu n'auras pas à chercher.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Un message pour moi", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = box.messageToSelf, onValueChange = { v -> onChange { it.copy(messageToSelf = v) } },
                placeholder = { Text("Ce que je voudrais m'entendre dire quand ça va mal…") },
                minLines = 4, modifier = Modifier.fillMaxWidth(),
            )
        }
        ListEditor("Ce qui me fait du bien", "Une chanson, une promenade, un thé…", box.comfort) { v -> onChange { it.copy(comfort = v) } }
    }
}

/** A small safety plan: signs, what helps, who to call, what matters. */
@Composable
fun PlanScreen(state: AppState, onChange: ((SupportBox) -> SupportBox) -> Unit, onContacts: () -> Unit, onBack: () -> Unit) {
    val box = state.support
    val contacts = Support.contacts(state)
    SupportFrame("Mon plan", onBack) {
        Text(
            "Un plan simple, écrit à l'avance, pour les moments où penser est difficile. Prends ton temps, tu peux le compléter petit à petit.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ListEditor("Mes signes que ça glisse", "Je ne dors plus, je m'isole…", box.signs) { v -> onChange { it.copy(signs = v) } }
        ListEditor("Ce qui m'aide", "Respirer, marcher, appeler…", box.helps) { v -> onChange { it.copy(helps = v) } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Qui je peux appeler", style = MaterialTheme.typography.titleMedium)
            if (contacts.isEmpty()) Text("Personne pour l'instant.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(contacts.joinToString(", ") { it.name.ifBlank { it.phone } }, style = MaterialTheme.typography.bodyLarge)
            Text("3114 · jour et nuit, gratuit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onContacts) { Text("Modifier mes personnes de confiance") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ce qui compte pour moi", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = box.matters, onValueChange = { v -> onChange { it.copy(matters = v) } },
                placeholder = { Text("Les personnes, les projets, ce que je ne veux pas perdre…") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────── Settings ───────────────────────────

/** Trusted people, a doctor or therapist, and what the support offers. */
@Composable
fun SupportSettingsScreen(settings: SupportSettings, onChange: ((SupportSettings) -> SupportSettings) -> Unit, onBack: () -> Unit) {
    SupportFrame("Soutien : mes réglages", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mes personnes de confiance", style = MaterialTheme.typography.titleMedium)
            Text(
                "Jusqu'à trois personnes, à appeler ou à prévenir d'un message tout prêt. Le numéro n'est composé que si tu appuies sur « appeler ».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (0 until 3).forEach { i ->
                val c = settings.contacts.getOrNull(i) ?: TrustedContact("", "")
                fun set(n: TrustedContact) = onChange { s ->
                    val list = (0 until 3).map { j -> if (j == i) n else s.contacts.getOrNull(j) ?: TrustedContact("", "") }
                    s.copy(contacts = list.dropLastWhile { it.name.isBlank() && it.phone.isBlank() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(c.name, { set(c.copy(name = it.take(30))) }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        c.phone, { set(c.copy(phone = it.take(20))) }, label = { Text("Téléphone") }, singleLine = true,
                        isError = c.phone.isNotBlank() && !Support.validPhone(c.phone),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mon médecin ou mon psy", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(settings.professionalName, { v -> onChange { it.copy(professionalName = v.take(30)) } }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    settings.professionalPhone, { v -> onChange { it.copy(professionalPhone = v.take(20)) } }, label = { Text("Téléphone") }, singleLine = true,
                    isError = settings.professionalPhone.isNotBlank() && !Support.validPhone(settings.professionalPhone),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1.2f),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingSwitch("Un petit cœur sur l'accueil", "Pour ouvrir « Ça ne va pas » en un geste", settings.homeButton) { v -> onChange { it.copy(homeButton = v) } }
            SettingSwitch("Proposer du soutien", "Quand tu notes ton humeur « très mal »", settings.suggestOnLowMood) { v -> onChange { it.copy(suggestOnLowMood = v) } }
            SettingSwitch("Vibrations de respiration", "Un petit tic à chaque souffle", settings.vibrate) { v -> onChange { it.copy(vibrate = v) } }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
