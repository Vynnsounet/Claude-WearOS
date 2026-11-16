package com.medipote.app.ui.motivation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medipote.app.data.AppState
import com.medipote.app.domain.Recaps
import com.medipote.app.domain.Sleep
import com.medipote.app.domain.WeeklyRecap
import com.medipote.app.ui.components.capitalized
import com.medipote.app.ui.components.dayMonth
import com.medipote.app.ui.components.fullDay
import com.medipote.app.ui.pet.PetView
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class RecapCard(val emoji: String, val title: String, val big: String, val lines: List<String>, val colors: List<Color>)

private fun cards(r: WeeklyRecap): List<RecapCard> = buildList {
    add(
        RecapCard(
            "🎁", "Ta semaine", "${r.from.format(dayMonth)} – ${r.to.format(dayMonth)}",
            listOf("${r.pointsEarned} points gagnés", "Glisse pour découvrir ta semaine →"),
            listOf(Color(0xFF7E57C2), Color(0xFF26A69A)),
        ),
    )
    if (r.dosesPlanned > 0) {
        add(
            RecapCard(
                "💊", "Tes prises", "${r.dosesTaken} / ${r.dosesPlanned}",
                listOfNotNull(
                    r.rate?.let { "${(it * 100).roundToInt()} % des prises faites" },
                    "${r.onTime} à l'heure",
                    if (r.perfectDays > 0) "${r.perfectDays} journée${if (r.perfectDays > 1) "s" else ""} parfaite${if (r.perfectDays > 1) "s" else ""} 🌟" else null,
                ),
                listOf(Color(0xFF1F7F69), Color(0xFF43A047)),
            ),
        )
    }
    if (r.nights > 0) {
        add(
            RecapCard(
                "🌙", "Tes nuits", r.averageSleep?.let(Sleep::format) ?: "—",
                listOfNotNull(
                    "en moyenne sur ${r.nights} nuit${if (r.nights > 1) "s" else ""} notée${if (r.nights > 1) "s" else ""}",
                    r.bestNight?.let { "Plus longue nuit : ${Sleep.format(it)}" },
                ),
                listOf(Color(0xFF283593), Color(0xFF5C6BC0)),
            ),
        )
    }
    if (r.moodAverage != null || r.journalDays > 0) {
        add(
            RecapCard(
                "😊", "Ton humeur", r.moodAverage?.let { FACES[(it.roundToInt() - 1).coerceIn(0, 4)] } ?: "📖",
                listOfNotNull(
                    r.moodAverage?.let { "Humeur moyenne : ${MOOD_LABELS[(it.roundToInt() - 1).coerceIn(0, 4)].lowercase()}" },
                    r.bestDay?.let { "Ton meilleur jour : ${it.format(fullDay)}" },
                    "${r.journalDays} jour${if (r.journalDays > 1) "s" else ""} de journal",
                ),
                listOf(Color(0xFFEF6C00), Color(0xFFFFB74D)),
            ),
        )
    }
    if (r.symptomDays > 0) {
        add(
            RecapCard(
                "🌡️", "Tes symptômes", "${r.symptomDays} jour${if (r.symptomDays > 1) "s" else ""} noté${if (r.symptomDays > 1) "s" else ""}",
                listOfNotNull(
                    r.topSymptom?.let { (id, avg) -> "Le plus présent : ${com.medipote.app.domain.Wellbeing.name(id).lowercase()} (force moyenne ${"%.1f".format(java.util.Locale.FRANCE, avg)}/5)" },
                    if (r.strongSymptomDays > 0) "${r.strongSymptomDays} jour${if (r.strongSymptomDays > 1) "s" else ""} plus difficile${if (r.strongSymptomDays > 1) "s" else ""}" else "Pas de jour très difficile 🌿",
                    "Le détail est dans Moi › Statistiques",
                ),
                listOf(Color(0xFF00796B), Color(0xFF4DB6AC)),
            ),
        )
    }
    if (r.wins > 0) {
        add(
            RecapCard(
                "💛", "Tes petites victoires", "${r.wins}",
                listOfNotNull(
                    "sur ${r.winDays} jour${if (r.winDays > 1) "s" else ""}",
                    if (r.winPoints > 0) "+${r.winPoints} points" else null,
                    "Chacune compte, même les toutes petites.",
                ),
                listOf(Color(0xFF8F5400), Color(0xFFB87100)),
            ),
        )
    }
    add(
        RecapCard(
            "🏅", "Tes réussites",
            if (r.badges.isEmpty()) "${r.challenges} défi${if (r.challenges > 1) "s" else ""}" else r.badges.joinToString(" ") { it.emoji },
            listOfNotNull(
                if (r.badges.isNotEmpty()) "Nouveau${if (r.badges.size > 1) "x" else ""} badge${if (r.badges.size > 1) "s" else ""} : ${r.badges.joinToString(", ") { it.title }}" else null,
                "${r.challenges} défi${if (r.challenges > 1) "s" else ""} de la semaine réussi${if (r.challenges > 1) "s" else ""}",
                if (r.waterGoalDays > 0) "Objectif d'eau atteint ${r.waterGoalDays} jour${if (r.waterGoalDays > 1) "s" else ""} 💧" else null,
            ),
            listOf(Color(0xFFC2185B), Color(0xFFF06292)),
        ),
    )
}

/** The weekly recap, card by card, ending with a word from the companion. */
@Composable
fun RecapScreen(state: AppState, week: LocalDate, now: LocalDateTime, onSeen: () -> Unit, onClose: () -> Unit, animate: Boolean = true) {
    val recap = remember(state, week) { Recaps.build(state, week, now) }
    val cards = remember(recap) { cards(recap) }
    val pager = rememberPagerState { cards.size + 1 }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onSeen() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page < cards.size) {
                val c = cards[page]
                Column(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(c.colors)).statusBarsPadding().navigationBarsPadding().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(c.emoji, style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(c.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    Text(c.big, style = MaterialTheme.typography.displaySmall, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    c.lines.forEach { Text(it.capitalized(), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(180.dp).clip(MaterialTheme.shapes.extraLarge)) {
                        PetView(
                            com.medipote.app.domain.PetMood.HAPPY, state.game.equipped, Modifier.fillMaxSize(), animate = animate,
                            stage = com.medipote.app.domain.Evolution.growth(state).stage,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(recap.petMessage, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth(0.8f).height(52.dp)) { Text("À la semaine prochaine !") }
                }
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(cards.size + 1) { i ->
                Box(
                    Modifier.size(width = if (i == pager.currentPage) 22.dp else 8.dp, height = 8.dp).clip(CircleShape)
                        .background(
                            (if (pager.currentPage < cards.size) Color.White else MaterialTheme.colorScheme.primary)
                                .copy(alpha = if (i == pager.currentPage) 0.95f else 0.45f),
                        ),
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()) {
            Icon(Icons.Rounded.Close, "Fermer", tint = if (pager.currentPage < cards.size) Color.White else MaterialTheme.colorScheme.onBackground)
        }
        if (pager.currentPage < cards.size) {
            // Like a story: tapping the right side goes to the next card.
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight(0.7f).width(110.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                    .semantics { contentDescription = "Carte suivante" },
            )
        }
    }
}
