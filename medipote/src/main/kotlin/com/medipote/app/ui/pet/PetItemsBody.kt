package com.medipote.app.ui.pet

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Ink2 = Color(0xFF1D2B28)

// ───────────────────────────── Face ─────────────────────────────

/** Face items (1.39): drawn over the face, from the places of the eyes, cheeks and mouth in [PetGeo]. */
internal fun DrawScope.drawFaceItem(id: String, g: PetGeo) {
    val s = g.s; val cx = g.cx
    val cheekY = g.eyeY + s * 0.057f + g.bh * 0.035f
    val cheekDx = g.eyeDx + g.bw * 0.075f
    when (id) {
        "face_freckles" -> for (side in listOf(-1f, 1f)) listOf(-0.03f to -0.012f, 0.0f to 0.01f, 0.03f to -0.008f, 0.014f to 0.03f, -0.016f to 0.026f).forEach { (dx, dy) ->
            drawCircle(Color(0xFF9A5B3C).copy(alpha = 0.75f), s * 0.006f, Offset(cx + side * (cheekDx - 0.0f) + s * dx, cheekY + s * dy))
        }
        "face_whiskers" -> for (side in listOf(-1f, 1f)) for (k in -1..1) {
            val start = Offset(cx + side * (cheekDx + g.bw * 0.03f), cheekY + k * s * 0.014f)
            drawLine(Color(0xFF455A64), start, start + Offset(side * g.bw * 0.2f, k * s * 0.03f), s * 0.005f, StrokeCap.Round)
        }
        "face_mustache" -> {
            val m = g.mouth
            val brown = Color(0xFF5D4037)
            for (side in listOf(-1f, 1f)) drawPath(Path().apply {
                moveTo(m.x, m.y - g.bh * 0.045f)
                quadraticTo(m.x + side * g.bw * 0.1f, m.y - g.bh * 0.085f, m.x + side * g.bw * 0.2f, m.y - g.bh * 0.045f)
                quadraticTo(m.x + side * g.bw * 0.22f, m.y - g.bh * 0.015f, m.x + side * g.bw * 0.17f, m.y - g.bh * 0.02f)
                quadraticTo(m.x + side * g.bw * 0.1f, m.y - g.bh * 0.045f, m.x, m.y - g.bh * 0.018f)
                close()
            }, brown)
        }
        "face_clownnose" -> {
            val c = Offset(cx, (g.eyeY + g.mouth.y) / 2f + g.bh * 0.005f)
            drawCircle(Color(0xFFE53935), s * 0.032f, c)
            drawCircle(Color.White.copy(alpha = 0.7f), s * 0.009f, c + Offset(-s * 0.01f, -s * 0.011f))
        }
        "face_cheekstars" -> for (side in listOf(-1f, 1f)) drawStar(Offset(cx + side * cheekDx, cheekY), s * 0.026f, Color(0xFFFFD54F), 5, 0.45f)
        "face_plaster" -> {
            val c = Offset(cx + cheekDx, cheekY)
            rotate(-30f, c) {
                drawRoundRect(Color(0xFFF3D2B3), c - Offset(s * 0.045f, s * 0.016f), Size(s * 0.09f, s * 0.032f), CornerRadius(s * 0.012f))
                drawRect(Color(0xFFE2B48A), c - Offset(s * 0.014f, s * 0.016f), Size(s * 0.028f, s * 0.032f))
                for (i in -1..1) for (j in -1..1) if (i != 0) drawCircle(Color(0xFFD9A678), s * 0.0022f, c + Offset(i * s * 0.03f, j * s * 0.008f))
            }
        }
        "face_eyepatch" -> {
            val e = Offset(cx + g.eyeDx, g.eyeY)
            drawLine(Color(0xFF263238), e + Offset(-s * 0.035f, -s * 0.04f), Offset(cx - g.hw * 0.62f, g.top + g.bh * 0.17f), s * 0.008f)
            drawLine(Color(0xFF263238), e + Offset(s * 0.035f, -s * 0.03f), Offset(cx + g.hw * 0.9f, g.eyeY - g.bh * 0.03f), s * 0.008f)
            drawOval(Color(0xFF263238), e - Offset(s * 0.062f, s * 0.056f), Size(s * 0.124f, s * 0.112f))
            drawOval(Color(0xFF455A64), e - Offset(s * 0.062f, s * 0.056f), Size(s * 0.124f, s * 0.112f), style = Stroke(s * 0.004f))
        }
        "face_hearts" -> for (side in listOf(-1f, 1f)) drawHeart(Offset(cx + side * cheekDx, cheekY), s * 0.026f, Color(0xFFFF4F7B))
        "face_fangs" -> {
            val m = g.mouth
            for (side in listOf(-1f, 1f)) drawPath(Path().apply {
                moveTo(m.x + side * g.bw * 0.05f, m.y + g.bh * 0.0f); lineTo(m.x + side * g.bw * 0.085f, m.y + g.bh * 0.0f); lineTo(m.x + side * g.bw * 0.07f, m.y + g.bh * 0.05f); close()
            }, Color.White)
        }
        "face_lashes" -> for (side in listOf(-1f, 1f)) for (k in 0..2) {
            val e = Offset(cx + side * g.eyeDx, g.eyeY)
            val a = (-60f - k * 25f) * (PI / 180).toFloat()
            val from = e + Offset(side * s * 0.03f * cos(a), s * 0.04f * sin(a) * 1.1f)
            drawLine(Ink2, from, from + Offset(side * s * 0.03f, -s * 0.016f - k * s * 0.003f) , s * 0.006f, StrokeCap.Round)
        }
        "face_blush" -> for (side in listOf(-1f, 1f)) {
            val c = Offset(cx + side * cheekDx, cheekY)
            drawOval(Brush.radialGradient(listOf(Color(0xCCFF7A9A), Color(0x00FF7A9A)), c, s * 0.055f), c - Offset(s * 0.055f, s * 0.04f), Size(s * 0.11f, s * 0.08f))
        }
        "face_tiger" -> for (side in listOf(-1f, 1f)) for (k in -1..1) {
            val from = Offset(cx + side * (cheekDx + g.bw * 0.02f), cheekY + k * s * 0.016f)
            drawLine(Color(0xFFFF7A00), from, from + Offset(side * g.bw * 0.11f, k * s * 0.014f), s * 0.008f, StrokeCap.Round)
        }
    }
}

// ───────────────────────────── Feet ─────────────────────────────

/** Footwear (1.39): drawn over the two feet at the bottom of the body. */
internal fun DrawScope.drawFeetItem(id: String, g: PetGeo, shade: Color) {
    val s = g.s; val bw = g.bw; val bh = g.bh
    val y = g.bottom - bh * 0.035f
    for (side in listOf(-1f, 1f)) {
        val x = g.cx + side * bw * 0.2f
        when (id) {
            "feet_sneakers" -> {
                drawOval(Color.White, Offset(x - bw * 0.135f, y - bh * 0.1f), Size(bw * 0.27f, bh * 0.18f))
                drawOval(Color(0xFFE53935), Offset(x - bw * 0.135f, y - bh * 0.1f), Size(bw * 0.27f, bh * 0.18f), style = Stroke(s * 0.006f))
                drawRect(Color(0xFFEEEEEE), Offset(x - bw * 0.13f, y + bh * 0.04f), Size(bw * 0.26f, bh * 0.045f))
                for (i in -1..1) drawLine(Color(0xFFB0BEC5), Offset(x + i * bw * 0.04f - bw * 0.02f, y - bh * 0.07f), Offset(x + i * bw * 0.04f + bw * 0.02f, y - bh * 0.07f), s * 0.004f)
            }
            "feet_rainboots" -> {
                val yellow = Color(0xFFFFCA28)
                drawRoundRect(yellow, Offset(x - bw * 0.1f, y - bh * 0.2f), Size(bw * 0.2f, bh * 0.22f), CornerRadius(s * 0.012f))
                drawOval(yellow, Offset(x - bw * 0.135f, y - bh * 0.04f), Size(bw * 0.27f, bh * 0.14f))
                drawRect(Color(0xFFE0A800), Offset(x - bw * 0.135f, y + bh * 0.07f), Size(bw * 0.27f, bh * 0.03f))
                drawRect(Color(0xFFE0A800), Offset(x - bw * 0.1f, y - bh * 0.2f), Size(bw * 0.2f, bh * 0.03f))
                drawOval(Color.White.copy(alpha = 0.5f), Offset(x - bw * 0.06f, y - bh * 0.15f), Size(bw * 0.03f, bh * 0.1f))
            }
            "feet_slippers" -> {
                val pink = Color(0xFFFFB3C8)
                drawOval(pink, Offset(x - bw * 0.14f, y - bh * 0.1f), Size(bw * 0.28f, bh * 0.18f))
                drawOval(Color(0xFFFFE0EB), Offset(x - bw * 0.1f, y - bh * 0.08f), Size(bw * 0.2f, bh * 0.07f))
                for (e in listOf(-1f, 1f)) drawOval(pink, Offset(x + e * bw * 0.05f - bw * 0.02f, y - bh * 0.2f), Size(bw * 0.04f, bh * 0.13f))
                drawCircle(Ink2, s * 0.005f, Offset(x - bw * 0.03f, y - bh * 0.03f)); drawCircle(Ink2, s * 0.005f, Offset(x + bw * 0.03f, y - bh * 0.03f))
            }
            "feet_socks" -> {
                drawRoundRect(Color.White, Offset(x - bw * 0.1f, y - bh * 0.17f), Size(bw * 0.2f, bh * 0.2f), CornerRadius(s * 0.01f))
                drawOval(Color.White, Offset(x - bw * 0.135f, y - bh * 0.06f), Size(bw * 0.27f, bh * 0.15f))
                for (i in 0..2) drawRect(if (i % 2 == 0) Color(0xFFE53935) else Color(0xFF3F6FD1), Offset(x - bw * 0.1f, y - bh * (0.17f - i * 0.045f)), Size(bw * 0.2f, bh * 0.022f))
            }
            "feet_flippers" -> {
                val green = Color(0xFF26A69A)
                drawPath(Path().apply {
                    moveTo(x - bw * 0.09f, y - bh * 0.08f)
                    quadraticTo(x - bw * 0.24f + side * bw * 0.06f, y + bh * 0.02f, x - bw * 0.2f + side * bw * 0.1f, y + bh * 0.1f)
                    lineTo(x + bw * 0.2f + side * bw * 0.1f, y + bh * 0.1f)
                    quadraticTo(x + bw * 0.24f + side * bw * 0.06f, y + bh * 0.02f, x + bw * 0.09f, y - bh * 0.08f)
                    close()
                }, green)
                for (i in -1..1) drawLine(Color(0xFF00796B), Offset(x + i * bw * 0.03f, y - bh * 0.03f), Offset(x + i * bw * 0.09f + side * bw * 0.06f, y + bh * 0.09f), s * 0.004f)
            }
            "feet_furboots" -> {
                val brown = Color(0xFFA1704A)
                drawRoundRect(brown, Offset(x - bw * 0.1f, y - bh * 0.2f), Size(bw * 0.2f, bh * 0.22f), CornerRadius(s * 0.012f))
                drawOval(brown, Offset(x - bw * 0.135f, y - bh * 0.04f), Size(bw * 0.27f, bh * 0.14f))
                for (i in -2..2) drawCircle(Color.White, s * 0.014f, Offset(x + i * bw * 0.045f, y - bh * 0.2f))
                drawRect(Color(0xFF7A5030), Offset(x - bw * 0.135f, y + bh * 0.07f), Size(bw * 0.27f, bh * 0.03f))
            }
            "feet_skates" -> {
                drawOval(Color.White, Offset(x - bw * 0.135f, y - bh * 0.15f), Size(bw * 0.27f, bh * 0.2f))
                drawOval(Color(0xFF64B5F6), Offset(x - bw * 0.135f, y - bh * 0.15f), Size(bw * 0.27f, bh * 0.2f), style = Stroke(s * 0.006f))
                drawLine(Color(0xFF78909C), Offset(x - bw * 0.17f, y + bh * 0.085f), Offset(x + bw * 0.18f, y + bh * 0.085f), s * 0.012f, StrokeCap.Round)
                drawLine(Color(0xFF78909C), Offset(x - bw * 0.08f, y + bh * 0.04f), Offset(x - bw * 0.08f, y + bh * 0.085f), s * 0.007f)
                drawLine(Color(0xFF78909C), Offset(x + bw * 0.09f, y + bh * 0.04f), Offset(x + bw * 0.09f, y + bh * 0.085f), s * 0.007f)
            }
            "feet_ballet" -> {
                val pink = Color(0xFFF8BBD0)
                drawOval(pink, Offset(x - bw * 0.125f, y - bh * 0.09f), Size(bw * 0.25f, bh * 0.16f))
                drawOval(Color(0xFFF48FB1), Offset(x - bw * 0.125f, y - bh * 0.09f), Size(bw * 0.25f, bh * 0.16f), style = Stroke(s * 0.004f))
                drawPath(Path().apply { moveTo(x - bw * 0.07f, y - bh * 0.05f); quadraticTo(x, y - bh * 0.22f, x + bw * 0.07f, y - bh * 0.05f) }, Color(0xFFF06292), style = Stroke(s * 0.006f, cap = StrokeCap.Round))
            }
        }
    }
}

// ───────────────────────────── Back ─────────────────────────────

/** Things on the back (1.39), drawn behind the body ([front] = false) or its straps in front ([front] = true). */
internal fun DrawScope.drawBackItem(id: String, g: PetGeo, loop: Float, front: Boolean) {
    val s = g.s; val cx = g.cx; val bw = g.bw; val bh = g.bh; val cy = g.cy
    val flap = sin(loop * 2 * PI).toFloat()
    if (front) {
        if (id == "back_backpack") for (side in listOf(-1f, 1f)) drawPath(Path().apply {
            moveTo(cx + side * bw * 0.44f, cy - bh * 0.1f); quadraticTo(cx + side * bw * 0.46f, cy + bh * 0.12f, cx + side * bw * 0.4f, cy + bh * 0.34f)
        }, Color(0xFFB71C1C), style = Stroke(s * 0.016f, cap = StrokeCap.Round))
        return
    }
    when (id) {
        "back_backpack" -> {
            val red = Color(0xFFE53935)
            drawRoundRect(red, Offset(cx - bw * 0.56f, cy - bh * 0.22f), Size(bw * 1.12f, bh * 0.6f), CornerRadius(s * 0.04f))
            drawRoundRect(Color(0xFFB71C1C), Offset(cx - bw * 0.56f, cy - bh * 0.22f), Size(bw * 1.12f, bh * 0.6f), CornerRadius(s * 0.04f), style = Stroke(s * 0.006f))
            drawRoundRect(Color(0xFFFFCA28), Offset(cx - bw * 0.5f, cy + bh * 0.2f), Size(bw * 0.18f, bh * 0.14f), CornerRadius(s * 0.01f))
        }
        "back_wings_angel", "back_wings_fairy", "back_wings_bat", "back_wings_butterfly" -> for (side in listOf(-1f, 1f)) {
            val root = Offset(cx + side * bw * 0.36f, cy - bh * 0.05f)
            val lift = flap * s * 0.02f
            // Wings are drawn a little smaller so their tips stay inside a square picture.
            scale(0.8f, root) { rotate(side * flap * 5f, root) {
                when (id) {
                    "back_wings_angel" -> {
                        val wing = Path().apply {
                            moveTo(root.x, root.y)
                            quadraticTo(root.x + side * s * 0.3f, root.y - s * 0.2f - lift, root.x + side * s * 0.34f, root.y - s * 0.02f)
                            quadraticTo(root.x + side * s * 0.3f, root.y + s * 0.04f, root.x + side * s * 0.25f, root.y + s * 0.06f)
                            quadraticTo(root.x + side * s * 0.16f, root.y + s * 0.1f, root.x, root.y + bh * 0.15f)
                            close()
                        }
                        drawPath(wing, Color.White); drawPath(wing, Color(0xFFCFD8DC), style = Stroke(s * 0.005f))
                        for (k in 1..3) drawLine(Color(0xFFE3EAEE), root + Offset(side * s * 0.04f, s * 0.0f), root + Offset(side * s * (0.12f + k * 0.07f), -s * (0.12f - k * 0.04f)), s * 0.004f)
                    }
                    "back_wings_fairy" -> {
                        val a = Color(0x88B3E5FC); val b = Color(0x88F8BBD0); val edge = Color(0xFF81D4FA)
                        drawOval(a, Offset(root.x + (if (side > 0) 0f else -s * 0.25f), root.y - s * 0.24f - lift), Size(s * 0.25f, s * 0.2f))
                        drawOval(b, Offset(root.x + (if (side > 0) 0f else -s * 0.2f), root.y - s * 0.04f), Size(s * 0.2f, s * 0.14f))
                        drawOval(edge, Offset(root.x + (if (side > 0) 0f else -s * 0.25f), root.y - s * 0.24f - lift), Size(s * 0.25f, s * 0.2f), style = Stroke(s * 0.004f))
                        drawOval(Color(0xFFF48FB1), Offset(root.x + (if (side > 0) 0f else -s * 0.2f), root.y - s * 0.04f), Size(s * 0.2f, s * 0.14f), style = Stroke(s * 0.004f))
                    }
                    "back_wings_bat" -> {
                        val purple = Color(0xFF5E35B1)
                        val wing = Path().apply {
                            moveTo(root.x, root.y)
                            lineTo(root.x + side * s * 0.12f, root.y - s * 0.2f - lift)
                            lineTo(root.x + side * s * 0.24f, root.y - s * 0.1f)
                            quadraticTo(root.x + side * s * 0.2f, root.y, root.x + side * s * 0.3f, root.y + s * 0.03f)
                            quadraticTo(root.x + side * s * 0.2f, root.y + s * 0.06f, root.x + side * s * 0.16f, root.y + s * 0.1f)
                            quadraticTo(root.x + side * s * 0.1f, root.y + s * 0.04f, root.x, root.y + s * 0.08f)
                            close()
                        }
                        drawPath(wing, purple); drawPath(wing, Color(0xFF311B92), style = Stroke(s * 0.005f))
                    }
                    else -> {
                        drawOval(Color(0xFFFF9800), Offset(root.x + (if (side > 0) 0f else -s * 0.26f), root.y - s * 0.22f - lift), Size(s * 0.26f, s * 0.22f))
                        drawOval(Color(0xFFFFB74D), Offset(root.x + (if (side > 0) 0f else -s * 0.2f), root.y - s * 0.02f), Size(s * 0.2f, s * 0.15f))
                        drawOval(Color(0xFF263238), Offset(root.x + (if (side > 0) 0f else -s * 0.26f), root.y - s * 0.22f - lift), Size(s * 0.26f, s * 0.22f), style = Stroke(s * 0.006f))
                        drawCircle(Color.White, s * 0.015f, Offset(root.x + side * s * 0.17f, root.y - s * 0.13f - lift)); drawCircle(Color.White, s * 0.01f, Offset(root.x + side * s * 0.12f, root.y + s * 0.04f))
                    }
                }
            } }
        }
        "back_jetpack" -> {
            for (side in listOf(-1f, 1f)) {
                val x = cx + side * bw * 0.56f
                drawRoundRect(Color(0xFF90A4AE), Offset(x - bw * 0.085f, cy - bh * 0.2f), Size(bw * 0.17f, bh * 0.5f), CornerRadius(s * 0.025f))
                drawRect(Color(0xFFE53935), Offset(x - bw * 0.085f, cy - bh * 0.02f), Size(bw * 0.17f, bh * 0.05f))
                drawLine(Color(0xFF607D8B), Offset(x - bw * 0.085f, cy - bh * 0.1f), Offset(x - bw * 0.0f, cy - bh * 0.1f), s * 0.004f)
                val f = 0.7f + 0.3f * sin((loop * 4 + (if (side > 0) 0.3f else 0f)) * PI).toFloat()
                drawPath(Path().apply { moveTo(x - bw * 0.06f, cy + bh * 0.3f); lineTo(x + bw * 0.06f, cy + bh * 0.3f); lineTo(x, cy + bh * (0.3f + 0.2f * f)); close() }, Color(0xFFFFA726))
                drawPath(Path().apply { moveTo(x - bw * 0.03f, cy + bh * 0.3f); lineTo(x + bw * 0.03f, cy + bh * 0.3f); lineTo(x, cy + bh * (0.3f + 0.11f * f)); close() }, Color(0xFFFFF59D))
            }
        }
        "back_shell" -> {
            val c = Offset(cx + bw * 0.28f, cy - bh * 0.05f)
            drawCircle(Color(0xFFD7A86E), bw * 0.42f, c); drawCircle(Color(0xFFB8864B), bw * 0.42f, c, style = Stroke(s * 0.007f))
            val p = Path()
            for (i in 0..60) { val a = i / 60f * 5 * PI; val rr = bw * 0.38f * i / 60f; val q = c + Offset((cos(a) * rr).toFloat(), (sin(a) * rr).toFloat()); if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }
            drawPath(p, Color(0xFF9C6A32), style = Stroke(s * 0.008f, cap = StrokeCap.Round))
        }
        "back_balloons" -> {
            val cols = listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF40C4FF))
            val hand = Offset(cx + bw * 0.5f, cy + bh * 0.1f)
            for (i in 0..2) {
                val sway = sin((loop + i * 0.3f) * 2 * PI).toFloat() * s * 0.012f
                val b = Offset(cx + bw * (0.5f + i * 0.09f) + sway, cy - bh * (0.7f + (i % 2) * 0.18f))
                drawPath(Path().apply { moveTo(hand.x, hand.y); quadraticTo(hand.x + s * 0.02f, (hand.y + b.y) / 2, b.x, b.y + s * 0.07f) }, Color(0xFF616161), style = Stroke(s * 0.003f))
                drawOval(cols[i], b - Offset(s * 0.055f, s * 0.07f), Size(s * 0.11f, s * 0.14f))
                drawOval(Color.White.copy(alpha = 0.4f), b - Offset(s * 0.035f, s * 0.05f), Size(s * 0.022f, s * 0.04f))
            }
        }
    }
}

// ───────────────────────────── Patterns ─────────────────────────────

/** Patterns on the body (1.39), drawn inside its outline: [body] clips them, [shade] is the darker tone of the colour. */
internal fun DrawScope.drawPatternItem(id: String, g: PetGeo, body: Path, shade: Color) {
    val s = g.s; val cx = g.cx; val cy = g.cy; val bw = g.bw; val bh = g.bh
    clipPath(body) {
        when (id) {
            "pattern_spots" -> listOf(-0.28f to -0.28f, 0.2f to -0.34f, 0.34f to -0.05f, -0.36f to 0.02f, 0.12f to 0.32f, -0.18f to 0.36f, 0.38f to 0.3f).forEach { (dx, dy) ->
                drawCircle(shade.copy(alpha = 0.4f), s * (0.03f + (kotlin.math.abs(dx * 10).toInt() % 3) * 0.008f), Offset(cx + bw * dx, cy + bh * dy))
            }
            "pattern_stripes" -> for (i in 0..5) drawRect(shade.copy(alpha = 0.32f), Offset(cx - bw, g.top + bh * (0.08f + i * 0.17f)), Size(bw * 2, bh * 0.07f))
            "pattern_dots" -> for (r in 0..4) for (c in -3..3) drawCircle(Color.White.copy(alpha = 0.5f), s * 0.012f, Offset(cx + bw * 0.16f * c + (if (r % 2 == 0) 0f else bw * 0.08f), g.top + bh * (0.1f + r * 0.2f)))
            "pattern_stars" -> listOf(-0.3f to -0.3f, 0.25f to -0.36f, 0.38f to 0.0f, -0.38f to 0.1f, -0.12f to 0.4f, 0.28f to 0.34f).forEach { (dx, dy) -> drawStar(Offset(cx + bw * dx, cy + bh * dy), s * 0.022f, Color.White.copy(alpha = 0.75f), 5, 0.45f) }
            "pattern_hearts" -> listOf(-0.3f to -0.28f, 0.28f to -0.32f, 0.38f to 0.05f, -0.38f to 0.08f, -0.14f to 0.4f, 0.26f to 0.36f).forEach { (dx, dy) -> drawHeart(Offset(cx + bw * dx, cy + bh * dy), s * 0.02f, Color(0xFFFF7A9A).copy(alpha = 0.8f)) }
            "pattern_flowers" -> listOf(-0.3f to -0.3f, 0.26f to -0.34f, 0.38f to 0.05f, -0.38f to 0.1f, 0.12f to 0.38f, -0.22f to 0.36f).forEach { (dx, dy) ->
                val c = Offset(cx + bw * dx, cy + bh * dy)
                for (k in 0 until 5) { val a = k * 2 * PI / 5; drawCircle(Color.White.copy(alpha = 0.8f), s * 0.011f, c + Offset((cos(a) * s * 0.014f).toFloat(), (sin(a) * s * 0.014f).toFloat())) }
                drawCircle(Color(0xFFFFD54F), s * 0.007f, c)
            }
            "pattern_checker" -> for (r in 0..6) for (c in -4..4) if ((r + c) % 2 == 0) drawRect(Color.White.copy(alpha = 0.28f), Offset(cx + bw * 0.14f * c - bw * 0.07f, g.top + bh * 0.14f * r), Size(bw * 0.14f, bh * 0.14f))
            "pattern_flames" -> for (i in -3..3) {
                val x = cx + bw * 0.16f * i
                drawPath(Path().apply { moveTo(x - bw * 0.07f, g.bottom); quadraticTo(x - bw * 0.05f, g.bottom - bh * 0.18f, x, g.bottom - bh * (0.3f + (i % 2) * 0.08f)); quadraticTo(x + bw * 0.05f, g.bottom - bh * 0.18f, x + bw * 0.07f, g.bottom); close() }, Color(0xFFFF7043).copy(alpha = 0.85f))
                drawPath(Path().apply { moveTo(x - bw * 0.035f, g.bottom); quadraticTo(x - bw * 0.02f, g.bottom - bh * 0.1f, x, g.bottom - bh * 0.17f); quadraticTo(x + bw * 0.02f, g.bottom - bh * 0.1f, x + bw * 0.035f, g.bottom); close() }, Color(0xFFFFD54F))
            }
            "pattern_waves" -> for (i in 0..5) {
                val y = g.top + bh * (0.1f + i * 0.17f)
                drawPath(Path().apply { moveTo(cx - bw / 2, y); for (k in 0..8) quadraticTo(cx - bw / 2 + bw * (k + 0.5f) / 8f, y + (if (k % 2 == 0) -1 else 1) * bh * 0.04f, cx - bw / 2 + bw * (k + 1) / 8f, y) }, Color.White.copy(alpha = 0.45f), style = Stroke(s * 0.008f))
            }
            "pattern_leopard" -> listOf(-0.3f to -0.28f, 0.2f to -0.34f, 0.36f to -0.02f, -0.36f to 0.04f, 0.1f to 0.3f, -0.2f to 0.34f, 0.38f to 0.3f).forEach { (dx, dy) ->
                val c = Offset(cx + bw * dx, cy + bh * dy)
                drawCircle(Color(0xFF6D4C41).copy(alpha = 0.55f), s * 0.026f, c, style = Stroke(s * 0.009f))
                drawCircle(Color(0xFF6D4C41).copy(alpha = 0.3f), s * 0.012f, c)
            }
        }
    }
}
