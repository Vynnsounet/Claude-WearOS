package com.medipote.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medipote.app.domain.CheckInReward
import com.medipote.app.domain.Rules
import com.medipote.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The words that go with the streak: always encouraging, never guilt-inducing. */
fun streakMessage(r: CheckInReward, pet: String): Pair<String, String> = when {
    r.milestoneBonus > 0 -> "Palier atteint ! 🏆" to "${r.streak} jours d'affilée : +${r.milestoneBonus} points bonus. $pet est aux anges !"
    r.streak == 1 && r.previousStreak > 1 -> "Nouveau départ 💪" to "Ta série précédente a duré ${r.previousStreak} jours (record : ${r.best}). On repart ensemble, un jour à la fois."
    r.streak == 1 -> "C'est parti !" to "Premier jour de ta série. Reviens demain pour la faire grandir avec $pet."
    r.streak < 7 -> "Ça continue ! ✨" to "$pet adore te voir chaque jour. Encore un peu et tu tiens une semaine entière !"
    r.streak < 30 -> "Quelle régularité ! 🌟" to "Tu prends soin de toi jour après jour, et ça se voit."
    r.streak < 100 -> "Incroyable constance 🔥" to "Plus d'un mois sans t'arrêter : bravo, vraiment."
    else -> "Légendaire 👑" to "${r.streak} jours. $pet n'en revient pas !"
}

/** Next milestone and how far along the way to it we are (0..1). */
fun nextMilestone(streak: Int): Triple<Int, Int, Float>? {
    val keys = Rules.STREAK_MILESTONES.keys.sorted()
    val next = keys.firstOrNull { it > streak } ?: return null
    val previous = keys.lastOrNull { it <= streak } ?: 0
    return Triple(next, Rules.STREAK_MILESTONES.getValue(next), (streak - previous).toFloat() / (next - previous))
}

/**
 * First opening of the day: a flame that grows, the streak counted up, the last 7 days lit,
 * confetti, and the next milestone.
 */
@Composable
fun StreakCelebration(reward: CheckInReward, pet: String, today: LocalDate, animate: Boolean = true, onDismiss: () -> Unit) {
    val flameIn = remember { Animatable(if (animate) 0.2f else 1f) }
    val count = remember { Animatable(if (animate) 0f else reward.streak.toFloat()) }
    val points = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        flameIn.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        count.animateTo(reward.streak.toFloat(), tween(durationMillis = (500 + reward.streak * 25).coerceAtMost(1400), easing = FastOutSlowInEasing))
        points.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    val loop = rememberInfiniteTransition(label = "streak")
    val flicker by if (animate) loop.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "flicker")
    else remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
    val fall by if (animate) loop.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "fall")
    else remember { androidx.compose.runtime.mutableFloatStateOf(0.35f) }

    val status = AppTheme.status
    val colors = MaterialTheme.colorScheme
    val confettiColors = listOf(colors.primary, colors.tertiary, status.gold, status.streak, colors.secondary, Color(0xFFEC407A))
    val confetti = remember { List(46) { Confetto(Random(it * 31 + 7)) } }
    val (title, message) = streakMessage(reward, pet)
    val milestone = nextMilestone(reward.streak)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.background, colors.background)))
                .semantics { contentDescription = "Série de ${reward.streak} jour${if (reward.streak > 1) "s" else ""}. $title $message" },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                confetti.forEachIndexed { i, c -> drawConfetto(c, fall, confettiColors[i % confettiColors.size]) }
            }
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(190.dp).scale(flameIn.value), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawFlame(flicker, status.streak, status.gold) }
                    Text(
                        count.value.roundToInt().toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            shadow = androidx.compose.ui.graphics.Shadow(Color(0x99602000), Offset(0f, 4f), 12f),
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(top = 46.dp),
                    )
                }
                Text(
                    if (reward.streak > 1) "jours d'affilée" else "jour",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(18.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(22.dp))
                WeekStrip(reward.streak, today, flicker)
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = colors.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.scale(0.6f + 0.4f * points.value),
                ) {
                    Text(
                        "⭐ +${reward.total} pts",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
                milestone?.let { (days, bonus, progress) ->
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Encore ${days - reward.streak} jour${if (days - reward.streak > 1) "s" else ""} avant le palier de $days jours (+$bonus pts)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress * count.value / reward.streak.coerceAtLeast(1) },
                        color = status.streak,
                        trackColor = status.streak.copy(alpha = 0.2f),
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth(0.8f).height(8.dp).clip(CircleShape),
                    )
                }
                Spacer(Modifier.height(28.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(0.8f).height(52.dp)) {
                    Text(if (reward.streak == 1) "C'est parti !" else "On continue !", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** The last seven days, today on the right; days of the current streak are lit. */
@Composable
private fun WeekStrip(streak: Int, today: LocalDate, pulse: Float) {
    val status = AppTheme.status
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 6 downTo 0) {
            val day = today.minusDays(i.toLong())
            val lit = i < streak
            val isToday = i == 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(34.dp)
                        .scale(if (isToday) 1f + 0.08f * pulse else 1f)
                        .clip(CircleShape)
                        .background(if (lit) status.streak else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (lit) "🔥" else "·", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.FRANCE),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private class Confetto(r: Random) {
    val x = r.nextFloat()
    val offset = r.nextFloat()
    val speed = 0.6f + r.nextFloat() * 0.8f
    val sway = 0.02f + r.nextFloat() * 0.04f
    val size = 5f + r.nextFloat() * 7f
    val spin = r.nextFloat() * 360f
    val round = r.nextBoolean()
}

private fun DrawScope.drawConfetto(c: Confetto, t: Float, color: Color) {
    val progress = (c.offset + t * c.speed) % 1f
    val y = -20f + progress * (size.height + 40f)
    val x = (c.x + sin((progress * 4 + c.offset) * PI).toFloat() * c.sway) * size.width
    val s = c.size * density / 2.5f
    if (c.round) {
        drawCircle(color.copy(alpha = 0.85f), s * 0.6f, Offset(x, y))
    } else {
        rotate(c.spin + progress * 540f, Offset(x, y)) {
            drawRect(color.copy(alpha = 0.85f), Offset(x - s / 2, y - s / 4), Size(s, s / 2))
        }
    }
}

/** A flame: an outer orange teardrop, an inner golden one, flickering gently. */
private fun DrawScope.drawFlame(flicker: Float, outer: Color, inner: Color) {
    val w = size.width
    val h = size.height
    fun flame(scale: Float, color: Brush, lean: Float) {
        val cx = w / 2
        val bottom = h * 0.95f
        val top = h * (1 - 0.92f * scale)
        val half = w * 0.36f * scale
        val path = Path().apply {
            moveTo(cx + lean, top)
            cubicTo(cx + half * 0.5f + lean, top + h * 0.25f * scale, cx + half * 1.15f, bottom - h * 0.38f * scale, cx + half, bottom - h * 0.2f * scale)
            cubicTo(cx + half * 0.85f, bottom, cx - half * 0.85f, bottom, cx - half, bottom - h * 0.2f * scale)
            cubicTo(cx - half * 1.15f, bottom - h * 0.38f * scale, cx - half * 0.4f + lean, top + h * 0.3f * scale, cx + lean, top)
            close()
        }
        drawPath(path, color)
    }
    val lean = (flicker - 0.5f) * w * 0.04f
    flame(1f + 0.03f * flicker, Brush.verticalGradient(listOf(inner, outer, outer)), lean)
    flame(0.62f - 0.03f * flicker, Brush.verticalGradient(listOf(Color(0xFFFFF3C4), inner)), -lean)
}
