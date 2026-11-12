package com.medipote.app.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.medipote.app.domain.PetMood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class BodyPalette(val base: Color, val belly: Color, val shade: Color, val galaxy: Boolean = false, val rainbow: Boolean = false)

private fun palette(colorId: String?): BodyPalette = when (colorId) {
    "color_peach" -> BodyPalette(Color(0xFFFFB38A), Color(0xFFFFE3D1), Color(0xFFE07B4F))
    "color_sky" -> BodyPalette(Color(0xFF7CC4F2), Color(0xFFD3EEFF), Color(0xFF3D8FC9))
    "color_lavender" -> BodyPalette(Color(0xFFB79CF0), Color(0xFFE9DFFF), Color(0xFF7E5CC4))
    "color_lemon" -> BodyPalette(Color(0xFFF5D547), Color(0xFFFFF4B8), Color(0xFFC29E00))
    "color_coral" -> BodyPalette(Color(0xFFFF8A80), Color(0xFFFFD6D1), Color(0xFFD9534F))
    "color_galaxy" -> BodyPalette(Color(0xFF6A55D6), Color(0xFFC9B8FF), Color(0xFF3A2A8C), galaxy = true)
    "color_choco" -> BodyPalette(Color(0xFFB07A52), Color(0xFFEBD3BC), Color(0xFF6F4428))
    "color_pink" -> BodyPalette(Color(0xFFFF9EC7), Color(0xFFFFE2EF), Color(0xFFD9578F))
    "color_night" -> BodyPalette(Color(0xFF5865B8), Color(0xFFB4BCF0), Color(0xFF2F3878))
    "color_rainbow" -> BodyPalette(Color(0xFFFFB3B3), Color(0xFFFFFFFF), Color(0xFF9C7BD1), rainbow = true)
    else -> BodyPalette(Color(0xFF5CC9A7), Color(0xFFB5ECD9), Color(0xFF2E9E83))
}

private val Ink = Color(0xFF1D2B28)
private val Cheek = Color(0xFFF48FB1)
private val SickTint = Color(0xFFA7B59A)

/**
 * The companion, drawn with Canvas so every mood and accessory is resolution independent.
 *
 * @param feedSignal change this value to play the "swallows a pill" animation.
 */
@Composable
fun PetView(
    mood: PetMood,
    equipped: Map<String, String>,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    animate: Boolean = true,
    feedSignal: Int = 0,
    description: String = "",
) {
    val transition = rememberInfiniteTransition(label = "pet")
    val breathe by if (animate) transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (mood == PetMood.SICK) 2600 else 1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    ) else remember { mutableFloat(0.5f) }
    val blink by if (animate) transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes {
            durationMillis = 4200
            0f at 0
            0f at 3900
            1f at 4000
            0f at 4150
        }),
        label = "blink",
    ) else remember { mutableFloat(0f) }
    val loop by if (animate) transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "loop",
    ) else remember { mutableFloat(0.3f) }

    val feed = remember { Animatable(1f) }
    LaunchedEffect(feedSignal) {
        if (feedSignal != 0) {
            feed.snapTo(0f)
            feed.animateTo(1f, tween(1600, easing = LinearEasing))
        }
    }

    Canvas(modifier.semantics { if (description.isNotEmpty()) contentDescription = description }) {
        if (showBackground) drawBackground(equipped["BACKGROUND"])
        drawPet(mood, equipped, breathe, blink, loop, feed.value)
    }
}

private fun mutableFloat(v: Float) = androidx.compose.runtime.mutableFloatStateOf(v)

// ─────────────────────────────── Backgrounds ───────────────────────────────

private fun DrawScope.drawBackground(id: String?) {
    val w = size.width
    val h = size.height
    when (id) {
        "bg_meadow" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF9ED8F5), Color(0xFFDDF3FC))))
            drawCircle(Color(0xFFFFE07A), w * 0.08f, Offset(w * 0.82f, h * 0.17f))
            drawCloud(Offset(w * 0.22f, h * 0.18f), w * 0.07f)
            drawOval(Color(0xFF9CD67C), Offset(-w * 0.3f, h * 0.62f), Size(w * 1.0f, h * 0.6f))
            drawOval(Color(0xFF7CC45E), Offset(w * 0.35f, h * 0.66f), Size(w * 1.0f, h * 0.6f))
            listOf(0.12f to 0.86f, 0.25f to 0.93f, 0.8f to 0.88f, 0.9f to 0.95f, 0.68f to 0.94f).forEach { (x, y) ->
                drawFlower(Offset(w * x, h * y), w * 0.018f)
            }
        }
        "bg_beach" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7FD3F7), Color(0xFFCFF1FF))))
            drawCircle(Color(0xFFFFD54F), w * 0.09f, Offset(w * 0.8f, h * 0.18f))
            drawRect(Color(0xFF3FA9D6), Offset(0f, h * 0.55f), Size(w, h * 0.17f))
            for (i in 0..6) {
                drawArc(Color.White.copy(alpha = 0.6f), 200f, 140f, false,
                    Offset(w * (i / 6f) - w * 0.05f, h * 0.58f), Size(w * 0.1f, h * 0.04f), style = Stroke(w * 0.006f))
            }
            drawRect(Color(0xFFF6DFA4), Offset(0f, h * 0.72f), Size(w, h * 0.28f))
            drawCircle(Color(0xFFE9C77E), w * 0.012f, Offset(w * 0.15f, h * 0.86f))
            drawCircle(Color(0xFFE9C77E), w * 0.01f, Offset(w * 0.85f, h * 0.92f))
            // starfish
            drawStar(Offset(w * 0.86f, h * 0.82f), w * 0.035f, Color(0xFFFF8A65), points = 5, inner = 0.45f)
        }
        "bg_night" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF14204A), Color(0xFF34407A))))
            listOf(0.1f to 0.1f, 0.3f to 0.22f, 0.55f to 0.08f, 0.9f to 0.3f, 0.15f to 0.4f, 0.7f to 0.4f, 0.45f to 0.3f)
                .forEach { (x, y) -> drawStar(Offset(w * x, h * y), w * 0.014f, Color(0xFFFFF3B0), 4, 0.35f) }
            drawCircle(Color(0xFFFFF1C1), w * 0.08f, Offset(w * 0.8f, h * 0.15f))
            drawCircle(Color(0xFF1C2858), w * 0.07f, Offset(w * 0.765f, h * 0.13f))
            drawOval(Color(0xFF263463), Offset(-w * 0.2f, h * 0.74f), Size(w * 1.4f, h * 0.5f))
        }
        "bg_clinic" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFEAF6F6), Color(0xFFD7EEEE))))
            drawRect(Color(0xFFBFDCDC), Offset(0f, h * 0.55f), Size(w, h * 0.04f))
            val cross = Offset(w * 0.17f, h * 0.22f)
            drawRoundRect(Color.White, cross - Offset(w * 0.08f, w * 0.08f), Size(w * 0.16f, w * 0.16f), CornerRadius(w * 0.02f))
            drawRect(Color(0xFF2E9E83), cross - Offset(w * 0.055f, w * 0.018f), Size(w * 0.11f, w * 0.036f))
            drawRect(Color(0xFF2E9E83), cross - Offset(w * 0.018f, w * 0.055f), Size(w * 0.036f, w * 0.11f))
            val chart = Rect(Offset(w * 0.74f, h * 0.12f), Size(w * 0.16f, h * 0.26f))
            drawRoundRect(Color.White, chart.topLeft, chart.size, CornerRadius(w * 0.01f))
            for (i in 0..4) drawLine(Color(0xFF9FB8B8), Offset(chart.left + w * 0.02f, chart.top + h * (0.05f + i * 0.045f)), Offset(chart.right - w * 0.02f - (i % 2) * w * 0.04f, chart.top + h * (0.05f + i * 0.045f)), w * 0.006f)
            drawRect(Color(0xFFCFE3E3), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
            for (i in 0..8) drawLine(Color(0xFFB7D1D1), Offset(w * i / 8f, h * 0.78f), Offset(w * i / 8f, h), w * 0.004f)
        }
        "bg_autumn" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFD7A8), Color(0xFFFFEFD9))))
            listOf(0.12f to Color(0xFFE65100), 0.3f to Color(0xFFFFA000), 0.72f to Color(0xFFD84315), 0.9f to Color(0xFFF9A825)).forEach { (x, col) ->
                drawRect(Color(0xFF795548), Offset(w * x - w * 0.012f, h * 0.38f), Size(w * 0.024f, h * 0.42f))
                drawCircle(col, w * 0.1f, Offset(w * x, h * 0.32f))
                drawCircle(col.copy(alpha = 0.85f), w * 0.075f, Offset(w * x - w * 0.06f, h * 0.38f))
            }
            drawRect(Color(0xFFC98B4E), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
            listOf(0.2f to 0.5f, 0.55f to 0.25f, 0.62f to 0.6f, 0.4f to 0.88f, 0.85f to 0.9f).forEach { (x, y) ->
                drawOval(Color(0xFFE65100), Offset(w * x, h * y), Size(w * 0.03f, w * 0.018f))
            }
        }
        "bg_snow" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFB9CDE0), Color(0xFFE7EFF7))))
            listOf(0.1f, 0.85f).forEach { x ->
                val tree = Path().apply {
                    moveTo(w * x, h * 0.25f); lineTo(w * x + w * 0.09f, h * 0.7f); lineTo(w * x - w * 0.09f, h * 0.7f); close()
                }
                drawPath(tree, Color(0xFF2E7D5B))
                drawPath(Path().apply { moveTo(w * x, h * 0.25f); lineTo(w * x + w * 0.04f, h * 0.42f); lineTo(w * x - w * 0.04f, h * 0.42f); close() }, Color.White)
            }
            drawOval(Color.White, Offset(-w * 0.2f, h * 0.7f), Size(w * 1.4f, h * 0.6f))
            for (i in 0 until 24) {
                val x = ((i * 41) % 100) / 100f
                val y = ((i * 23) % 70) / 100f
                drawCircle(Color.White.copy(alpha = 0.9f), w * 0.007f, Offset(w * x, h * y))
            }
        }
        "bg_rainbow" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFDDF1FF), Color(0xFFFDF3FF))))
            val colors = listOf(Color(0xFFFF8A80), Color(0xFFFFCC80), Color(0xFFFFF59D), Color(0xFFA5D6A7), Color(0xFF90CAF9), Color(0xFFCE93D8))
            colors.forEachIndexed { i, c ->
                val r = w * (0.55f - i * 0.04f)
                drawArc(c, 180f, 180f, false, Offset(w / 2 - r, h * 0.78f - r), Size(r * 2, r * 2), style = Stroke(w * 0.04f))
            }
            drawCloud(Offset(w * 0.1f, h * 0.72f), w * 0.07f)
            drawCloud(Offset(w * 0.9f, h * 0.72f), w * 0.07f)
            drawRect(Color(0xFFB9E4A8), Offset(0f, h * 0.8f), Size(w, h * 0.2f))
        }
        "bg_city" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1F3D), Color(0xFF3B3266))))
            drawCircle(Color(0xFFFFF3C4), w * 0.05f, Offset(w * 0.85f, h * 0.14f))
            val buildings = listOf(0.0f to 0.45f, 0.12f to 0.3f, 0.22f to 0.5f, 0.66f to 0.38f, 0.78f to 0.55f, 0.9f to 0.35f)
            buildings.forEachIndexed { i, (x, top) ->
                val bwid = w * 0.12f
                drawRect(if (i % 2 == 0) Color(0xFF2A2F55) else Color(0xFF353A66), Offset(w * x, h * top), Size(bwid, h * (1 - top)))
                for (r in 0 until 6) for (c in 0..1) {
                    if ((i + r + c) % 3 != 0) drawRect(Color(0xFFFFE082).copy(alpha = 0.85f), Offset(w * x + bwid * (0.2f + c * 0.42f), h * (top + 0.05f + r * 0.07f)), Size(bwid * 0.2f, h * 0.03f))
                }
            }
            drawRect(Color(0xFF22264A), Offset(0f, h * 0.82f), Size(w, h * 0.18f))
        }
        "bg_ocean" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0277BD))))
            for (i in 0..4) {
                val x = w * (0.08f + i * 0.22f)
                drawPath(Path().apply {
                    moveTo(x, h)
                    quadraticTo(x - w * 0.04f, h * 0.8f, x + w * 0.01f, h * 0.65f)
                    quadraticTo(x + w * 0.05f, h * 0.8f, x + w * 0.02f, h)
                    close()
                }, if (i % 2 == 0) Color(0xFF2E7D32) else Color(0xFF43A047))
            }
            drawRect(Color(0xFFF2D59B), Offset(0f, h * 0.86f), Size(w, h * 0.14f))
            listOf(0.15f to 0.3f, 0.2f to 0.2f, 0.82f to 0.4f, 0.86f to 0.28f, 0.6f to 0.15f).forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.55f), w * 0.015f, Offset(w * x, h * y), style = Stroke(w * 0.004f))
            }
            val fish = Offset(w * 0.8f, h * 0.6f)
            drawOval(Color(0xFFFF8A65), fish - Offset(w * 0.04f, w * 0.022f), Size(w * 0.08f, w * 0.044f))
            drawPath(Path().apply { moveTo(fish.x + w * 0.035f, fish.y); lineTo(fish.x + w * 0.07f, fish.y - w * 0.025f); lineTo(fish.x + w * 0.07f, fish.y + w * 0.025f); close() }, Color(0xFFFF7043))
        }
        "bg_space" -> {
            drawRect(Brush.radialGradient(listOf(Color(0xFF3A1D6E), Color(0xFF0D0726)), Offset(w * 0.5f, h * 0.4f), w))
            for (i in 0 until 26) {
                val x = ((i * 37) % 100) / 100f
                val y = ((i * 61) % 100) / 100f
                drawCircle(Color.White.copy(alpha = 0.4f + (i % 3) * 0.2f), w * (0.003f + (i % 3) * 0.002f), Offset(w * x, h * y))
            }
            val planet = Offset(w * 0.18f, h * 0.2f)
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFFB86B), Color(0xFFE0603E)), planet - Offset(w * 0.07f, w * 0.07f), planet + Offset(w * 0.07f, w * 0.07f)), w * 0.07f, planet)
            rotate(-20f, planet) {
                drawOval(Color(0xFFFFE0B2), planet - Offset(w * 0.12f, w * 0.025f), Size(w * 0.24f, w * 0.05f), style = Stroke(w * 0.008f))
            }
            drawOval(Color(0xFF5E5A7A), Offset(-w * 0.2f, h * 0.78f), Size(w * 1.4f, h * 0.5f))
            drawCircle(Color(0xFF4A4766), w * 0.03f, Offset(w * 0.2f, h * 0.88f))
            drawCircle(Color(0xFF4A4766), w * 0.02f, Offset(w * 0.78f, h * 0.9f))
        }
        else -> { // bg_room
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF6EA), Color(0xFFFCE6CF))))
            // window
            val win = Rect(Offset(w * 0.08f, h * 0.1f), Size(w * 0.26f, h * 0.28f))
            drawRoundRect(Color(0xFFBFE6FA), win.topLeft, win.size, CornerRadius(w * 0.02f))
            drawCloud(Offset(win.center.x - w * 0.02f, win.center.y), w * 0.035f)
            drawRoundRect(Color(0xFFE3B98C), win.topLeft, win.size, CornerRadius(w * 0.02f), style = Stroke(w * 0.012f))
            drawLine(Color(0xFFE3B98C), Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), w * 0.008f)
            drawLine(Color(0xFFE3B98C), Offset(win.left, win.center.y), Offset(win.right, win.center.y), w * 0.008f)
            // frame with a heart
            val frame = Rect(Offset(w * 0.74f, h * 0.14f), Size(w * 0.16f, h * 0.14f))
            drawRoundRect(Color(0xFFFFFFFF), frame.topLeft, frame.size, CornerRadius(w * 0.01f))
            drawRoundRect(Color(0xFFB98A5E), frame.topLeft, frame.size, CornerRadius(w * 0.01f), style = Stroke(w * 0.01f))
            drawHeart(frame.center, w * 0.03f, Color(0xFFFF8A80))
            // floor
            drawRect(Color(0xFFE8C59C), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
            drawLine(Color(0xFFD9B184), Offset(0f, h * 0.78f), Offset(w, h * 0.78f), w * 0.008f)
            drawOval(Color(0xFFF7A7A0).copy(alpha = 0.55f), Offset(w * 0.2f, h * 0.84f), Size(w * 0.6f, h * 0.1f))
            // plant
            val pot = Offset(w * 0.86f, h * 0.74f)
            drawRoundRect(Color(0xFFD87C55), pot - Offset(w * 0.045f, 0f), Size(w * 0.09f, h * 0.08f), CornerRadius(w * 0.01f))
            drawOval(Color(0xFF66BB6A), pot + Offset(-w * 0.07f, -h * 0.09f), Size(w * 0.07f, h * 0.1f))
            drawOval(Color(0xFF4CAF50), pot + Offset(0f, -h * 0.11f), Size(w * 0.07f, h * 0.12f))
        }
    }
}

private fun DrawScope.drawCloud(c: Offset, r: Float) {
    val white = Color.White.copy(alpha = 0.9f)
    drawCircle(white, r, c)
    drawCircle(white, r * 0.8f, c + Offset(r * 0.9f, r * 0.2f))
    drawCircle(white, r * 0.7f, c + Offset(-r * 0.9f, r * 0.25f))
}

private fun DrawScope.drawFlower(c: Offset, r: Float) {
    for (i in 0 until 5) {
        val a = i * 2 * PI / 5
        drawCircle(Color.White, r, c + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()))
    }
    drawCircle(Color(0xFFFFC107), r * 0.7f, c)
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color, points: Int, inner: Float) {
    val path = Path()
    for (i in 0 until points * 2) {
        val rr = if (i % 2 == 0) r else r * inner
        val a = -PI / 2 + i * PI / points
        val p = c + Offset((cos(a) * rr).toFloat(), (sin(a) * rr).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + r)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.1f, c.x - r * 0.9f, c.y - r * 1.4f, c.x, c.y - r * 0.5f)
        cubicTo(c.x + r * 0.9f, c.y - r * 1.4f, c.x + r * 1.6f, c.y - r * 0.1f, c.x, c.y + r)
        close()
    }
    drawPath(path, color)
}

// ─────────────────────────────── The pet ───────────────────────────────

private fun DrawScope.drawPet(
    mood: PetMood,
    equipped: Map<String, String>,
    breathe: Float,
    blink: Float,
    loop: Float,
    feed: Float,
) {
    val s = min(size.width, size.height)
    val cx = size.width / 2
    val feeding = feed < 1f
    // Hop when fed, gentle breathing otherwise; sick pets sway slowly.
    val hop = if (feeding && feed > 0.35f) sin(((feed - 0.35f) / 0.65f) * PI * 2).toFloat().coerceAtLeast(0f) * s * 0.06f else 0f
    val bob = (breathe - 0.5f) * s * (if (mood == PetMood.SICK || mood == PetMood.SLEEPING) 0.006f else 0.015f)
    val squash = (breathe - 0.5f) * 0.03f
    val bw = s * 0.56f * (1 + squash)
    val bh = s * 0.5f * (1 - squash)
    val cy = size.height * 0.6f + bob - hop
    val sway = if (mood == PetMood.SICK) sin(loop * 2 * PI).toFloat() * 3f else 0f

    if (equipped["EFFECT"] == "fx_aura") {
        val glow = 0.85f + 0.15f * sin(loop * 2 * PI).toFloat()
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE082).copy(alpha = 0.75f), Color(0xFFFFE082).copy(alpha = 0f)), Offset(cx, cy), s * 0.48f * glow),
            s * 0.48f * glow, Offset(cx, cy),
        )
    }

    // Shadow on the ground
    drawOval(
        Color.Black.copy(alpha = 0.12f),
        Offset(cx - bw * 0.42f, size.height * 0.6f + bh * 0.47f),
        Size(bw * 0.84f, bh * 0.12f),
    )

    rotate(sway, Offset(cx, cy + bh / 2)) {
        val pal = palette(equipped["COLOR"])
        val tint = when (mood) {
            PetMood.SICK -> 0.55f
            PetMood.UNWELL -> 0.3f
            else -> 0f
        }
        val base = lerp(pal.base, SickTint, tint)
        val belly = lerp(pal.belly, Color(0xFFDDE3D3), tint)
        val shade = lerp(pal.shade, Color(0xFF7D8B72), tint)

        val top = cy - bh / 2
        val bottom = cy + bh / 2
        val hw = bw / 2

        if (equipped["NECK"] == "neck_cape") drawCapeBack(cx, cy, bw, bh, loop)

        // Feet
        drawOval(shade, Offset(cx - bw * 0.3f, bottom - bh * 0.1f), Size(bw * 0.2f, bh * 0.14f))
        drawOval(shade, Offset(cx + bw * 0.1f, bottom - bh * 0.1f), Size(bw * 0.2f, bh * 0.14f))
        // Arms (raised when fed)
        val armLift = if (feeding) bh * 0.15f else 0f
        drawOval(shade, Offset(cx - hw - bw * 0.06f, cy + bh * 0.02f - armLift), Size(bw * 0.16f, bh * 0.24f))
        drawOval(shade, Offset(cx + hw - bw * 0.1f, cy + bh * 0.02f - armLift), Size(bw * 0.16f, bh * 0.24f))

        val body = Path().apply {
            moveTo(cx, top)
            cubicTo(cx + hw * 0.6f, top, cx + hw, cy - bh * 0.25f, cx + hw, cy + bh * 0.08f)
            cubicTo(cx + hw, cy + bh * 0.38f, cx + hw * 0.7f, bottom, cx, bottom)
            cubicTo(cx - hw * 0.7f, bottom, cx - hw, cy + bh * 0.38f, cx - hw, cy + bh * 0.08f)
            cubicTo(cx - hw, cy - bh * 0.25f, cx - hw * 0.6f, top, cx, top)
            close()
        }
        if (pal.galaxy && tint == 0f) {
            drawPath(body, Brush.linearGradient(listOf(Color(0xFF4B3BB8), Color(0xFFB04BD0)), Offset(cx - hw, top), Offset(cx + hw, bottom)))
            listOf(-0.25f to -0.2f, 0.3f to -0.05f, -0.35f to 0.2f, 0.2f to 0.3f, 0.05f to -0.32f).forEach { (x, y) ->
                drawStar(Offset(cx + bw * x, cy + bh * y), s * 0.012f, Color.White.copy(alpha = 0.85f), 4, 0.4f)
            }
        } else if (pal.rainbow && tint == 0f) {
            drawPath(
                body,
                Brush.linearGradient(
                    listOf(Color(0xFFFF8A80), Color(0xFFFFD180), Color(0xFFFFF59D), Color(0xFFA5D6A7), Color(0xFF81D4FA), Color(0xFFB39DDB)),
                    Offset(cx - hw, top), Offset(cx + hw, bottom),
                ),
            )
        } else {
            drawPath(body, Brush.verticalGradient(listOf(lerp(base, Color.White, 0.18f), base), top, bottom))
        }
        drawPath(body, shade.copy(alpha = 0.55f), style = Stroke(s * 0.008f))
        // Belly
        drawOval(belly.copy(alpha = if (pal.galaxy || pal.rainbow) 0.4f else 0.9f), Offset(cx - bw * 0.27f, cy + bh * 0.02f), Size(bw * 0.54f, bh * 0.4f))
        // Shine
        drawOval(Color.White.copy(alpha = 0.35f), Offset(cx - hw * 0.62f, top + bh * 0.12f), Size(bw * 0.13f, bh * 0.08f))

        val hasHat = equipped["HAT"] != null
        if (!hasHat) drawSprout(cx, top, s, sick = mood == PetMood.SICK || mood == PetMood.UNWELL)

        drawFace(mood, cx, cy, bw, bh, s, blink, loop, feed, shade)

        equipped["NECK"]?.let { drawNeck(it, cx, cy, bw, bh, s) }
        equipped["GLASSES"]?.let { drawGlasses(it, cx, cy, bw, bh, s) }
        equipped["HAT"]?.let { drawHat(it, cx, top, bw, s, base, cy, bh) }
        equipped["TOY"]?.let { drawToy(it, cx, cy, bw, bh, s, loop) }

        if (mood == PetMood.SICK && !feeding) drawThermometer(cx, cy, bw, bh, s)
        if ((mood == PetMood.SICK || mood == PetMood.UNWELL) && !feeding) {
            val dropY = top + bh * 0.15f + loop * bh * 0.2f
            drawDrop(Offset(cx + hw * 0.78f, dropY), s * 0.03f, Color(0xFF6EC6FF).copy(alpha = 1f - loop * 0.7f))
        }
    }

    if (mood == PetMood.SLEEPING && !feeding) {
        val z = loop
        drawZ(Offset(cx + s * 0.22f, size.height * 0.35f - z * s * 0.08f), s * 0.05f, 1f - z * 0.6f)
        drawZ(Offset(cx + s * 0.3f, size.height * 0.27f - z * s * 0.06f), s * 0.035f, 0.8f - z * 0.5f)
    }
    if (mood == PetMood.HUNGRY && !feeding) drawPillBubble(cx + s * 0.28f, size.height * 0.27f + (breathe - 0.5f) * s * 0.02f, s)
    if (feeding) drawFeeding(cx, cy, bh, s, feed)
    equipped["EFFECT"]?.let { if (it != "fx_aura") drawEffect(it, cx, cy, bw, bh, s, loop) }
}

private fun DrawScope.drawSprout(cx: Float, top: Float, s: Float, sick: Boolean) {
    val stem = Color(0xFF2E7D5B)
    val leaf = if (sick) Color(0xFF9DAF7E) else Color(0xFF4CAF7A)
    val droop = if (sick) s * 0.03f else 0f
    val tipX = cx + s * 0.015f + droop
    val tipY = top - s * 0.09f + droop
    drawPath(Path().apply {
        moveTo(cx, top + s * 0.01f)
        quadraticTo(cx - s * 0.005f, top - s * 0.05f, tipX, tipY)
    }, stem, style = Stroke(s * 0.012f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(tipX, tipY)
        cubicTo(tipX + s * 0.03f, tipY - s * 0.06f + droop, tipX + s * 0.1f, tipY - s * 0.04f + droop * 2, tipX + s * 0.11f, tipY - s * 0.01f + droop * 2)
        cubicTo(tipX + s * 0.07f, tipY + s * 0.02f + droop, tipX + s * 0.03f, tipY + s * 0.01f, tipX, tipY)
    }, leaf)
    drawPath(Path().apply {
        moveTo(tipX - s * 0.005f, tipY + s * 0.01f)
        cubicTo(tipX - s * 0.03f, tipY - s * 0.04f + droop, tipX - s * 0.08f, tipY - s * 0.02f + droop * 2, tipX - s * 0.09f, tipY + s * 0.005f + droop * 2)
        cubicTo(tipX - s * 0.06f, tipY + s * 0.03f + droop, tipX - s * 0.03f, tipY + s * 0.025f, tipX - s * 0.005f, tipY + s * 0.01f)
    }, lerp(leaf, Color.White, 0.15f))
}

private fun DrawScope.drawFace(
    mood: PetMood, cx: Float, cy: Float, bw: Float, bh: Float, s: Float,
    blink: Float, loop: Float, feed: Float, shade: Color,
) {
    val feeding = feed < 1f
    val eyeY = cy - bh * 0.06f
    val eyeDx = bw * 0.19f
    val rx = s * 0.042f
    val ry = s * 0.052f
    val line = Stroke(s * 0.013f, cap = StrokeCap.Round)

    for (side in listOf(-1f, 1f)) {
        val e = Offset(cx + side * eyeDx, eyeY)
        when {
            feeding || mood == PetMood.HAPPY && blink > 0.5f -> // happy closed eyes ^ ^
                drawArc(Ink, 200f, 140f, false, e - Offset(rx, ry * 0.4f), Size(rx * 2, ry * 1.4f), style = line)
            mood == PetMood.SLEEPING ->
                drawArc(Ink, 20f, 140f, false, e - Offset(rx, ry * 0.9f), Size(rx * 2, ry * 1.2f), style = line)
            blink > 0.5f ->
                drawLine(Ink, e - Offset(rx, 0f), e + Offset(rx, 0f), s * 0.013f, StrokeCap.Round)
            else -> {
                drawOval(Ink, e - Offset(rx, ry), Size(rx * 2, ry * 2))
                drawCircle(Color.White, rx * 0.38f, e + Offset(rx * 0.3f, -ry * 0.38f))
                drawCircle(Color.White.copy(alpha = 0.7f), rx * 0.18f, e + Offset(-rx * 0.35f, ry * 0.35f))
                if (mood == PetMood.UNWELL || mood == PetMood.SICK) {
                    // Heavy eyelids and dark circles
                    val lid = if (mood == PetMood.SICK) 1.15f else 0.8f
                    drawRect(lerp(shade, SickTint, 0.3f), e - Offset(rx * 1.3f, ry * 1.3f), Size(rx * 2.6f, ry * lid))
                    drawLine(Ink, e + Offset(-rx * 1.1f, -ry * 1.3f + ry * lid), e + Offset(rx * 1.1f, -ry * 1.3f + ry * lid), s * 0.01f, StrokeCap.Round)
                    drawArc(Color(0xFF6D5A7B).copy(alpha = 0.45f), 20f, 140f, false, e - Offset(rx, -ry * 0.5f), Size(rx * 2, ry), style = Stroke(s * 0.008f))
                }
            }
        }
        // Eyebrows for worried moods
        if (!feeding && (mood == PetMood.UNWELL || mood == PetMood.SICK || mood == PetMood.HUNGRY)) {
            val inner = e + Offset(-side * rx * 0.6f, -ry * 1.65f)
            val outer = e + Offset(side * rx * 1.1f, -ry * 1.35f)
            drawLine(Ink, inner, outer, s * 0.01f, StrokeCap.Round)
        }
    }

    // Cheeks
    if (mood != PetMood.SICK) {
        val a = if (mood == PetMood.UNWELL) 0.25f else 0.55f
        drawOval(Cheek.copy(alpha = a), Offset(cx - eyeDx - bw * 0.14f, eyeY + ry * 1.1f), Size(bw * 0.13f, bh * 0.07f))
        drawOval(Cheek.copy(alpha = a), Offset(cx + eyeDx + bw * 0.01f, eyeY + ry * 1.1f), Size(bw * 0.13f, bh * 0.07f))
    }

    // Mouth
    val m = Offset(cx, cy + bh * 0.1f)
    val mw = bw * 0.1f
    when {
        feeding -> {
            val open = if (feed < 0.35f) feed / 0.35f else 1f - ((feed - 0.35f) / 0.65f).coerceAtMost(1f) * 0.6f
            drawOval(Ink, m - Offset(mw, mw * 0.2f), Size(mw * 2, mw * (0.6f + 1.2f * open)))
            drawOval(Color(0xFFFF8A9A), m + Offset(-mw * 0.5f, mw * (0.15f + 0.6f * open)), Size(mw, mw * 0.5f))
        }
        mood == PetMood.HAPPY -> {
            val p = Path().apply {
                moveTo(m.x - mw, m.y - mw * 0.1f)
                quadraticTo(m.x, m.y + mw * 1.6f, m.x + mw, m.y - mw * 0.1f)
                close()
            }
            drawPath(p, Ink)
            drawOval(Color(0xFFFF8A9A), m + Offset(-mw * 0.45f, mw * 0.35f), Size(mw * 0.9f, mw * 0.4f))
        }
        mood == PetMood.CONTENT ->
            drawArc(Ink, 20f, 140f, false, m - Offset(mw * 0.8f, mw * 0.8f), Size(mw * 1.6f, mw * 1.2f), style = Stroke(s * 0.012f, cap = StrokeCap.Round))
        mood == PetMood.HUNGRY || mood == PetMood.SLEEPING ->
            drawOval(Ink, m - Offset(mw * 0.35f, 0f), Size(mw * 0.7f, mw * (if (mood == PetMood.SLEEPING) 0.4f else 0.8f)))
        mood == PetMood.UNWELL -> {
            val p = Path().apply {
                moveTo(m.x - mw, m.y + mw * 0.3f)
                quadraticTo(m.x - mw * 0.5f, m.y, m.x, m.y + mw * 0.3f)
                quadraticTo(m.x + mw * 0.5f, m.y + mw * 0.6f, m.x + mw, m.y + mw * 0.3f)
            }
            drawPath(p, Ink, style = Stroke(s * 0.011f, cap = StrokeCap.Round))
        }
        else -> // SICK: frown
            drawArc(Ink, 200f, 140f, false, m - Offset(mw * 0.8f, -mw * 0.2f), Size(mw * 1.6f, mw * 1.2f), style = Stroke(s * 0.012f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawThermometer(cx: Float, cy: Float, bw: Float, bh: Float, s: Float) {
    val start = Offset(cx + bw * 0.04f, cy + bh * 0.15f)
    rotate(25f, start) {
        drawRoundRect(Color.White, start, Size(s * 0.14f, s * 0.028f), CornerRadius(s * 0.014f))
        drawRoundRect(Color(0xFF9E9E9E), start, Size(s * 0.14f, s * 0.028f), CornerRadius(s * 0.014f), style = Stroke(s * 0.004f))
        drawRoundRect(Color(0xFFE53935), start + Offset(s * 0.06f, s * 0.009f), Size(s * 0.07f, s * 0.01f), CornerRadius(s * 0.005f))
        drawCircle(Color(0xFFE53935), s * 0.018f, start + Offset(s * 0.14f, s * 0.014f))
    }
}

private fun DrawScope.drawDrop(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r * 1.6f)
        cubicTo(c.x + r * 0.2f, c.y - r * 0.8f, c.x + r, c.y - r * 0.2f, c.x + r, c.y + r * 0.3f)
        cubicTo(c.x + r, c.y + r * 1.0f, c.x - r, c.y + r * 1.0f, c.x - r, c.y + r * 0.3f)
        cubicTo(c.x - r, c.y - r * 0.2f, c.x - r * 0.2f, c.y - r * 0.8f, c.x, c.y - r * 1.6f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawZ(c: Offset, r: Float, alpha: Float) {
    val col = Color(0xFF5C6BC0).copy(alpha = alpha.coerceIn(0f, 1f))
    val p = Path().apply {
        moveTo(c.x - r / 2, c.y - r / 2)
        lineTo(c.x + r / 2, c.y - r / 2)
        lineTo(c.x - r / 2, c.y + r / 2)
        lineTo(c.x + r / 2, c.y + r / 2)
    }
    drawPath(p, col, style = Stroke(r * 0.22f, cap = StrokeCap.Round))
}

private fun DrawScope.drawPill(c: Offset, len: Float, angle: Float) {
    rotate(angle, c) {
        val h = len * 0.42f
        drawRoundRect(Color.White, Offset(c.x - len / 2, c.y - h / 2), Size(len, h), CornerRadius(h / 2))
        drawPath(Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset(c.x - len / 2, c.y - h / 2), Size(len / 2, h)), topLeft = CornerRadius(h / 2), bottomLeft = CornerRadius(h / 2)))
        }, Color(0xFFFF7A6B))
        drawRoundRect(Color(0xFF8D6E63).copy(alpha = 0.4f), Offset(c.x - len / 2, c.y - h / 2), Size(len, h), CornerRadius(h / 2), style = Stroke(len * 0.04f))
    }
}

private fun DrawScope.drawPillBubble(x: Float, y: Float, s: Float) {
    val r = s * 0.085f
    drawCircle(Color.White, r, Offset(x, y))
    drawCircle(Color(0xFFB0BEC5), r, Offset(x, y), style = Stroke(s * 0.005f))
    drawCircle(Color.White, r * 0.25f, Offset(x - r * 0.9f, y + r * 1.05f))
    drawCircle(Color.White, r * 0.13f, Offset(x - r * 1.35f, y + r * 1.5f))
    drawPill(Offset(x, y), r * 1.3f, -35f)
}

private fun DrawScope.drawFeeding(cx: Float, cy: Float, bh: Float, s: Float, feed: Float) {
    val mouth = Offset(cx, cy + bh * 0.12f)
    if (feed < 0.35f) {
        val t = feed / 0.35f
        val start = Offset(cx + s * 0.35f, size.height * 0.98f)
        val p = Offset(
            start.x + (mouth.x - start.x) * t,
            start.y + (mouth.y - start.y) * t - sin(t * PI).toFloat() * s * 0.25f,
        )
        drawPill(p, s * 0.1f * (1f - t * 0.4f), t * 360f)
    } else {
        val t = (feed - 0.35f) / 0.65f
        val alpha = (1f - t).coerceIn(0f, 1f)
        listOf(-0.22f to 0f, 0.2f to 0.15f, 0.02f to 0.3f).forEach { (dx, delay) ->
            val tt = (t - delay).coerceIn(0f, 1f)
            drawHeart(Offset(cx + s * dx, cy - bh * 0.55f - tt * s * 0.2f), s * 0.035f, Color(0xFFFF5C7A).copy(alpha = alpha))
        }
        for (i in 0 until 6) {
            val a = i * PI / 3 + t
            val d = s * (0.3f + t * 0.1f)
            drawStar(Offset(cx + (cos(a) * d).toFloat(), cy - bh * 0.1f + (sin(a) * d * 0.8f).toFloat()), s * 0.018f, Color(0xFFFFD54F).copy(alpha = alpha), 4, 0.4f)
        }
    }
}

// ─────────────────────────────── Accessories ───────────────────────────────

private fun DrawScope.drawGlasses(id: String, cx: Float, cy: Float, bw: Float, bh: Float, s: Float) {
    val eyeY = cy - bh * 0.06f
    val dx = bw * 0.19f
    val r = s * 0.075f
    val frame = Color(0xFF37474F)
    when (id) {
        "glasses_round" -> {
            for (side in listOf(-1f, 1f)) {
                drawCircle(Color.White.copy(alpha = 0.18f), r, Offset(cx + side * dx, eyeY))
                drawCircle(frame, r, Offset(cx + side * dx, eyeY), style = Stroke(s * 0.012f))
            }
            drawArc(frame, 200f, 140f, false, Offset(cx - dx + r, eyeY - r * 0.4f), Size(2 * (dx - r), r * 0.6f), style = Stroke(s * 0.01f))
        }
        "glasses_sun" -> {
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * dx, eyeY)
                drawRoundRect(Color(0xFF212121), c - Offset(r * 1.1f, r * 0.8f), Size(r * 2.2f, r * 1.6f), CornerRadius(r * 0.6f))
                drawLine(Color.White.copy(alpha = 0.5f), c + Offset(-r * 0.6f, -r * 0.4f), c + Offset(-r * 0.1f, -r * 0.4f), s * 0.008f, StrokeCap.Round)
            }
            drawLine(Color(0xFF212121), Offset(cx - dx + r, eyeY - r * 0.3f), Offset(cx + dx - r, eyeY - r * 0.3f), s * 0.014f)
        }
        "glasses_heart" -> {
            for (side in listOf(-1f, 1f)) {
                drawHeart(Offset(cx + side * dx, eyeY), r * 0.95f, Color(0xFFFF4F7B).copy(alpha = 0.85f))
            }
            drawLine(Color(0xFFD81B60), Offset(cx - dx + r * 0.8f, eyeY - r * 0.3f), Offset(cx + dx - r * 0.8f, eyeY - r * 0.3f), s * 0.012f)
        }
        "glasses_star" -> {
            for (side in listOf(-1f, 1f)) {
                drawStar(Offset(cx + side * dx, eyeY), r * 1.25f, Color(0xFFFFC107).copy(alpha = 0.85f), 5, 0.5f)
                drawStar(Offset(cx + side * dx, eyeY), r * 0.75f, Color(0xFFFFF59D).copy(alpha = 0.6f), 5, 0.5f)
            }
            drawLine(Color(0xFFFFA000), Offset(cx - dx + r * 0.7f, eyeY - r * 0.2f), Offset(cx + dx - r * 0.7f, eyeY - r * 0.2f), s * 0.012f)
        }
        "glasses_3d" -> {
            val lens = listOf(Color(0xFFE53935), Color(0xFF00BCD4))
            listOf(-1f, 1f).forEachIndexed { i, side ->
                val c = Offset(cx + side * dx, eyeY)
                drawRect(lens[i].copy(alpha = 0.75f), c - Offset(r * 1.05f, r * 0.75f), Size(r * 2.1f, r * 1.5f))
                drawRect(Color.White, c - Offset(r * 1.05f, r * 0.75f), Size(r * 2.1f, r * 1.5f), style = Stroke(s * 0.012f))
            }
            drawLine(Color.White, Offset(cx - dx + r * 1.05f, eyeY - r * 0.3f), Offset(cx + dx - r * 1.05f, eyeY - r * 0.3f), s * 0.014f)
        }
        "glasses_monocle" -> {
            val c = Offset(cx + dx, eyeY)
            val gold = Color(0xFFD4A017)
            drawCircle(Color.White.copy(alpha = 0.2f), r, c)
            drawCircle(gold, r, c, style = Stroke(s * 0.012f))
            drawPath(Path().apply {
                moveTo(c.x + r * 0.7f, c.y + r * 0.7f)
                quadraticTo(c.x + r * 1.4f, c.y + r * 3f, c.x + r * 0.3f, c.y + r * 4f)
            }, gold, style = Stroke(s * 0.006f))
        }
    }
}

private fun DrawScope.drawNeck(id: String, cx: Float, cy: Float, bw: Float, bh: Float, s: Float) {
    val y = cy + bh * 0.3f
    when (id) {
        "neck_bowtie" -> {
            val c = Offset(cx, y)
            val w = s * 0.08f
            val red = Color(0xFFE53935)
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x - w, c.y - w * 0.6f); lineTo(c.x - w, c.y + w * 0.6f); close() }, red)
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x + w, c.y - w * 0.6f); lineTo(c.x + w, c.y + w * 0.6f); close() }, red)
            drawCircle(Color(0xFFB71C1C), w * 0.25f, c)
        }
        "neck_scarf" -> {
            val band = Path().apply {
                moveTo(cx - bw * 0.42f, y - bh * 0.08f)
                quadraticTo(cx, y + bh * 0.04f, cx + bw * 0.42f, y - bh * 0.08f)
                lineTo(cx + bw * 0.4f, y + bh * 0.04f)
                quadraticTo(cx, y + bh * 0.16f, cx - bw * 0.4f, y + bh * 0.04f)
                close()
            }
            drawPath(band, Color(0xFFE57373))
            drawRoundRect(Color(0xFFE57373), Offset(cx + bw * 0.12f, y), Size(bw * 0.1f, bh * 0.28f), CornerRadius(s * 0.01f))
            for (i in 0..2) drawLine(Color.White.copy(alpha = 0.7f), Offset(cx + bw * 0.12f, y + bh * (0.08f + i * 0.07f)), Offset(cx + bw * 0.22f, y + bh * (0.08f + i * 0.07f)), s * 0.008f)
        }
        "neck_stetho" -> {
            val tube = Color(0xFF546E7A)
            drawPath(Path().apply {
                moveTo(cx - bw * 0.3f, y - bh * 0.12f)
                quadraticTo(cx - bw * 0.25f, y + bh * 0.1f, cx, y + bh * 0.08f)
                quadraticTo(cx + bw * 0.22f, y + bh * 0.06f, cx + bw * 0.18f, y + bh * 0.24f)
            }, tube, style = Stroke(s * 0.014f, cap = StrokeCap.Round))
            drawPath(Path().apply {
                moveTo(cx + bw * 0.3f, y - bh * 0.12f)
                quadraticTo(cx + bw * 0.25f, y + bh * 0.05f, cx, y + bh * 0.08f)
            }, tube, style = Stroke(s * 0.014f, cap = StrokeCap.Round))
            drawCircle(Color(0xFFB0BEC5), s * 0.03f, Offset(cx + bw * 0.18f, y + bh * 0.27f))
            drawCircle(Color(0xFF78909C), s * 0.03f, Offset(cx + bw * 0.18f, y + bh * 0.27f), style = Stroke(s * 0.006f))
        }
        "neck_bandana" -> {
            val red = Color(0xFFD32F2F)
            val p = Path().apply {
                moveTo(cx - bw * 0.36f, y - bh * 0.08f)
                quadraticTo(cx, y + bh * 0.02f, cx + bw * 0.36f, y - bh * 0.08f)
                lineTo(cx, y + bh * 0.26f)
                close()
            }
            drawPath(p, red)
            listOf(-0.12f to 0.02f, 0.1f to 0.0f, 0f to 0.12f, -0.05f to 0.2f, 0.06f to 0.17f).forEach { (dx, dy) ->
                drawCircle(Color.White, s * 0.008f, Offset(cx + bw * dx, y + bh * dy))
            }
        }
        "neck_tie" -> {
            val blue = Color(0xFF1E5AA8)
            val knot = Path().apply {
                moveTo(cx - s * 0.03f, y - bh * 0.06f); lineTo(cx + s * 0.03f, y - bh * 0.06f)
                lineTo(cx + s * 0.02f, y + bh * 0.03f); lineTo(cx - s * 0.02f, y + bh * 0.03f); close()
            }
            val blade = Path().apply {
                moveTo(cx - s * 0.02f, y + bh * 0.03f); lineTo(cx + s * 0.02f, y + bh * 0.03f)
                lineTo(cx + s * 0.04f, y + bh * 0.3f); lineTo(cx, y + bh * 0.36f); lineTo(cx - s * 0.04f, y + bh * 0.3f); close()
            }
            drawPath(blade, blue)
            drawPath(knot, Color(0xFF16457F))
            for (i in 1..3) drawLine(Color(0xFFFFCA28), Offset(cx - s * 0.025f, y + bh * (0.06f + i * 0.07f)), Offset(cx + s * 0.03f, y + bh * (0.02f + i * 0.07f)), s * 0.006f)
        }
        "neck_pearls" -> {
            for (i in 0..10) {
                val t = i / 10f
                val px = cx - bw * 0.32f + bw * 0.64f * t
                val py = y - bh * 0.08f + sin(t * PI).toFloat() * bh * 0.12f
                drawCircle(Color(0xFFFFFDF5), s * 0.016f, Offset(px, py))
                drawCircle(Color(0xFFD7CCC8), s * 0.016f, Offset(px, py), style = Stroke(s * 0.003f))
                drawCircle(Color.White, s * 0.005f, Offset(px - s * 0.004f, py - s * 0.005f))
            }
        }
        "neck_cape" -> {
            drawCircle(Color(0xFFFFC107), s * 0.022f, Offset(cx - bw * 0.3f, y - bh * 0.1f))
            drawCircle(Color(0xFFFFC107), s * 0.022f, Offset(cx + bw * 0.3f, y - bh * 0.1f))
        }
        "neck_medal" -> {
            val c = Offset(cx, y + bh * 0.12f)
            drawPath(Path().apply { moveTo(cx - bw * 0.12f, y - bh * 0.12f); lineTo(c.x - s * 0.01f, c.y); lineTo(c.x + s * 0.02f, c.y); lineTo(cx - bw * 0.04f, y - bh * 0.12f); close() }, Color(0xFF1E88E5))
            drawPath(Path().apply { moveTo(cx + bw * 0.12f, y - bh * 0.12f); lineTo(c.x + s * 0.01f, c.y); lineTo(c.x - s * 0.02f, c.y); lineTo(cx + bw * 0.04f, y - bh * 0.12f); close() }, Color(0xFFE53935))
            drawCircle(Color(0xFFFFC107), s * 0.04f, c)
            drawCircle(Color(0xFFFFA000), s * 0.04f, c, style = Stroke(s * 0.006f))
            drawStar(c, s * 0.022f, Color(0xFFFFF8E1), 5, 0.45f)
        }
    }
}

private fun DrawScope.drawHat(id: String, cx: Float, top: Float, bw: Float, s: Float, base: Color, cy: Float, bh: Float) {
    when (id) {
        "hat_bow" -> {
            val c = Offset(cx + bw * 0.18f, top + s * 0.03f)
            val w = s * 0.075f
            val pink = Color(0xFFFF6FA5)
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x - w, c.y - w * 0.7f); lineTo(c.x - w * 0.9f, c.y + w * 0.6f); close() }, pink)
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x + w, c.y - w * 0.7f); lineTo(c.x + w * 0.9f, c.y + w * 0.6f); close() }, pink)
            drawCircle(Color(0xFFE91E63), w * 0.28f, c)
        }
        "hat_flower" -> {
            val c = Offset(cx - bw * 0.16f, top + s * 0.02f)
            val r = s * 0.032f
            for (i in 0 until 6) {
                val a = i * PI / 3
                drawCircle(Color(0xFFFFF59D), r, c + Offset((cos(a) * r * 1.2f).toFloat(), (sin(a) * r * 1.2f).toFloat()))
            }
            drawCircle(Color(0xFFFF9800), r * 0.9f, c)
        }
        "hat_cap" -> {
            val w = bw * 0.62f
            val blue = Color(0xFF1E88E5)
            drawArc(blue, 180f, 180f, true, Offset(cx - w / 2, top - s * 0.06f), Size(w, s * 0.2f))
            drawOval(Color(0xFF1565C0), Offset(cx - w * 0.05f, top + s * 0.02f), Size(w * 0.8f, s * 0.05f))
            drawCircle(Color(0xFF0D47A1), s * 0.012f, Offset(cx, top - s * 0.06f))
            drawStar(Offset(cx - w * 0.1f, top - s * 0.005f), s * 0.025f, Color.White, 5, 0.45f)
        }
        "hat_beanie" -> {
            val w = bw * 0.66f
            drawArc(Color(0xFF7E57C2), 180f, 180f, true, Offset(cx - w / 2, top - s * 0.08f), Size(w, s * 0.24f))
            drawRoundRect(Color(0xFF5E35B1), Offset(cx - w / 2 - s * 0.01f, top + s * 0.01f), Size(w + s * 0.02f, s * 0.05f), CornerRadius(s * 0.02f))
            drawCircle(Color.White, s * 0.035f, Offset(cx, top - s * 0.09f))
        }
        "hat_party" -> {
            val base = top + s * 0.03f
            val w = s * 0.17f
            val tri = Path().apply { moveTo(cx - w / 2, base); lineTo(cx + w / 2, base); lineTo(cx + s * 0.01f, base - s * 0.2f); close() }
            drawPath(tri, Color(0xFF26C6DA))
            for (i in 1..3) {
                val f = i / 4f
                drawLine(Color(0xFFFFEB3B), Offset(cx - w / 2 * (1 - f) + s * 0.01f * f, base - s * 0.2f * f), Offset(cx + w / 2 * (1 - f) + s * 0.01f * f, base - s * 0.2f * f), s * 0.012f)
            }
            drawCircle(Color(0xFFFF4081), s * 0.025f, Offset(cx + s * 0.01f, base - s * 0.205f))
        }
        "hat_top" -> {
            val w = s * 0.2f
            drawRoundRect(Color(0xFF212121), Offset(cx - w / 2, top - s * 0.17f), Size(w, s * 0.19f), CornerRadius(s * 0.012f))
            drawRoundRect(Color(0xFFD32F2F), Offset(cx - w / 2, top - s * 0.03f), Size(w, s * 0.035f))
            drawRoundRect(Color(0xFF212121), Offset(cx - w * 0.85f, top + s * 0.005f), Size(w * 1.7f, s * 0.03f), CornerRadius(s * 0.015f))
        }
        "hat_headband" -> {
            drawArc(Color(0xFFE53935), 205f, 130f, false, Offset(cx - bw * 0.46f, top + s * 0.015f), Size(bw * 0.92f, bh * 0.75f), style = Stroke(s * 0.04f))
            drawArc(Color.White, 205f, 130f, false, Offset(cx - bw * 0.46f, top + s * 0.015f), Size(bw * 0.92f, bh * 0.75f), style = Stroke(s * 0.01f))
        }
        "hat_straw" -> {
            val straw = Color(0xFFEBC46B)
            drawOval(straw, Offset(cx - bw * 0.6f, top - s * 0.01f), Size(bw * 1.2f, s * 0.07f))
            drawArc(straw, 180f, 180f, true, Offset(cx - bw * 0.3f, top - s * 0.09f), Size(bw * 0.6f, s * 0.2f))
            drawRect(Color(0xFFE53935), Offset(cx - bw * 0.29f, top - s * 0.005f), Size(bw * 0.58f, s * 0.025f))
            for (i in -2..2) drawLine(Color(0xFFC99A3B), Offset(cx + i * bw * 0.1f, top - s * 0.06f), Offset(cx + i * bw * 0.12f, top - s * 0.01f), s * 0.004f)
        }
        "hat_catears" -> {
            for (side in listOf(-1f, 1f)) {
                val bx = cx + side * bw * 0.28f
                val outer = Path().apply {
                    moveTo(bx - side * bw * 0.13f, top + s * 0.045f)
                    lineTo(bx + side * bw * 0.04f, top - s * 0.09f)
                    lineTo(bx + side * bw * 0.14f, top + s * 0.07f)
                    close()
                }
                drawPath(outer, base)
                val inner = Path().apply {
                    moveTo(bx - side * bw * 0.06f, top + s * 0.04f)
                    lineTo(bx + side * bw * 0.035f, top - s * 0.045f)
                    lineTo(bx + side * bw * 0.08f, top + s * 0.05f)
                    close()
                }
                drawPath(inner, Color(0xFFFFB3C8))
            }
        }
        "hat_headphones" -> {
            val dark = Color(0xFF37474F)
            drawArc(dark, 190f, 160f, false, Offset(cx - bw * 0.5f, top - s * 0.04f), Size(bw * 1.0f, bh * 0.9f), style = Stroke(s * 0.025f, cap = StrokeCap.Round))
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * bw * 0.49f, cy - bh * 0.1f)
                drawRoundRect(Color(0xFF26C6DA), c - Offset(s * 0.035f, s * 0.06f), Size(s * 0.07f, s * 0.12f), CornerRadius(s * 0.03f))
                drawRoundRect(dark, c - Offset(s * 0.035f, s * 0.06f), Size(s * 0.07f, s * 0.12f), CornerRadius(s * 0.03f), style = Stroke(s * 0.008f))
            }
        }
        "hat_witch" -> {
            val purple = Color(0xFF5E35B1)
            drawOval(purple, Offset(cx - bw * 0.5f, top - s * 0.005f), Size(bw * 1.0f, s * 0.06f))
            val cone = Path().apply {
                moveTo(cx - bw * 0.25f, top + s * 0.02f)
                lineTo(cx + bw * 0.25f, top + s * 0.02f)
                quadraticTo(cx + bw * 0.08f, top - s * 0.12f, cx + bw * 0.3f, top - s * 0.24f)
                quadraticTo(cx - bw * 0.05f, top - s * 0.15f, cx - bw * 0.25f, top + s * 0.02f)
                close()
            }
            drawPath(cone, purple)
            drawRect(Color(0xFFFFCA28), Offset(cx - bw * 0.24f, top - s * 0.015f), Size(bw * 0.48f, s * 0.025f))
            drawStar(Offset(cx + bw * 0.02f, top - s * 0.08f), s * 0.025f, Color(0xFFFFEB3B), 5, 0.45f)
        }
        "hat_halo" -> {
            drawOval(Color(0xFFFFE082).copy(alpha = 0.5f), Offset(cx - bw * 0.3f, top - s * 0.12f), Size(bw * 0.6f, s * 0.07f), style = Stroke(s * 0.03f))
            drawOval(Color(0xFFFFC107), Offset(cx - bw * 0.3f, top - s * 0.12f), Size(bw * 0.6f, s * 0.07f), style = Stroke(s * 0.014f))
        }
        "hat_crown" -> {
            val w = s * 0.24f
            val base = top + s * 0.035f
            val h = s * 0.11f
            val p = Path().apply {
                moveTo(cx - w / 2, base)
                lineTo(cx - w / 2, base - h)
                lineTo(cx - w / 4, base - h * 0.5f)
                lineTo(cx, base - h * 1.15f)
                lineTo(cx + w / 4, base - h * 0.5f)
                lineTo(cx + w / 2, base - h)
                lineTo(cx + w / 2, base)
                close()
            }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE082), Color(0xFFFFB300)), base - h, base))
            drawPath(p, Color(0xFFE09B00), style = Stroke(s * 0.006f))
            drawCircle(Color(0xFFE53935), s * 0.015f, Offset(cx, base - h * 0.35f))
            drawCircle(Color(0xFF1E88E5), s * 0.012f, Offset(cx - w * 0.3f, base - h * 0.3f))
            drawCircle(Color(0xFF43A047), s * 0.012f, Offset(cx + w * 0.3f, base - h * 0.3f))
        }
    }
}


// ─────────────────────────────── Cape, toys, effects ───────────────────────────────

private fun DrawScope.drawCapeBack(cx: Float, cy: Float, bw: Float, bh: Float, loop: Float) {
    val wave = sin(loop * 2 * PI).toFloat() * bw * 0.03f
    val cape = Path().apply {
        moveTo(cx - bw * 0.3f, cy - bh * 0.12f)
        quadraticTo(cx - bw * 0.62f, cy + bh * 0.15f, cx - bw * 0.7f + wave, cy + bh * 0.52f)
        quadraticTo(cx, cy + bh * 0.62f, cx + bw * 0.7f + wave, cy + bh * 0.52f)
        quadraticTo(cx + bw * 0.62f, cy + bh * 0.15f, cx + bw * 0.3f, cy - bh * 0.12f)
        close()
    }
    drawPath(cape, Color(0xFFD32F2F))
    drawPath(cape, Color(0xFF9A0007), style = Stroke(bw * 0.012f))
}

private fun DrawScope.drawToy(id: String, cx: Float, cy: Float, bw: Float, bh: Float, s: Float, loop: Float) {
    val ground = cy + bh * 0.47f
    when (id) {
        "toy_balloon" -> {
            val hand = Offset(cx - bw * 0.52f, cy + bh * 0.1f)
            val sway = sin(loop * 2 * PI).toFloat() * s * 0.015f
            val b = Offset(cx - bw * 0.72f + sway, cy - bh * 0.75f)
            drawPath(Path().apply {
                moveTo(hand.x, hand.y)
                quadraticTo(hand.x - s * 0.06f, (hand.y + b.y) / 2, b.x, b.y + s * 0.09f)
            }, Color(0xFF616161), style = Stroke(s * 0.004f))
            drawOval(Color(0xFFFF5252), b - Offset(s * 0.07f, s * 0.085f), Size(s * 0.14f, s * 0.17f))
            drawOval(Color.White.copy(alpha = 0.45f), b - Offset(s * 0.045f, s * 0.06f), Size(s * 0.03f, s * 0.05f))
            drawPath(Path().apply { moveTo(b.x - s * 0.012f, b.y + s * 0.095f); lineTo(b.x + s * 0.012f, b.y + s * 0.095f); lineTo(b.x, b.y + s * 0.08f); close() }, Color(0xFFD32F2F))
        }
        "toy_teddy" -> scale(1.6f, Offset(cx - bw * 0.74f, ground)) {
            val c = Offset(cx - bw * 0.74f, ground - s * 0.07f)
            val brown = Color(0xFFA1704A)
            drawCircle(brown, s * 0.065f, c + Offset(0f, s * 0.02f))
            drawCircle(brown, s * 0.05f, c - Offset(0f, s * 0.075f))
            drawCircle(brown, s * 0.02f, c + Offset(-s * 0.04f, -s * 0.115f))
            drawCircle(brown, s * 0.02f, c + Offset(s * 0.04f, -s * 0.115f))
            drawCircle(Color(0xFFE3C3A3), s * 0.022f, c + Offset(0f, -s * 0.06f))
            drawCircle(Color(0xFFE3C3A3), s * 0.035f, c + Offset(0f, s * 0.03f))
            drawCircle(Ink, s * 0.007f, c + Offset(-s * 0.018f, -s * 0.085f))
            drawCircle(Ink, s * 0.007f, c + Offset(s * 0.018f, -s * 0.085f))
            drawCircle(Ink, s * 0.006f, c + Offset(0f, -s * 0.065f))
        }
        "toy_pillplush" -> scale(1.6f, Offset(cx + bw * 0.76f, ground)) {
            val c = Offset(cx + bw * 0.76f, ground - s * 0.06f)
            rotate(-25f, c) {
                drawRoundRect(Color.White, c - Offset(s * 0.1f, s * 0.05f), Size(s * 0.2f, s * 0.1f), CornerRadius(s * 0.05f))
                drawPath(Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(c - Offset(s * 0.1f, s * 0.05f), Size(s * 0.1f, s * 0.1f)), topLeft = CornerRadius(s * 0.05f), bottomLeft = CornerRadius(s * 0.05f)))
                }, Color(0xFFFF7A6B))
                drawRoundRect(Color(0xFFB0BEC5), c - Offset(s * 0.1f, s * 0.05f), Size(s * 0.2f, s * 0.1f), CornerRadius(s * 0.05f), style = Stroke(s * 0.005f))
            }
            drawCircle(Ink, s * 0.008f, c + Offset(-s * 0.025f, -s * 0.0f))
            drawCircle(Ink, s * 0.008f, c + Offset(s * 0.02f, -s * 0.02f))
            drawArc(Ink, 20f, 140f, false, c + Offset(-s * 0.015f, -s * 0.005f), Size(s * 0.03f, s * 0.025f), style = Stroke(s * 0.005f))
        }
        "toy_butterfly" -> {
            val a = loop * 2 * PI
            val c = Offset(cx + (cos(a) * bw * 0.65f).toFloat(), cy - bh * 0.55f + (sin(a * 2) * bh * 0.12f).toFloat())
            scale(1.8f, c) { drawButterfly(c, s, a) }
        }
        "toy_fishbowl" -> scale(1.4f, Offset(cx + bw * 0.78f, ground)) {
            val c = Offset(cx + bw * 0.78f, ground - s * 0.09f)
            val r = s * 0.1f
            drawCircle(Color(0xFFB3E5FC).copy(alpha = 0.7f), r, c)
            drawRect(Color(0xFF81D4FA).copy(alpha = 0.6f), Offset(c.x - r * 0.95f, c.y - r * 0.2f), Size(r * 1.9f, r * 0.9f))
            drawCircle(Color(0xFF90A4AE), r, c, style = Stroke(s * 0.006f))
            val fishX = c.x + (sin(loop * 2 * PI) * r * 0.4f).toFloat()
            drawOval(Color(0xFFFF9800), Offset(fishX - r * 0.3f, c.y + r * 0.05f), Size(r * 0.6f, r * 0.35f))
            drawPath(Path().apply { moveTo(fishX + r * 0.25f, c.y + r * 0.22f); lineTo(fishX + r * 0.5f, c.y + r * 0.05f); lineTo(fishX + r * 0.5f, c.y + r * 0.4f); close() }, Color(0xFFF57C00))
            drawCircle(Ink, r * 0.05f, Offset(fishX - r * 0.15f, c.y + r * 0.18f))
            drawRoundRect(Color(0xFF8D6E63), Offset(c.x - r * 0.7f, c.y + r * 0.85f), Size(r * 1.4f, r * 0.25f), CornerRadius(r * 0.1f))
        }
    }
}


private fun DrawScope.drawButterfly(c: Offset, s: Float, a: Double) {
    val flap = 0.6f + 0.4f * kotlin.math.abs(sin(a * 6)).toFloat()
    for (side in listOf(-1f, 1f)) {
        drawOval(Color(0xFFAB47BC), Offset(c.x + if (side < 0) -s * 0.05f * flap else 0f, c.y - s * 0.04f), Size(s * 0.05f * flap, s * 0.045f))
        drawOval(Color(0xFFFFB74D), Offset(c.x + if (side < 0) -s * 0.035f * flap else 0f, c.y), Size(s * 0.035f * flap, s * 0.03f))
    }
    drawLine(Ink, c + Offset(0f, -s * 0.04f), c + Offset(0f, s * 0.03f), s * 0.008f, StrokeCap.Round)
}

private fun DrawScope.drawEffect(id: String, cx: Float, cy: Float, bw: Float, bh: Float, s: Float, loop: Float) {
    when (id) {
        "fx_sparkles" -> {
            listOf(-0.6f to -0.6f, 0.62f to -0.5f, -0.7f to 0.1f, 0.68f to 0.2f, 0.1f to -0.95f, -0.3f to -0.85f).forEachIndexed { i, (x, y) ->
                val t = (loop + i / 6f) % 1f
                val a = sin(t * PI).toFloat()
                drawStar(Offset(cx + bw * x, cy + bh * y), s * (0.012f + 0.02f * a), Color(0xFFFFF176).copy(alpha = a), 4, 0.35f)
            }
        }
        "fx_hearts" -> {
            for (i in 0 until 5) {
                val t = (loop + i / 5f) % 1f
                val x = cx + bw * (-0.7f + i * 0.35f) + sin(t * 2 * PI).toFloat() * s * 0.02f
                drawHeart(Offset(x, cy + bh * 0.3f - t * bh * 1.2f), s * 0.025f, Color(0xFFFF5C7A).copy(alpha = sin(t * PI).toFloat()))
            }
        }
        "fx_bubbles" -> {
            for (i in 0 until 7) {
                val t = (loop + i / 7f) % 1f
                val x = cx + bw * (-0.75f + (i * 0.25f)) + sin(t * 4 * PI).toFloat() * s * 0.015f
                val c = Offset(x, cy + bh * 0.4f - t * bh * 1.3f)
                val r = s * (0.015f + (i % 3) * 0.008f)
                drawCircle(Color(0xFFB3E5FC).copy(alpha = 0.25f * sin(t * PI).toFloat()), r, c)
                drawCircle(Color.White.copy(alpha = 0.8f * sin(t * PI).toFloat()), r, c, style = Stroke(s * 0.004f))
            }
        }
        "fx_confetti" -> {
            val colors = listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB))
            for (i in 0 until 14) {
                val t = (loop + i / 14f) % 1f
                val x = cx + bw * (-0.9f + (i * 0.137f)) + sin((t + i) * 2 * PI).toFloat() * s * 0.02f
                val y = cy - bh * 1.0f + t * bh * 1.5f
                rotate(t * 360f + i * 40f, Offset(x, y)) {
                    drawRect(colors[i % colors.size], Offset(x - s * 0.008f, y - s * 0.004f), Size(s * 0.016f, s * 0.008f))
                }
            }
        }
    }
}

/** A small preview of an item: the item on a neutral pet (or the scene itself for backgrounds). */
@Composable
fun ItemPreview(itemId: String, slot: String, modifier: Modifier = Modifier) {
    val equipped = buildMap {
        put("COLOR", if (slot == "COLOR") itemId else "color_mint")
        if (slot != "COLOR" && slot != "BACKGROUND") put(slot, itemId)
        if (slot == "BACKGROUND") put("BACKGROUND", itemId)
    }
    Canvas(modifier) {
        if (slot == "BACKGROUND") drawBackground(itemId)
        if (slot == "EFFECT") drawBackground("bg_night")
        val zoom = when (slot) {
            "HAT", "GLASSES" -> 1.25f
            "TOY", "EFFECT" -> 0.85f
            else -> 1f
        }
        val shiftY = if (slot == "HAT") size.height * 0.12f else if (slot == "GLASSES") size.height * 0.02f else 0f
        translate(0f, shiftY) {
            scale(zoom, Offset(size.width / 2, size.height / 2)) {
                drawPet(PetMood.CONTENT, equipped, 0.5f, 0f, 0.3f, 1f)
            }
        }
    }
}
