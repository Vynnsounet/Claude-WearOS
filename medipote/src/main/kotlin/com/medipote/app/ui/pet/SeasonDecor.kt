package com.medipote.app.ui.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import com.medipote.app.data.Festival
import com.medipote.app.data.Season
import com.medipote.app.domain.Ambiance
import com.medipote.app.domain.TimeOfDay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val LEAVES = listOf(0xFFE65100, 0xFFFFA000, 0xFFD84315, 0xFFF9A825, 0xFFBF360C).map { Color(it) }
private val PETALS = listOf(0xFFF8BBD0, 0xFFF48FB1, 0xFFFFFFFF, 0xFFFFCDD2).map { Color(it) }
private val PARTY = listOf(0xFFEF5350, 0xFFFFCA28, 0xFF42A5F5, 0xFF66BB6A, 0xFFAB47BC, 0xFFFF7043).map { Color(it) }

/** Backgrounds that are a room, not the outdoors: the seasons show through the window only. */
private fun indoors(background: String?) = background == null || background == "bg_room" || background == "bg_clinic"

/** Backgrounds on which falling seasonal particles make no sense at all. */
private fun noSeason(background: String?) = background == "bg_clinic" || background == "bg_space" || background == "bg_ocean"

/**
 * Light decoration over the companion's background: the season's falling leaves, snow, petals or
 * drifting light, and a festival's own touches. Discreet by design; [t] is a slow 0..1 loop.
 */
internal fun DrawScope.drawSeasonDecor(a: Ambiance, background: String?, time: TimeOfDay, t: Float) {
    val w = size.width
    val h = size.height
    if (!noSeason(background)) {
        if (indoors(background)) {
            // Through the window of the room.
            val win = Rect(Offset(w * 0.08f, h * 0.1f), Size(w * 0.26f, h * 0.28f))
            clipRect(win.left, win.top, win.right, win.bottom) { seasonLayer(a.season, time, t, win, small = true) }
        } else {
            seasonLayer(a.season, time, t, Rect(Offset.Zero, size), small = false)
        }
    }
    a.festival?.let { festivalLayer(it, background, time, t) }
}

/** [n] things drifting down (or up) through [area], each with its own speed, sway and phase. */
private fun DrawScope.drift(n: Int, t: Float, seed: Int, area: Rect, up: Boolean = false, sway: Float = 0.03f, draw: DrawScope.(Offset, Float, Int, Float) -> Unit) {
    for (i in 0 until n) {
        val r = Random(seed * 101 + i * 17 + 3)
        val x0 = r.nextFloat()
        val speed = 0.5f + r.nextFloat() * 0.6f
        val phase = r.nextFloat()
        val size = 0.6f + r.nextFloat() * 0.8f
        val p = (phase + t * speed) % 1f
        val x = area.left + (x0 + sin((p * 3f + phase) * 2f * PI).toFloat() * sway) * area.width
        val y = area.top + (if (up) 1f - p else p) * area.height
        draw(Offset(x, y), size, i, p)
    }
}

private fun DrawScope.seasonLayer(season: Season, time: TimeOfDay, t: Float, area: Rect, small: Boolean) {
    val unit = if (small) area.width * 1.5f else size.width
    val night = time == TimeOfDay.NIGHT
    when (season) {
        Season.AUTUMN -> drift(if (small) 6 else 12, t, 1, area) { c, sz, i, p ->
            val len = unit * 0.03f * sz
            rotate(p * 540f + i * 40f, c) {
                drawOval(LEAVES[i % LEAVES.size], c - Offset(len, len * 0.55f), Size(len * 2f, len * 1.1f))
                drawLine(Color(0x66402010), c - Offset(len, 0f), c + Offset(len, 0f), len * 0.12f)
            }
        }
        Season.WINTER -> drift(if (small) 10 else 22, t, 2, area, sway = 0.02f) { c, sz, _, _ ->
            val r = unit * 0.009f * sz
            drawCircle(Color.White.copy(alpha = 0.95f), r, c)
            drawCircle(Color(0x66A9C3DD), r, c, style = Stroke(unit * 0.0015f))
        }
        Season.SPRING -> drift(if (small) 6 else 12, t, 3, area, sway = 0.05f) { c, sz, i, p ->
            val len = unit * 0.017f * sz
            rotate(p * 360f + i * 30f, c) { drawOval(PETALS[i % PETALS.size], c - Offset(len, len * 0.6f), Size(len * 2f, len * 1.2f)) }
        }
        Season.SUMMER -> if (night) {
            // Fireflies, blinking slowly.
            drift(if (small) 4 else 8, t * 0.4f, 4, Rect(area.left, area.top + area.height * 0.4f, area.right, area.bottom), sway = 0.08f) { c, sz, i, p ->
                val glow = (0.5f + 0.5f * sin((p * 6f + i) * PI).toFloat())
                drawCircle(Color(0x55FFF59D).copy(alpha = 0.35f * glow), unit * 0.02f * sz, c)
                drawCircle(Color(0xFFFFF59D).copy(alpha = 0.4f + 0.6f * glow), unit * 0.006f * sz, c)
            }
        } else {
            // Warm light drifting up, like dandelion seeds.
            drift(if (small) 5 else 10, t * 0.6f, 5, area, up = true, sway = 0.06f) { c, sz, _, p ->
                val a = (1f - kotlin.math.abs(p - 0.5f) * 2f).coerceIn(0f, 1f)
                drawCircle(Color(0xFFFFD54F).copy(alpha = 0.95f * a), unit * 0.011f * sz, c)
                drawCircle(Color.White.copy(alpha = 0.8f * a), unit * 0.005f * sz, c)
            }
        }
    }
}

private fun DrawScope.festivalLayer(f: Festival, background: String?, time: TimeOfDay, t: Float) {
    val w = size.width
    val h = size.height
    val full = Rect(Offset.Zero, size)
    when (f) {
        Festival.NEW_YEAR -> {
            drift(26, t, 10, full, sway = 0.04f) { c, sz, i, p ->
                rotate(p * 720f + i * 25f, c) { drawRect(PARTY[i % PARTY.size], c - Offset(w * 0.008f * sz, w * 0.004f * sz), Size(w * 0.016f * sz, w * 0.008f * sz)) }
            }
            listOf(0.14f to 0.1f, 0.5f to 0.06f, 0.86f to 0.14f, 0.3f to 0.2f, 0.72f to 0.22f).forEachIndexed { i, (x, y) ->
                val tw = 0.5f + 0.5f * sin((t * 6f + i) * PI).toFloat()
                drawStar(Offset(w * x, h * y), w * (0.012f + 0.012f * tw), Color(0xFFFFE082).copy(alpha = 0.6f + 0.4f * tw), 4, 0.35f)
            }
        }
        Festival.VALENTINE -> drift(12, t * 0.8f, 11, full, up = true, sway = 0.05f) { c, sz, i, p ->
            val a = (1f - kotlin.math.abs(p - 0.5f) * 1.2f).coerceIn(0.5f, 1f)
            drawHeart(c, w * 0.03f * sz, listOf(Color(0xFFF06292), Color(0xFFEC407A), Color(0xFFFF8A80))[i % 3].copy(alpha = a))
        }
        Festival.EASTER -> {
            // Painted eggs in the grass (or on the floor).
            listOf(0.1f to 0.9f, 0.24f to 0.95f, 0.8f to 0.92f, 0.92f to 0.88f).forEachIndexed { i, (x, y) ->
                val c = Offset(w * x, h * y)
                val rx = w * 0.032f
                drawOval(PARTY[(i + 1) % PARTY.size].copy(alpha = 0.95f), c - Offset(rx, rx * 1.3f), Size(rx * 2f, rx * 2.6f))
                drawLine(Color.White.copy(alpha = 0.8f), c - Offset(rx * 0.9f, 0f), c + Offset(rx * 0.9f, 0f), rx * 0.3f)
                drawCircle(Color.White.copy(alpha = 0.7f), rx * 0.18f, c - Offset(0f, rx * 0.8f))
            }
            drift(8, t, 12, full, sway = 0.05f) { c, sz, i, p ->
                val len = w * 0.013f * sz
                rotate(p * 360f, c) { drawOval(PETALS[i % PETALS.size], c - Offset(len, len * 0.6f), Size(len * 2f, len * 1.2f)) }
            }
        }
        Festival.HALLOWEEN -> {
            val c = Offset(w * 0.12f, h * 0.88f)
            val r = w * 0.07f
            drawRect(Color(0xFF558B2F), c - Offset(r * 0.12f, r * 1.25f), Size(r * 0.24f, r * 0.4f))
            drawOval(Color(0xFFEF6C00), c - Offset(r, r * 0.85f), Size(r * 2f, r * 1.7f))
            drawOval(Color(0xFFF57C00), c - Offset(r * 0.5f, r * 0.85f), Size(r, r * 1.7f))
            // The face: two triangles, a jagged smile.
            listOf(-0.4f, 0.4f).forEach { dx ->
                drawPath(Path().apply {
                    moveTo(c.x + r * dx, c.y - r * 0.5f); lineTo(c.x + r * (dx - 0.2f), c.y - r * 0.15f); lineTo(c.x + r * (dx + 0.2f), c.y - r * 0.15f); close()
                }, Color(0xFF3E2723))
            }
            drawPath(Path().apply {
                moveTo(c.x - r * 0.55f, c.y + r * 0.15f); lineTo(c.x - r * 0.28f, c.y + r * 0.45f); lineTo(c.x - r * 0.1f, c.y + r * 0.2f)
                lineTo(c.x + r * 0.1f, c.y + r * 0.45f); lineTo(c.x + r * 0.28f, c.y + r * 0.2f); lineTo(c.x + r * 0.55f, c.y + r * 0.15f)
                lineTo(c.x + r * 0.3f, c.y + r * 0.6f); lineTo(c.x - r * 0.3f, c.y + r * 0.6f); close()
            }, Color(0xFF3E2723))
            // Two bats drifting across the top.
            listOf(0.0f to 0.14f, 0.45f to 0.24f).forEach { (off, y) ->
                val x = ((off + t * 0.5f) % 1f) * (w * 1.2f) - w * 0.1f
                val flap = 0.6f + 0.4f * sin((t * 40f + off * 10f) * PI).toFloat()
                val b = Offset(x, h * y)
                val s = w * 0.03f
                drawPath(Path().apply {
                    moveTo(b.x - s * 1.5f, b.y - s * flap); quadraticTo(b.x - s * 0.8f, b.y + s * 0.2f, b.x, b.y)
                    quadraticTo(b.x + s * 0.8f, b.y + s * 0.2f, b.x + s * 1.5f, b.y - s * flap)
                    quadraticTo(b.x + s * 0.7f, b.y - s * 0.4f, b.x, b.y - s * 0.3f)
                    quadraticTo(b.x - s * 0.7f, b.y - s * 0.4f, b.x - s * 1.5f, b.y - s * flap); close()
                }, if (time == TimeOfDay.NIGHT) Color(0xFFB39DDB) else Color(0xFF212121))
            }
        }
        Festival.CHRISTMAS -> {
            drift(14, t, 13, full, sway = 0.02f) { c, sz, _, _ -> drawCircle(Color.White.copy(alpha = 0.95f), w * 0.006f * sz, c) }
            // A little tree with lights.
            val base = Offset(w * 0.88f, h * 0.9f)
            val tw = w * 0.12f
            drawRect(Color(0xFF6D4C41), base - Offset(tw * 0.07f, tw * 0.2f), Size(tw * 0.14f, tw * 0.25f))
            listOf(0f to 0.55f, 0.32f to 0.42f, 0.62f to 0.3f).forEach { (up, half) ->
                val y = base.y - tw * 0.2f - tw * up
                drawPath(Path().apply { moveTo(base.x, y - tw * 0.5f); lineTo(base.x + tw * half, y); lineTo(base.x - tw * half, y); close() }, Color(0xFF2E7D32))
            }
            drawStar(Offset(base.x, base.y - tw * 1.3f), tw * 0.13f, Color(0xFFFFD54F), 5, 0.45f)
            listOf(-0.25f to -0.15f, 0.2f to -0.4f, -0.1f to -0.7f, 0.12f to -0.95f).forEachIndexed { i, (dx, dy) ->
                val on = 0.55f + 0.45f * sin((t * 20f + i * 1.7f) * PI).toFloat()
                drawCircle(PARTY[i % PARTY.size].copy(alpha = on), tw * 0.045f, Offset(base.x + tw * dx, base.y + tw * dy))
            }
            // A string of lights along the top.
            for (i in 0..8) {
                val x = w * (0.05f + i * 0.11f)
                val y = h * (0.04f + 0.03f * sin(i * 0.9f))
                val on = 0.5f + 0.5f * sin((t * 20f + i * 1.3f) * PI).toFloat()
                drawCircle(PARTY[i % PARTY.size].copy(alpha = 0.45f + 0.5f * on), w * 0.011f, Offset(x, y))
            }
        }
        Festival.PET_BIRTHDAY, Festival.USER_BIRTHDAY -> {
            drift(5, t * 0.6f, 14, full, up = true, sway = 0.04f) { c, sz, i, _ ->
                val rx = w * 0.03f * sz
                drawLine(Color(0x88000000), c + Offset(0f, rx * 1.3f), c + Offset(rx * 0.2f, rx * 3.2f), w * 0.0025f)
                drawOval(PARTY[i % PARTY.size], c - Offset(rx, rx * 1.3f), Size(rx * 2f, rx * 2.6f))
                drawCircle(Color.White.copy(alpha = 0.5f), rx * 0.25f, c - Offset(rx * 0.4f, rx * 0.6f))
            }
            drift(18, t, 15, full, sway = 0.04f) { c, sz, i, p ->
                rotate(p * 720f + i * 25f, c) { drawRect(PARTY[i % PARTY.size], c - Offset(w * 0.007f * sz, w * 0.0035f * sz), Size(w * 0.014f * sz, w * 0.007f * sz)) }
            }
        }
    }
}
