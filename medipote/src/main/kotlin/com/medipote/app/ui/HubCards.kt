package com.medipote.app.ui

import androidx.activity.compose.BackHandler
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

/** A card as the "Mes cartes" page lists it. */
data class CardDef(val key: String, val emoji: String, val label: String)

/** Every card of every hub page, so "Mes cartes" can list them without opening the pages. */
object HubCatalog {
    const val SOINS = "Soins"
    const val SUIVI = "Suivi"
    const val MIEUX = "Mieux-être"
    const val MOI = "Moi"

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
                CardDef("suivi:sleep", "🌙", "Sommeil"),
                CardDef("suivi:measures", "🩺", "Mesures").takeIf { state.settings.measures.isNotEmpty() },
                CardDef("suivi:cycle", "🩸", "Cycle").takeIf { menstruating },
                CardDef("suivi:symptoms", "🌡️", "Symptômes").takeIf { Wellbeing.enabled(state) },
            ),
            MIEUX to listOfNotNull(
                CardDef("mieux:softday", "💛", "Journée douce").takeIf { Wellbeing.enabled(state) },
                CardDef("mieux:support", "🤗", "Ça ne va pas"),
                CardDef("mieux:breathe", "🌬️", "Respirer"),
                CardDef("mieux:rituals", "✨", "Rituels du matin et du soir"),
                CardDef("mieux:me", "🌿", "Ce qui m'accompagne"),
            ),
            MOI to listOf(
                CardDef("moi:stats", "📊", "Statistiques"),
                CardDef("moi:day", "📋", "Jour par jour"),
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
        state.sleep.firstOrNull { it.date == now.toLocalDate() }?.let { "Cette nuit : ${Sleep.format(it.asleepMinutes)}" } ?: "Nuit pas encore notée"

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
    content: @Composable (String) -> Unit,
) {
    val shown = cards.filter { it.key !in hidden }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val current = shown.firstOrNull { it.key == open && it.onClick == null }
    BackHandler(enabled = current != null) { open = null }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.label ?: title) },
                navigationIcon = {
                    if (current != null) IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                },
                actions = {
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
                    items(shown, key = { it.key }) { card ->
                        CardRow(card) { if (card.onClick != null) card.onClick.invoke() else open = card.key }
                    }
                    item {
                        TextButton(onClick = onChooseCards, modifier = Modifier.fillMaxWidth()) { Text("✏️  Choisir mes cartes") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardRow(card: HubCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
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
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
