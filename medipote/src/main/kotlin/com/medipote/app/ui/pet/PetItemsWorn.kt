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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Hats, glasses and neck items added after the first set. Each is placed from PetGeo, so it sits on the body whatever its pose.

private val Dark = Color(0xFF263238)

/** Hats from 1.39: placed on the dome of the head (`g.top` is the very top; its sides are lower). */
internal fun DrawScope.drawHatExtra(id: String, g: PetGeo, base: Color, loop: Float) {
    val s = g.s; val cx = g.cx; val top = g.top; val bw = g.bw
    when (id) {
        "hat_pirate" -> {
            val hat = Path().apply {
                moveTo(cx - bw * 0.36f, top + s * 0.06f)
                quadraticTo(cx - bw * 0.3f, top - s * 0.1f, cx, top - s * 0.095f)
                quadraticTo(cx + bw * 0.3f, top - s * 0.1f, cx + bw * 0.36f, top + s * 0.06f)
                quadraticTo(cx, top + s * 0.012f, cx - bw * 0.36f, top + s * 0.06f)
                close()
            }
            drawPath(hat, Color(0xFF263238))
            drawPath(hat, Color(0xFFFFC107), style = Stroke(s * 0.006f))
            drawCircle(Color(0xFFF5F5F5), s * 0.022f, Offset(cx, top - s * 0.04f))
            drawCircle(Color(0xFF263238), s * 0.005f, Offset(cx - s * 0.008f, top - s * 0.043f))
            drawCircle(Color(0xFF263238), s * 0.005f, Offset(cx + s * 0.008f, top - s * 0.043f))
            drawLine(Color(0xFFF5F5F5), Offset(cx - s * 0.03f, top - s * 0.015f), Offset(cx + s * 0.03f, top - s * 0.017f), s * 0.007f, StrokeCap.Round)
        }
        "hat_chef" -> {
            val white = Color(0xFFFFFFFF); val line = Color(0xFFD7DEE2)
            listOf(-0.17f to -0.055f, 0.17f to -0.055f, 0f to -0.095f, -0.09f to -0.085f, 0.09f to -0.085f).forEach { (dx, dy) ->
                drawCircle(white, s * 0.058f, Offset(cx + bw * dx, top + s * dy))
                drawCircle(line, s * 0.058f, Offset(cx + bw * dx, top + s * dy), style = Stroke(s * 0.004f))
            }
            drawRoundRect(white, Offset(cx - bw * 0.27f, top - s * 0.04f), Size(bw * 0.54f, s * 0.08f), CornerRadius(s * 0.01f))
            drawRoundRect(line, Offset(cx - bw * 0.27f, top - s * 0.04f), Size(bw * 0.54f, s * 0.08f), CornerRadius(s * 0.01f), style = Stroke(s * 0.004f))
        }
        "hat_cowboy" -> {
            val tan = Color(0xFFC68E5B); val dark = Color(0xFF8D5A32)
            drawPath(Path().apply {
                moveTo(cx - bw * 0.62f, top + s * 0.0f)
                quadraticTo(cx - bw * 0.5f, top + s * 0.055f, cx, top + s * 0.05f)
                quadraticTo(cx + bw * 0.5f, top + s * 0.055f, cx + bw * 0.62f, top + s * 0.0f)
                quadraticTo(cx + bw * 0.45f, top + s * 0.015f, cx, top + s * 0.012f)
                quadraticTo(cx - bw * 0.45f, top + s * 0.015f, cx - bw * 0.62f, top + s * 0.0f)
                close()
            }, tan)
            drawPath(Path().apply {
                moveTo(cx - bw * 0.28f, top + s * 0.03f)
                quadraticTo(cx - bw * 0.3f, top - s * 0.09f, cx - bw * 0.1f, top - s * 0.095f)
                quadraticTo(cx, top - s * 0.06f, cx + bw * 0.1f, top - s * 0.095f)
                quadraticTo(cx + bw * 0.3f, top - s * 0.09f, cx + bw * 0.28f, top + s * 0.03f)
                close()
            }, tan)
            drawRect(dark, Offset(cx - bw * 0.285f, top - s * 0.005f), Size(bw * 0.57f, s * 0.026f))
            drawCircle(Color(0xFFFFD54F), s * 0.009f, Offset(cx, top + s * 0.008f))
        }
        "hat_beret" -> {
            val red = Color(0xFFD84A5F)
            rotate(-8f, Offset(cx, top)) {
                drawOval(red, Offset(cx - bw * 0.36f, top - s * 0.05f), Size(bw * 0.78f, s * 0.11f))
                drawOval(Color(0xFFB03047), Offset(cx - bw * 0.36f, top - s * 0.05f), Size(bw * 0.78f, s * 0.11f), style = Stroke(s * 0.004f))
                drawRoundRect(Color(0xFF8E2437), Offset(cx + bw * 0.02f, top - s * 0.075f), Size(s * 0.012f, s * 0.03f), CornerRadius(s * 0.005f))
            }
        }
        "hat_bucket" -> {
            val c = Color(0xFFF2D16B); val dark = Color(0xFFD9B24A)
            drawPath(Path().apply {
                moveTo(cx - bw * 0.27f, top + s * 0.015f); lineTo(cx - bw * 0.21f, top - s * 0.075f)
                lineTo(cx + bw * 0.21f, top - s * 0.075f); lineTo(cx + bw * 0.27f, top + s * 0.015f); close()
            }, c)
            drawOval(c, Offset(cx - bw * 0.46f, top - s * 0.005f), Size(bw * 0.92f, s * 0.07f))
            drawOval(dark, Offset(cx - bw * 0.46f, top - s * 0.005f), Size(bw * 0.92f, s * 0.07f), style = Stroke(s * 0.004f))
            drawRect(Color(0xFF5C8FD6), Offset(cx - bw * 0.265f, top - s * 0.02f), Size(bw * 0.53f, s * 0.022f))
        }
        "hat_sailor" -> {
            val white = Color(0xFFFAFAFA); val blue = Color(0xFF1E4F9C)
            drawArc(white, 180f, 180f, true, Offset(cx - bw * 0.3f, top - s * 0.07f), Size(bw * 0.6f, s * 0.2f))
            drawRoundRect(white, Offset(cx - bw * 0.33f, top + s * 0.005f), Size(bw * 0.66f, s * 0.04f), CornerRadius(s * 0.02f))
            drawRoundRect(blue, Offset(cx - bw * 0.31f, top + s * 0.012f), Size(bw * 0.62f, s * 0.011f), CornerRadius(s * 0.005f))
            drawRoundRect(Color(0xFFD7DEE2), Offset(cx - bw * 0.33f, top + s * 0.005f), Size(bw * 0.66f, s * 0.04f), CornerRadius(s * 0.02f), style = Stroke(s * 0.004f))
            drawCircle(blue, s * 0.012f, Offset(cx, top - s * 0.075f))
        }
        "hat_graduation" -> {
            val black = Color(0xFF263238)
            drawRoundRect(black, Offset(cx - bw * 0.22f, top - s * 0.015f), Size(bw * 0.44f, s * 0.06f), CornerRadius(s * 0.012f))
            val board = Path().apply {
                moveTo(cx, top - s * 0.095f); lineTo(cx + bw * 0.48f, top - s * 0.045f); lineTo(cx, top + s * 0.0f); lineTo(cx - bw * 0.48f, top - s * 0.045f); close()
            }
            drawPath(board, Color(0xFF37474F)); drawPath(board, black, style = Stroke(s * 0.005f))
            drawLine(Color(0xFFFFC107), Offset(cx, top - s * 0.048f), Offset(cx + bw * 0.42f, top - s * 0.042f), s * 0.005f)
            drawLine(Color(0xFFFFC107), Offset(cx + bw * 0.42f, top - s * 0.042f), Offset(cx + bw * 0.42f, top + s * 0.04f), s * 0.005f)
            drawCircle(Color(0xFFFFC107), s * 0.011f, Offset(cx + bw * 0.42f, top + s * 0.045f))
        }
        "hat_viking" -> {
            val iron = Color(0xFF90A4AE); val horn = Color(0xFFFFF3D6)
            for (side in listOf(-1f, 1f)) {
                drawPath(Path().apply {
                    moveTo(cx + side * bw * 0.3f, top + s * 0.07f)
                    quadraticTo(cx + side * bw * 0.52f, top + s * 0.05f, cx + side * bw * 0.5f, top - s * 0.11f)
                    quadraticTo(cx + side * bw * 0.4f, top - s * 0.02f, cx + side * bw * 0.22f, top + s * 0.02f)
                    close()
                }, horn)
                drawPath(Path().apply {
                    moveTo(cx + side * bw * 0.3f, top + s * 0.07f)
                    quadraticTo(cx + side * bw * 0.52f, top + s * 0.05f, cx + side * bw * 0.5f, top - s * 0.11f)
                }, Color(0xFFD9C79E), style = Stroke(s * 0.005f))
            }
            drawArc(iron, 180f, 180f, true, Offset(cx - bw * 0.38f, top - s * 0.1f), Size(bw * 0.76f, s * 0.26f))
            drawRoundRect(Color(0xFF607D8B), Offset(cx - bw * 0.39f, top + s * 0.0f), Size(bw * 0.78f, s * 0.035f), CornerRadius(s * 0.01f))
            drawCircle(Color(0xFFFFC107), s * 0.01f, Offset(cx, top + s * 0.017f))
        }
        "hat_bunny" -> {
            val pink = Color(0xFFFFB3C8)
            for (side in listOf(-1f, 1f)) {
                rotate(side * 9f, Offset(cx + side * bw * 0.17f, top + s * 0.03f)) {
                    val x = cx + side * bw * 0.17f
                    drawOval(base, Offset(x - s * 0.036f, top - s * 0.19f), Size(s * 0.072f, s * 0.23f))
                    drawOval(pink, Offset(x - s * 0.02f, top - s * 0.165f), Size(s * 0.04f, s * 0.17f))
                }
            }
        }
        "hat_bear" -> {
            for (side in listOf(-1f, 1f)) {
                val c = g.dome(0.3f, side, out = 0.012f)
                val dark = androidx.compose.ui.graphics.lerp(base, Color.Black, 0.2f)
                drawCircle(dark, s * 0.052f, c)
                drawCircle(Color(0xFFFFD2B8), s * 0.03f, c + Offset(side * s * 0.003f, s * 0.004f))
            }
        }
        "hat_unicorn" -> {
            val horn = Path().apply { moveTo(cx - s * 0.045f, top + s * 0.015f); lineTo(cx, top - s * 0.2f); lineTo(cx + s * 0.045f, top + s * 0.015f); close() }
            drawPath(horn, Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB74D)), top - s * 0.2f, top + s * 0.015f))
            for (i in 1..4) drawLine(Color.White.copy(alpha = 0.85f), Offset(cx - s * 0.04f * i / 4f, top - s * 0.2f + s * 0.0525f * i), Offset(cx + s * 0.04f * i / 4f, top - s * 0.2f + s * 0.0525f * i - s * 0.012f), s * 0.007f)
            drawStar(Offset(cx + s * 0.1f, top - s * 0.12f), s * 0.02f, Color(0xFFFFF176), 4, 0.35f)
            for (side in listOf(-1f, 1f)) drawPath(Path().apply {
                moveTo(cx + side * bw * 0.2f, top + s * 0.04f); lineTo(cx + side * bw * 0.27f, top - s * 0.04f); lineTo(cx + side * bw * 0.32f, top + s * 0.05f); close()
            }, base)
        }
        "hat_devil" -> {
            val red = Color(0xFFE53935)
            for (side in listOf(-1f, 1f)) drawPath(Path().apply {
                moveTo(cx + side * bw * 0.14f, top + s * 0.03f)
                quadraticTo(cx + side * bw * 0.16f, top - s * 0.06f, cx + side * bw * 0.3f, top - s * 0.09f)
                quadraticTo(cx + side * bw * 0.26f, top - s * 0.01f, cx + side * bw * 0.3f, top + s * 0.05f)
                close()
            }, red)
        }
        "hat_antlers" -> {
            val brown = Color(0xFF8D6E63)
            for (side in listOf(-1f, 1f)) {
                val p0 = g.dome(0.3f, side, out = -0.004f)
                val stroke = Stroke(s * 0.024f, cap = StrokeCap.Round)
                val tip = Offset(p0.x + side * s * 0.11f, p0.y - s * 0.17f)
                drawPath(Path().apply { moveTo(p0.x, p0.y); quadraticTo(p0.x + side * s * 0.01f, p0.y - s * 0.1f, tip.x, tip.y) }, brown, style = stroke)
                drawLine(brown, Offset(p0.x + side * s * 0.03f, p0.y - s * 0.08f), Offset(p0.x - side * s * 0.01f, p0.y - s * 0.15f), s * 0.018f, StrokeCap.Round)
                drawLine(brown, Offset(p0.x + side * s * 0.06f, p0.y - s * 0.12f), Offset(p0.x + side * s * 0.13f, p0.y - s * 0.1f), s * 0.018f, StrokeCap.Round)
            }
        }
        "hat_sombrero" -> {
            val straw = Color(0xFFF1C453); val red = Color(0xFFE53935)
            drawOval(straw, Offset(cx - bw * 0.72f, top - s * 0.01f), Size(bw * 1.44f, s * 0.09f))
            drawOval(Color(0xFFD6A52E), Offset(cx - bw * 0.72f, top - s * 0.01f), Size(bw * 1.44f, s * 0.09f), style = Stroke(s * 0.005f))
            drawPath(Path().apply {
                moveTo(cx - bw * 0.22f, top + s * 0.02f); lineTo(cx - bw * 0.13f, top - s * 0.11f); lineTo(cx + bw * 0.13f, top - s * 0.11f); lineTo(cx + bw * 0.22f, top + s * 0.02f); close()
            }, straw)
            drawRect(red, Offset(cx - bw * 0.2f, top - s * 0.03f), Size(bw * 0.4f, s * 0.025f))
            for (i in -3..3) drawCircle(if (i % 2 == 0) Color(0xFFFFEB3B) else Color(0xFF43A047), s * 0.006f, Offset(cx + i * bw * 0.06f, top + s * 0.04f))
        }
        "hat_fez" -> {
            val red = Color(0xFFC62828)
            drawPath(Path().apply {
                moveTo(cx - bw * 0.2f, top + s * 0.02f); lineTo(cx - bw * 0.15f, top - s * 0.09f); lineTo(cx + bw * 0.15f, top - s * 0.09f); lineTo(cx + bw * 0.2f, top + s * 0.02f); close()
            }, red)
            drawRoundRect(Color(0xFFFFC107), Offset(cx - bw * 0.14f, top - s * 0.1f), Size(bw * 0.28f, s * 0.012f), CornerRadius(s * 0.005f))
            drawLine(Color(0xFF263238), Offset(cx, top - s * 0.095f), Offset(cx + bw * 0.2f, top - s * 0.03f), s * 0.006f)
            for (i in 0..3) drawLine(Color(0xFF263238), Offset(cx + bw * 0.2f, top - s * 0.03f), Offset(cx + bw * (0.19f + i * 0.01f), top + s * (0.03f + i * 0.004f)), s * 0.004f)
        }
        "hat_mushroom" -> {
            val red = Color(0xFFE53935)
            drawArc(red, 180f, 180f, true, Offset(cx - bw * 0.42f, top - s * 0.1f), Size(bw * 0.84f, s * 0.24f))
            drawArc(Color(0xFFB71C1C), 180f, 180f, true, Offset(cx - bw * 0.42f, top - s * 0.1f), Size(bw * 0.84f, s * 0.24f), style = Stroke(s * 0.005f))
            listOf(-0.22f to -0.025f, 0.05f to -0.07f, 0.22f to -0.015f, -0.04f to -0.01f).forEach { (dx, dy) ->
                drawCircle(Color.White, s * 0.02f, Offset(cx + bw * dx, top + s * dy))
            }
        }
        "hat_leaf" -> {
            val leaf = Color(0xFF66BB6A)
            rotate(-12f, Offset(cx, top)) {
                val p = Path().apply {
                    moveTo(cx - bw * 0.36f, top + s * 0.01f)
                    quadraticTo(cx - bw * 0.1f, top - s * 0.17f, cx + bw * 0.42f, top - s * 0.05f)
                    quadraticTo(cx + bw * 0.1f, top + s * 0.07f, cx - bw * 0.36f, top + s * 0.01f)
                    close()
                }
                drawPath(p, leaf)
                drawLine(Color(0xFF2E7D32), Offset(cx - bw * 0.36f, top + s * 0.01f), Offset(cx + bw * 0.3f, top - s * 0.05f), s * 0.006f)
                for (i in 1..3) drawLine(Color(0xFF43A047), Offset(cx - bw * (0.3f - i * 0.15f), top - s * (0.0f + i * 0.01f)), Offset(cx - bw * (0.22f - i * 0.15f), top - s * (0.06f + i * 0.012f)), s * 0.004f)
            }
        }
    }
}

/** Glasses from 1.39: they sit over the eyes at `g.eyeY`, `g.eyeDx` either side of the middle. */
internal fun DrawScope.drawGlassesExtra(id: String, g: PetGeo) {
    val s = g.s; val cx = g.cx; val eyeY = g.eyeY; val dx = g.eyeDx; val r = s * 0.075f
    when (id) {
        "glasses_square" -> {
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * dx, eyeY)
                drawRoundRect(Color.White.copy(alpha = 0.2f), c - Offset(r * 1.05f, r * 0.85f), Size(r * 2.1f, r * 1.7f), CornerRadius(r * 0.25f))
                drawRoundRect(Dark, c - Offset(r * 1.05f, r * 0.85f), Size(r * 2.1f, r * 1.7f), CornerRadius(r * 0.25f), style = Stroke(s * 0.014f))
            }
            drawLine(Dark, Offset(cx - dx + r * 1.05f, eyeY - r * 0.3f), Offset(cx + dx - r * 1.05f, eyeY - r * 0.3f), s * 0.012f)
        }
        "glasses_cateye" -> {
            val plum = Color(0xFF8E24AA)
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * dx, eyeY)
                val p = Path().apply {
                    moveTo(c.x - side * r * 1.0f, c.y - r * 0.2f)
                    quadraticTo(c.x - side * r * 0.3f, c.y - r * 1.05f, c.x + side * r * 1.15f, c.y - r * 0.95f)
                    quadraticTo(c.x + side * r * 1.1f, c.y + r * 0.8f, c.x - side * r * 0.2f, c.y + r * 0.85f)
                    quadraticTo(c.x - side * r * 1.1f, c.y + r * 0.7f, c.x - side * r * 1.0f, c.y - r * 0.2f)
                    close()
                }
                drawPath(p, Color.White.copy(alpha = 0.2f)); drawPath(p, plum, style = Stroke(s * 0.014f))
                drawCircle(Color(0xFFFFD54F), s * 0.007f, Offset(c.x + side * r * 1.05f, c.y - r * 0.9f))
            }
            drawLine(plum, Offset(cx - dx + r * 0.9f, eyeY - r * 0.2f), Offset(cx + dx - r * 0.9f, eyeY - r * 0.2f), s * 0.011f)
        }
        "glasses_aviator" -> {
            val gold = Color(0xFFD4A017)
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * dx, eyeY)
                val p = Path().apply {
                    moveTo(c.x - r * 1.0f, c.y - r * 0.8f); lineTo(c.x + r * 1.0f, c.y - r * 0.8f)
                    quadraticTo(c.x + r * 1.0f, c.y + r * 1.1f, c.x, c.y + r * 1.05f)
                    quadraticTo(c.x - r * 1.0f, c.y + r * 1.1f, c.x - r * 1.0f, c.y - r * 0.8f)
                    close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xCC4E342E), Color(0x66A1887F)), c.y - r * 0.8f, c.y + r * 1.05f))
                drawPath(p, gold, style = Stroke(s * 0.011f))
                drawLine(Color.White.copy(alpha = 0.55f), c + Offset(-r * 0.6f, -r * 0.45f), c + Offset(-r * 0.15f, -r * 0.5f), s * 0.007f, StrokeCap.Round)
            }
            drawLine(gold, Offset(cx - dx + r, eyeY - r * 0.6f), Offset(cx + dx - r, eyeY - r * 0.6f), s * 0.01f)
        }
        "glasses_swim" -> {
            // Goggles with a strap that goes round to the sides of the head.
            val strap = Color(0xFF1E88E5)
            drawLine(strap, Offset(cx - g.hw * 0.97f, eyeY), Offset(cx - dx - r * 1.15f, eyeY), s * 0.02f)
            drawLine(strap, Offset(cx + g.hw * 0.97f, eyeY), Offset(cx + dx + r * 1.15f, eyeY), s * 0.02f)
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * dx, eyeY)
                drawOval(Color(0xFF81D4FA).copy(alpha = 0.55f), c - Offset(r * 1.1f, r * 0.95f), Size(r * 2.2f, r * 1.9f))
                drawOval(strap, c - Offset(r * 1.1f, r * 0.95f), Size(r * 2.2f, r * 1.9f), style = Stroke(s * 0.016f))
                drawLine(Color.White.copy(alpha = 0.7f), c + Offset(-r * 0.6f, -r * 0.45f), c + Offset(-r * 0.2f, -r * 0.6f), s * 0.007f, StrokeCap.Round)
            }
            drawLine(strap, Offset(cx - dx + r * 1.1f, eyeY), Offset(cx + dx - r * 1.1f, eyeY), s * 0.012f)
        }
        "glasses_flower" -> {
            val petals = listOf(Color(0xFFFF8A80), Color(0xFFFFD54F))
            for ((i, side) in listOf(-1f, 1f).withIndex()) {
                val c = Offset(cx + side * dx, eyeY)
                for (k in 0 until 8) {
                    val a = k * PI / 4
                    drawCircle(petals[i].copy(alpha = 0.92f), r * 0.55f, c + Offset((cos(a) * r * 0.85f).toFloat(), (sin(a) * r * 0.85f).toFloat()))
                }
                drawCircle(Color.White.copy(alpha = 0.3f), r * 0.78f, c)
                drawCircle(Color(0xFFFF7043), r * 0.78f, c, style = Stroke(s * 0.008f))
            }
            drawLine(Color(0xFFFF7043), Offset(cx - dx + r * 1.2f, eyeY), Offset(cx + dx - r * 1.2f, eyeY), s * 0.009f)
        }
        "glasses_moon" -> {
            val gold = Color(0xFFFFD54F)
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * dx, eyeY)
                // A crescent around each eye: a disc with a bite taken out towards the middle of the face.
                val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c - Offset(r * 1.15f, r * 1.15f), Size(r * 2.3f, r * 2.3f))) }
                val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(-side * r * 0.45f, -r * 0.1f) - Offset(r * 1.0f, r * 1.0f), Size(r * 2.0f, r * 2.0f))) }
                val crescent = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, disc, bite)
                drawPath(crescent, gold)
                drawPath(crescent, Color(0xFFE0A800), style = Stroke(s * 0.004f))
                drawStar(c + Offset(side * r * 0.95f, -r * 0.95f), r * 0.28f, Color(0xFFFFF8E1), 4, 0.35f)
            }
            drawLine(gold, Offset(cx - dx + r * 0.8f, eyeY - r * 0.5f), Offset(cx + dx - r * 0.8f, eyeY - r * 0.5f), s * 0.008f)
        }
        "glasses_rainbow" -> {
            val bands = listOf(Color(0xFFFF5252), Color(0xFFFFB74D), Color(0xFFFFEE58), Color(0xFF66BB6A), Color(0xFF42A5F5), Color(0xFF9575CD))
            for (side in listOf(-1f, 1f)) {
                val c = Offset(cx + side * dx, eyeY)
                bands.forEachIndexed { i, col -> drawCircle(col.copy(alpha = 0.85f), r * (1.1f - i * 0.12f), c, style = Stroke(s * 0.01f)) }
            }
            drawLine(Color(0xFF9575CD), Offset(cx - dx + r * 1.1f, eyeY - r * 0.2f), Offset(cx + dx - r * 1.1f, eyeY - r * 0.2f), s * 0.009f)
        }
        "glasses_ski" -> {
            val strap = Color(0xFF37474F)
            drawLine(strap, Offset(cx - g.hw * 0.97f, eyeY), Offset(cx - dx - r * 1.3f, eyeY), s * 0.028f)
            drawLine(strap, Offset(cx + g.hw * 0.97f, eyeY), Offset(cx + dx + r * 1.3f, eyeY), s * 0.028f)
            val shield = androidx.compose.ui.geometry.Rect(Offset(cx - dx - r * 1.3f, eyeY - r * 1.0f), Size((dx + r * 1.3f) * 2, r * 2.0f))
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFFF7043), Color(0xFFFFCA28)), shield.left, shield.right), shield.topLeft, shield.size, CornerRadius(r * 0.9f))
            drawRoundRect(strap, shield.topLeft, shield.size, CornerRadius(r * 0.9f), style = Stroke(s * 0.014f))
            drawLine(Color.White.copy(alpha = 0.6f), Offset(shield.left + r * 0.5f, shield.top + r * 0.45f), Offset(shield.left + r * 1.3f, shield.top + r * 0.35f), s * 0.008f, StrokeCap.Round)
        }
    }
}

/** Neck items from 1.39: worn on the line just under the mouth (`y`), never lower than the body. */
internal fun DrawScope.drawNeckExtra(id: String, g: PetGeo, loop: Float) {
    val s = g.s; val cx = g.cx; val bw = g.bw; val bh = g.bh
    val y = g.cy + bh * 0.3f
    fun chain(t: Float): Offset = Offset(cx - bw * 0.32f + bw * 0.64f * t, y - bh * 0.1f + sin(t * PI).toFloat() * bh * 0.13f)
    when (id) {
        "neck_lei" -> {
            val colors = listOf(Color(0xFFF06292), Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFFFFEE58), Color(0xFF4DD0E1))
            drawPath(Path().apply { moveTo(chain(0f).x, chain(0f).y); for (i in 1..20) chain(i / 20f).let { lineTo(it.x, it.y) } }, Color(0xFF43A047), style = Stroke(s * 0.01f))
            for (i in 0..6) {
                val c = chain(i / 6f)
                for (k in 0 until 5) {
                    val a = k * 2 * PI / 5
                    drawCircle(colors[i % colors.size], s * 0.016f, c + Offset((cos(a) * s * 0.02f).toFloat(), (sin(a) * s * 0.02f).toFloat()))
                }
                drawCircle(Color(0xFFFFF59D), s * 0.01f, c)
            }
        }
        "neck_bell" -> {
            val red = Color(0xFFE53935)
            drawPath(Path().apply { moveTo(chain(0f).x, chain(0f).y); for (i in 1..20) chain(i / 20f).let { lineTo(it.x, it.y) } }, red, style = Stroke(s * 0.016f, cap = StrokeCap.Round))
            val b = Offset(cx, y + bh * 0.06f)
            drawCircle(Color(0xFFFFC107), s * 0.026f, b); drawCircle(Color(0xFFE09B00), s * 0.026f, b, style = Stroke(s * 0.004f))
            drawLine(Color(0xFFE09B00), b + Offset(-s * 0.02f, s * 0.004f), b + Offset(s * 0.02f, s * 0.004f), s * 0.004f)
            drawCircle(Color(0xFF8D5A00), s * 0.005f, b + Offset(0f, s * 0.014f))
        }
        "neck_stripescarf" -> {
            val band = Path().apply {
                moveTo(cx - bw * 0.42f, y - bh * 0.08f)
                quadraticTo(cx, y + bh * 0.04f, cx + bw * 0.42f, y - bh * 0.08f)
                lineTo(cx + bw * 0.4f, y + bh * 0.04f)
                quadraticTo(cx, y + bh * 0.16f, cx - bw * 0.4f, y + bh * 0.04f)
                close()
            }
            drawPath(band, Color(0xFFFAFAFA))
            clipPath(band) { for (i in -5..5) drawLine(Color(0xFF3F6FD1), Offset(cx + i * bw * 0.09f, y - bh * 0.15f), Offset(cx + i * bw * 0.09f + bw * 0.02f, y + bh * 0.25f), s * 0.016f) }
            drawPath(band, Color(0xFF3F6FD1), style = Stroke(s * 0.004f))
            drawRoundRect(Color(0xFF3F6FD1), Offset(cx + bw * 0.14f, y + bh * 0.01f), Size(bw * 0.1f, bh * 0.17f), CornerRadius(s * 0.008f))
            drawRoundRect(Color.White, Offset(cx + bw * 0.14f, y + bh * 0.085f), Size(bw * 0.1f, bh * 0.03f))
        }
        "neck_bib" -> {
            val p = Path().apply {
                moveTo(cx - bw * 0.2f, y - bh * 0.1f)
                quadraticTo(cx, y + bh * 0.0f, cx + bw * 0.2f, y - bh * 0.1f)
                lineTo(cx + bw * 0.24f, y + bh * 0.1f)
                quadraticTo(cx, y + bh * 0.24f, cx - bw * 0.24f, y + bh * 0.1f)
                close()
            }
            drawPath(p, Color(0xFFFFFFFF)); drawPath(p, Color(0xFF90CAF9), style = Stroke(s * 0.006f))
            drawHeart(Offset(cx, y + bh * 0.07f), s * 0.026f, Color(0xFFFF7A9A))
            drawLine(Color(0xFF90CAF9), Offset(cx - bw * 0.2f, y - bh * 0.1f), Offset(cx - bw * 0.27f, y - bh * 0.15f), s * 0.006f, StrokeCap.Round)
            drawLine(Color(0xFF90CAF9), Offset(cx + bw * 0.2f, y - bh * 0.1f), Offset(cx + bw * 0.27f, y - bh * 0.15f), s * 0.006f, StrokeCap.Round)
        }
        "neck_pendant" -> {
            val gold = Color(0xFFFFC107)
            drawPath(Path().apply { moveTo(cx - bw * 0.2f, y - bh * 0.14f); quadraticTo(cx, y + bh * 0.1f, cx + bw * 0.2f, y - bh * 0.14f) }, gold, style = Stroke(s * 0.006f, cap = StrokeCap.Round))
            drawHeart(Offset(cx, y + bh * 0.1f), s * 0.034f, Color(0xFFE91E63))
            drawHeart(Offset(cx - s * 0.006f, y + bh * 0.09f), s * 0.012f, Color.White.copy(alpha = 0.5f))
        }
        "neck_fur" -> {
            for (i in 0..8) {
                val t = i / 8f
                val c = Offset(cx - bw * 0.4f + bw * 0.8f * t, y - bh * 0.09f + sin(t * PI).toFloat() * bh * 0.08f)
                drawCircle(Color(0xFFFFFFFF), s * 0.04f, c)
                drawCircle(Color(0xFFD9E2E8), s * 0.04f, c, style = Stroke(s * 0.004f))
            }
        }
        "neck_lanyard" -> {
            val blue = Color(0xFF3F6FD1)
            drawPath(Path().apply { moveTo(cx - bw * 0.16f, y - bh * 0.15f); lineTo(cx, y + bh * 0.06f); lineTo(cx + bw * 0.16f, y - bh * 0.15f) }, blue, style = Stroke(s * 0.012f, cap = StrokeCap.Round))
            val card = Offset(cx - s * 0.035f, y + bh * 0.05f)
            drawRoundRect(Color.White, card, Size(s * 0.07f, s * 0.085f), CornerRadius(s * 0.008f))
            drawRoundRect(blue, card, Size(s * 0.07f, s * 0.085f), CornerRadius(s * 0.008f), style = Stroke(s * 0.004f))
            drawRect(Color(0xFFE53935), Offset(cx - s * 0.005f, card.y + s * 0.015f), Size(s * 0.01f, s * 0.03f))
            drawRect(Color(0xFFE53935), Offset(cx - s * 0.015f, card.y + s * 0.025f), Size(s * 0.03f, s * 0.01f))
            drawLine(Color(0xFFB0BEC5), Offset(cx - s * 0.025f, card.y + s * 0.06f), Offset(cx + s * 0.025f, card.y + s * 0.06f), s * 0.004f)
        }
        "neck_shell" -> {
            val beads = listOf(Color(0xFFFFCCBC), Color(0xFFFFAB91), Color(0xFFFFF3E0))
            for (i in 0..10) drawCircle(beads[i % 3], s * 0.014f, chain(i / 10f))
            val c = Offset(cx, y + bh * 0.07f)
            val shell = Path().apply { moveTo(c.x, c.y + s * 0.03f); quadraticTo(c.x - s * 0.04f, c.y - s * 0.005f, c.x - s * 0.02f, c.y - s * 0.03f); quadraticTo(c.x, c.y - s * 0.04f, c.x + s * 0.02f, c.y - s * 0.03f); quadraticTo(c.x + s * 0.04f, c.y - s * 0.005f, c.x, c.y + s * 0.03f); close() }
            drawPath(shell, Color(0xFFFFB6A0)); drawPath(shell, Color(0xFFE57373), style = Stroke(s * 0.004f))
            for (k in -1..1) drawLine(Color(0xFFE57373), Offset(c.x, c.y + s * 0.028f), Offset(c.x + k * s * 0.017f, c.y - s * 0.028f), s * 0.003f)
        }
        "neck_lifebuoy" -> {
            // A ring round the belly: red and white.
            val c = Offset(cx, g.cy + bh * 0.3f)
            val w = bw * 1.04f; val h = bh * 0.34f
            drawOval(Color(0xFFE53935), c - Offset(w / 2, h / 2), Size(w, h), style = Stroke(s * 0.04f))
            for (k in 0 until 4) {
                val a = k * PI / 2 + PI / 4
                val p = c + Offset((cos(a) * w / 2).toFloat(), (sin(a) * h / 2).toFloat())
                drawCircle(Color.White, s * 0.02f, p)
            }
            drawOval(Color(0xFFB71C1C), c - Offset(w / 2 + s * 0.02f, h / 2 + s * 0.02f), Size(w + s * 0.04f, h + s * 0.04f), style = Stroke(s * 0.004f))
        }
        "neck_snowflake" -> {
            val c = Offset(cx, y + bh * 0.09f)
            drawPath(Path().apply { moveTo(cx - bw * 0.2f, y - bh * 0.14f); quadraticTo(cx, y + bh * 0.04f, cx + bw * 0.2f, y - bh * 0.14f) }, Color(0xFFB0BEC5), style = Stroke(s * 0.005f, cap = StrokeCap.Round))
            for (k in 0 until 3) rotate(k * 60f, c) { drawLine(Color(0xFF4FC3F7), c - Offset(0f, s * 0.05f), c + Offset(0f, s * 0.05f), s * 0.01f, StrokeCap.Round) }
            drawCircle(Color(0xFFE1F5FE), s * 0.012f, c)
        }
    }
}
