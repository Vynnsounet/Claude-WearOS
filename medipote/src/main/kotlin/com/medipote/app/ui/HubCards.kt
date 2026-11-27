package com.medipote.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.Sleep
import com.medipote.app.domain.Wellbeing
import java.time.LocalDateTime

/**
 * A card of a hub page: its [key] is stable (it is what "Mes cartes" hides), [preview] a live line
 * under the name. With [onClick] the card opens another screen instead of showing its content in place.
 */
data class HubCard(
    val key: String,
    val emoji: String,
    val label: String,
    val preview: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * What a hub page needs to be a little personal: the cards pinned on home, what the person opens (to order
 * the cards and offer to put away the unused ones). All local, all optional.
 */
data class HubTools(
    val usage: com.medipote.app.data.UsageStats = com.medipote.app.data.UsageStats(),
    val byUse: Boolean = true,
    val pins: List<String> = emptyList(),
    val onOpened: (String) -> Unit = {},
    val onTogglePin: (String) -> Unit = {},
    val onKeep: (String) -> Unit = {},
    val onPutAway: (String) -> Unit = {},
)

/** A card as the "Mes cartes" page lists it. */
data class CardDef(val key: String, val emoji: String, val label: String)

/** Every card of every hub page, so "Mes cartes" can list them without opening the pages. */
object HubCatalog {
    const val SOINS = "Soins"
    const val SUIVI = "Suivi"
    const val MIEUX = "Mieux-être"
    const val MOI = "Moi"

    /** Every card of the app, whoever you are: what "ce qui compte pour toi" starts from. */
    val EVERYTHING: List<CardDef> = listOf(
        CardDef("soins:treatments", "💊", "Traitements"), CardDef("soins:pill", "🔁", "Pilule"), CardDef("soins:agenda", "📅", "Agenda"),
        CardDef("soins:docs", "📂", "Carnet de santé"),
        CardDef("suivi:journal", "📖", "Journal"), CardDef("suivi:calendar", "🗓️", "Calendrier"), CardDef("suivi:labs", "🧪", "Analyses"), CardDef("suivi:timeline", "🕰️", "Ma période"), CardDef("suivi:checkup", "🔎", "Comprendre"), CardDef("suivi:sleep", "🌙", "Sommeil"), CardDef("suivi:measures", "🩺", "Mesures"),
        CardDef("suivi:cycle", "🩸", "Cycle"), CardDef("suivi:symptoms", "🌡️", "Symptômes"),
        CardDef("mieux:softday", "💛", "Journée douce"), CardDef("mieux:feel", "💭", "Ce que je ressens"),
        CardDef("mieux:rituals", "✨", "Rituels du matin et du soir"), CardDef("mieux:me", "🌿", "Ce qui m'accompagne"),
        CardDef("moi:stats", "📊", "Statistiques"), CardDef("moi:year", "📆", "Mon année"), CardDef("moi:shop", "🛍️", "Boutique"),
        CardDef("moi:badges", "🏅", "Badges et défis"),
    )

    /** Hidden by the person, plus what sober mode puts away (the shop and the badges). */
    fun hidden(settings: com.medipote.app.data.Settings): Set<String> =
        if (settings.sober) settings.hiddenCards + setOf("moi:shop", "moi:badges") else settings.hiddenCards

    /** The cards that make sense for this person, page by page. */
    fun cards(state: AppState): List<Pair<String, List<CardDef>>> {
        val menstruating = state.profile?.menstruating == true
        val hasPill = state.treatments.any { it.pill != null }
        return listOf(
            SOINS to listOfNotNull(
                CardDef("soins:treatments", "💊", "Traitements"),
                CardDef("soins:pill", "🔁", "Pilule").takeIf { menstruating || hasPill },
                CardDef("soins:agenda", "📅", "Agenda"),
                CardDef("soins:docs", "📂", "Carnet de santé"),
            ),
            SUIVI to listOfNotNull(
                CardDef("suivi:journal", "📖", "Journal"),
                CardDef("suivi:calendar", "🗓️", "Calendrier"),
                CardDef("suivi:labs", "🧪", "Analyses"),
                CardDef("suivi:timeline", "🕰️", "Ma période"),
                CardDef("suivi:checkup", "🔎", "Comprendre"),
                CardDef("suivi:sleep", "🌙", "Sommeil"),
                CardDef("suivi:measures", "🩺", "Mesures").takeIf { state.settings.measures.isNotEmpty() },
                CardDef("suivi:cycle", "🩸", "Cycle").takeIf { menstruating },
                CardDef("suivi:symptoms", "🌡️", "Symptômes").takeIf { Wellbeing.enabled(state) },
            ),
            MIEUX to listOfNotNull(
                CardDef("mieux:softday", "💛", "Journée douce").takeIf { Wellbeing.enabled(state) },
                CardDef("mieux:feel", "💭", "Ce que je ressens"),
                CardDef("mieux:rituals", "✨", "Rituels du matin et du soir"),
                CardDef("mieux:me", "🌿", "Ce qui m'accompagne"),
            ),
            MOI to listOf(
                CardDef("moi:stats", "📊", "Statistiques"),
                CardDef("moi:year", "📆", "Mon année"),
                CardDef("moi:shop", "🛍️", "Boutique"),
                CardDef("moi:badges", "🏅", "Badges et défis"),
            ),
        )
    }
}

/** The short line shown under a card's name. */
object HubPreviews {
    fun treatments(state: AppState, now: LocalDateTime): String {
        val today = now.toLocalDate()
        val active = state.treatments.filter { it.endDate == null || !it.endDate.isBefore(today) }
        val low = active.filter { it.isLowStock }
        return when {
            low.isNotEmpty() -> "À racheter : ${low.joinToString(", ") { it.name }}"
            active.isEmpty() -> "Aucun traitement pour l'instant"
            else -> "${active.size} en cours"
        }
    }

    fun agenda(state: AppState, now: LocalDateTime): String {
        val next = state.appointments.filter { !it.dateTime.isBefore(now) }.minByOrNull { it.dateTime }
            ?: return "Aucun rendez-vous à venir"
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), next.dateTime.toLocalDate())
        return "${next.title} · " + when (days) {
            0L -> "aujourd'hui"
            1L -> "demain"
            else -> "dans $days jours"
        }
    }

    fun documents(state: AppState): String = state.documents.size.let { if (it == 0) "Ordonnances, résultats, comptes rendus" else "$it document${if (it > 1) "s" else ""}" }

    fun journal(state: AppState, now: LocalDateTime): String =
        if (state.entryFor(now.toLocalDate()).hasJournal) "Noté aujourd'hui" else "Pas encore noté aujourd'hui"

    fun sleep(state: AppState, now: LocalDateTime): String =
        state.sleep.firstOrNull { it.date == now.toLocalDate() }?.let { "Cette nuit : ${Sleep.label(it)}" } ?: "Nuit pas encore notée"

    fun timeline(state: AppState, today: java.time.LocalDate): String {
        val events = com.medipote.app.domain.Timeline.events(state, today.minusDays(89), today)
        return if (events.isEmpty()) "Traitements, symptômes, sommeil et analyses, sur une ligne" else com.medipote.app.domain.Timeline.summary(events)
    }

    fun labs(state: AppState): String {
        val last = state.labs.maxByOrNull { it.date } ?: return "Tes prises de sang, courbe par courbe"
        return "Dernière : ${com.medipote.app.domain.Labs.name(last.marker)} ${com.medipote.app.domain.Labs.format(last.value, last.marker)} ${last.unit}"
    }

    fun checkup(state: AppState, today: java.time.LocalDate): String {
        val n = com.medipote.app.domain.Understand.visible(com.medipote.app.domain.Understand.scan(state, today), state.settings.dismissedChecks, today).size
        return if (n == 0) "Rien d'inhabituel dans tes notes" else "$n chose${if (n > 1) "s" else ""} à regarder"
    }

    fun measures(state: AppState): String = state.settings.measures.joinToString(", ") { it.label.lowercase() }.ifEmpty { "Tension, poids, pouls…" }

    fun symptoms(state: AppState, now: LocalDateTime): String {
        val notes = Wellbeing.notesOn(state, now.toLocalDate())
        if (notes.isEmpty()) return "Rien de noté aujourd'hui"
        return notes.take(2).joinToString(" · ") { "${Wellbeing.name(it.symptom).lowercase()} ${it.intensity}/5" }
    }

    fun softDay(state: AppState, now: LocalDateTime): String {
        val n = Wellbeing.winsOn(state, now.toLocalDate()).size
        return if (n == 0) "Des petites missions et des tâches en petites étapes" else "$n petite${if (n > 1) "s" else ""} victoire${if (n > 1) "s" else ""} aujourd'hui"
    }

    fun me(state: AppState): String {
        val w = state.settings.wellbeing
        return if (w.enabled && w.conditions.isNotEmpty()) w.conditions.joinToString(", ") { it.name }
        else "Ce que je vis, mes symptômes, mes missions"
    }
}

/**
 * A top-level space shown as an airy page of cards, each with a live line under its name. A card opens
 * its content in place (back arrow to the page) or another screen. Cards the user hid are not listed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardHub(
    title: String,
    cards: List<HubCard>,
    hidden: Set<String>,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onChooseCards: () -> Unit,
    intro: (@Composable () -> Unit)? = null,
    /** The magnifying glass and the heart sit in every bar: finding things and asking for help never take a search. */
    onSearch: (() -> Unit)? = null,
    onSupport: (() -> Unit)? = null,
    /** The card opened at once (from the search). It opens even if the person hid it. */
    initialOpen: String? = null,
    tools: HubTools = HubTools(),
    content: @Composable (String) -> Unit,
) {
    val listed = cards.filter { it.key !in hidden }
    val order = com.medipote.app.domain.CardUse.ordered(tools.usage, listed.map { it.key }, tools.byUse)
    val shown = order.mapNotNull { key -> listed.firstOrNull { it.key == key } }
    val suggestion = com.medipote.app.domain.CardUse.suggestion(tools.usage, listed.map { it.key })?.let { key -> cards.firstOrNull { it.key == key } }
    var open by rememberSaveable { mutableStateOf(initialOpen) }
    androidx.compose.runtime.LaunchedEffect(initialOpen) { initialOpen?.let(tools.onOpened) }
    val current = cards.firstOrNull { it.key == open && it.onClick == null && (it.key !in hidden || it.key == initialOpen) }
    BackHandler(enabled = current != null) { open = null }
    Scaffold(
        topBar = {
            TopAppBar(
                // Where you are, always: "Sommeil" with the tab's name under it.
                title = {
                    if (current == null) Text(title) else Column {
                        Text(current.label)
                        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (current != null) IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                },
                actions = {
                    onSupport?.let { IconButton(onClick = it) { Icon(Icons.Outlined.FavoriteBorder, "Ça ne va pas : soutien") } }
                    onSearch?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Search, "Chercher dans l'appli") } }
                    IconButton(onClick = onOpenProfile) { Icon(Icons.Outlined.AccountCircle, "Profil médical") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Paramètres") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (current != null) {
                content(current.key)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (intro != null) item { intro() }
                    suggestion?.let { card ->
                        item(key = "suggestion") { PutAwayOffer(card, onPutAway = { tools.onPutAway(card.key) }, onKeep = { tools.onKeep(card.key) }) }
                    }
                    items(shown, key = { it.key }) { card ->
                        CardRow(
                            card, pinned = card.key in tools.pins, onPin = { tools.onTogglePin(card.key) },
                        ) {
                            tools.onOpened(card.key)
                            if (card.onClick != null) card.onClick.invoke() else open = card.key
                        }
                    }
                    item {
                        TextButton(onClick = onChooseCards, modifier = Modifier.fillMaxWidth()) { Text("✏️  Choisir mes cartes") }
                        if (tools.pins.isEmpty()) {
                            Text(
                                "Astuce : un appui long sur une carte l'épingle sur l'accueil.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CardRow(card: HubCard, pinned: Boolean, onPin: () -> Unit, onClick: () -> Unit) {
    var menu by androidx.compose.runtime.remember { mutableStateOf(false) }
    val pinLabel = if (pinned) "Retirer de l'accueil" else "Épingler sur l'accueil"
    Box {
        Card(
            shape = CardDefaults.shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .semantics { customActions = listOf(CustomAccessibilityAction(pinLabel) { onPin(); true }) },
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(card.emoji, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(card.label, style = MaterialTheme.typography.titleMedium)
                    card.preview?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pinned) Text("📌", style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("📌  $pinLabel") },
                onClick = { menu = false; onPin() },
            )
        }
    }
}

/** « Tu n'ouvres jamais X : on le range ? », once enough days have passed; the person always decides. */
@Composable
private fun PutAwayOffer(card: HubCard, onPutAway: () -> Unit, onKeep: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tu n'ouvres jamais « ${card.label} » : on la range ?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Elle ne disparaît pas : tu la retrouves avec la loupe et dans « Choisir mes cartes ».",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(onClick = onPutAway) { Text("Ranger") }
                TextButton(onClick = onKeep) { Text("Garder") }
            }
        }
    }
}

/** What matters most to the person: it decides which cards start visible (the rest is one tap away in "Mes cartes"). */
enum class Interest(val emoji: String, val label: String) {
    TREATMENTS("💊", "Mes traitements"),
    CYCLE("🩸", "Mon cycle"),
    MENTAL("💛", "Ma santé mentale"),
    SLEEP("🌙", "Mon sommeil"),
    ;

    companion object {
        private val ALWAYS = setOf("soins:treatments", "soins:agenda", "suivi:journal", "suivi:calendar", "suivi:checkup", "suivi:timeline", "moi:stats", "moi:year", "moi:shop", "moi:badges")
        private val COVERS = mapOf(
            TREATMENTS to setOf("soins:pill", "soins:docs", "suivi:measures", "suivi:labs"),
            CYCLE to setOf("suivi:cycle", "soins:pill"),
            MENTAL to setOf("mieux:softday", "mieux:feel", "mieux:rituals", "mieux:me", "suivi:symptoms"),
            SLEEP to setOf("suivi:sleep"),
        )

        /** The cards to hide for someone who chose [chosen]; nothing chosen, or everything, hides nothing. */
        fun hiddenFor(chosen: Set<Interest>): Set<String> {
            if (chosen.isEmpty() || chosen.size == entries.size) return emptySet()
            val visible = ALWAYS + chosen.flatMap { COVERS.getValue(it) }
            return HubCatalog.EVERYTHING.map { it.key }.filter { it !in visible }.toSet()
        }
    }
}
