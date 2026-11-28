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
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val InkDark = Color(0xFF1D2B28)

/**
 * Toys from 1.39. They stand on the floor to the left of the companion (`g.cx - 0.62 bw`), inside the picture and clear of the plant,
 * or are held by the left hand; [loop] is the 0..1 animation cycle.
 */
internal fun DrawScope.drawToyExtra(id: String, g: PetGeo, loop: Float) {
    val s = g.s
    val ground = g.cy + g.bh * 0.47f
    val x = g.cx - g.bw * 0.62f
    when (id) {
        "toy_book" -> {
            val w = s * 0.14f; val h = s * 0.04f
            drawRoundRect(Color(0xFFEC6F8F), Offset(x - w * 0.45f, ground - h * 2f), Size(w * 0.9f, h), CornerRadius(s * 0.004f))
            drawRoundRect(Color(0xFF3F9E96), Offset(x - w / 2, ground - h), Size(w, h), CornerRadius(s * 0.004f))
            drawRect(Color(0xFFFFF8E7), Offset(x - w / 2 + s * 0.012f, ground - h + s * 0.004f), Size(w - s * 0.014f, h - s * 0.008f))
            drawRect(Color(0xFF2F7F78), Offset(x - w / 2, ground - h), Size(s * 0.012f, h))
            drawRect(Color(0xFFB4506B), Offset(x - w * 0.45f, ground - h * 2f), Size(s * 0.011f, h))
            drawLine(Color(0xFFFFD54F), Offset(x + w * 0.22f, ground - h * 2f), Offset(x + w * 0.22f, ground - h * 2f + s * 0.05f), s * 0.006f)
        }
        "toy_duck" -> {
            val bob = sin(loop * 2 * PI).toFloat() * s * 0.004f
            val yellow = Color(0xFFFFD54F)
            drawOval(yellow, Offset(x - s * 0.07f, ground - s * 0.075f + bob), Size(s * 0.14f, s * 0.075f))
            drawCircle(yellow, s * 0.04f, Offset(x + s * 0.045f, ground - s * 0.09f + bob))
            drawPath(Path().apply { moveTo(x + s * 0.078f, ground - s * 0.09f + bob); lineTo(x + s * 0.115f, ground - s * 0.083f + bob); lineTo(x + s * 0.078f, ground - s * 0.074f + bob); close() }, Color(0xFFFF9800))
            drawCircle(InkDark, s * 0.006f, Offset(x + s * 0.052f, ground - s * 0.1f + bob))
            drawArc(Color(0xFFFFB300), 200f, 150f, false, Offset(x - s * 0.04f, ground - s * 0.07f + bob), Size(s * 0.07f, s * 0.04f), style = Stroke(s * 0.006f))
            drawOval(Color(0x33FFFFFF), Offset(x - s * 0.05f, ground - s * 0.07f + bob), Size(s * 0.05f, s * 0.02f))
        }
        "toy_icecream" -> {
            val tip = Offset(x, ground)
            val cone = Path().apply { moveTo(tip.x, tip.y); lineTo(tip.x - s * 0.04f, tip.y - s * 0.1f); lineTo(tip.x + s * 0.04f, tip.y - s * 0.1f); close() }
            drawPath(cone, Color(0xFFE0A24E))
            for (i in 1..3) drawLine(Color(0xFFB9772A), Offset(tip.x - s * 0.04f + s * 0.02f * i, tip.y - s * 0.1f), Offset(tip.x + s * 0.02f * i - s * 0.02f, tip.y), s * 0.003f)
            drawCircle(Color(0xFF8BE0C7), s * 0.048f, Offset(x, tip.y - s * 0.11f))
            drawCircle(Color(0xFFFF9EBB), s * 0.045f, Offset(x, tip.y - s * 0.15f))
            drawCircle(Color(0xFFE53935), s * 0.013f, Offset(x + s * 0.005f, tip.y - s * 0.2f))
            drawOval(Color(0xFFFF9EBB), Offset(x - s * 0.04f, tip.y - s * 0.115f), Size(s * 0.012f, s * 0.03f))
        }
        "toy_kite" -> {
            val hand = Offset(g.cx - g.bw * 0.52f, g.cy + g.bh * 0.1f)
            val sway = sin(loop * 2 * PI).toFloat() * s * 0.012f
            val k = Offset(g.cx - g.bw * 0.7f + sway, g.cy - g.bh * 0.78f)
            drawPath(Path().apply { moveTo(hand.x, hand.y); quadraticTo(hand.x - s * 0.07f, (hand.y + k.y) / 2, k.x, k.y + s * 0.07f) }, Color(0xFF616161), style = Stroke(s * 0.004f))
            val d = s * 0.07f
            val top = Path().apply { moveTo(k.x, k.y - d); lineTo(k.x + d * 0.75f, k.y); lineTo(k.x, k.y); close() }
            drawPath(top, Color(0xFFFF7043))
            drawPath(Path().apply { moveTo(k.x, k.y - d); lineTo(k.x - d * 0.75f, k.y); lineTo(k.x, k.y); close() }, Color(0xFFFFCA28))
            drawPath(Path().apply { moveTo(k.x, k.y + d * 1.1f); lineTo(k.x + d * 0.75f, k.y); lineTo(k.x, k.y); close() }, Color(0xFF42A5F5))
            drawPath(Path().apply { moveTo(k.x, k.y + d * 1.1f); lineTo(k.x - d * 0.75f, k.y); lineTo(k.x, k.y); close() }, Color(0xFF66BB6A))
            for (i in 1..3) drawCircle(if (i % 2 == 0) Color(0xFFFF7043) else Color(0xFFFFCA28), s * 0.009f, Offset(k.x - sway * 0.5f * i + s * 0.012f * sin(loop * 2 * PI + i).toFloat(), k.y + d * 1.1f + s * 0.025f * i))
        }
        "toy_bouquet" -> {
            val green = Color(0xFF66BB6A)
            val flowers = listOf(Triple(-0.03f, -0.19f, Color(0xFFFF7A9A)), Triple(0.03f, -0.17f, Color(0xFFFFD54F)), Triple(0f, -0.22f, Color(0xFFFF5252)))
            flowers.forEach { (dx, dy, c) ->
                drawLine(green, Offset(x, ground - s * 0.07f), Offset(x + s * dx, ground + s * dy + s * 0.04f), s * 0.006f)
                for (k in 0 until 5) { val a = k * 2 * PI / 5; drawCircle(c, s * 0.014f, Offset(x + s * dx + (cos(a) * s * 0.019f).toFloat(), ground + s * dy + (sin(a) * s * 0.019f).toFloat())) }
                drawCircle(Color(0xFFFFF59D), s * 0.01f, Offset(x + s * dx, ground + s * dy))
            }
            val paper = Path().apply { moveTo(x - s * 0.045f, ground - s * 0.11f); lineTo(x + s * 0.045f, ground - s * 0.11f); lineTo(x + s * 0.012f, ground); lineTo(x - s * 0.012f, ground); close() }
            drawPath(paper, Color(0xFFFFF3E0)); drawPath(paper, Color(0xFFE6C9A8), style = Stroke(s * 0.004f))
            drawPath(Path().apply { moveTo(x - s * 0.02f, ground - s * 0.08f); lineTo(x + s * 0.02f, ground - s * 0.08f) }, Color(0xFFFF7A9A), style = Stroke(s * 0.01f))
        }
        "toy_cactus" -> {
            drawRoundRect(Color(0xFFD87C55), Offset(x - s * 0.04f, ground - s * 0.05f), Size(s * 0.08f, s * 0.05f), CornerRadius(s * 0.008f))
            drawRect(Color(0xFFB9603C), Offset(x - s * 0.045f, ground - s * 0.055f), Size(s * 0.09f, s * 0.012f))
            val green = Color(0xFF4CAF7A)
            drawRoundRect(green, Offset(x - s * 0.025f, ground - s * 0.15f), Size(s * 0.05f, s * 0.1f), CornerRadius(s * 0.025f))
            drawRoundRect(green, Offset(x - s * 0.055f, ground - s * 0.12f), Size(s * 0.03f, s * 0.045f), CornerRadius(s * 0.015f))
            drawRoundRect(green, Offset(x + s * 0.025f, ground - s * 0.13f), Size(s * 0.03f, s * 0.04f), CornerRadius(s * 0.015f))
            for (i in -1..1) drawLine(Color(0xFFE8F5E9), Offset(x + i * s * 0.012f, ground - s * 0.13f), Offset(x + i * s * 0.012f, ground - s * 0.08f), s * 0.003f)
            drawCircle(Color(0xFFFF7A9A), s * 0.013f, Offset(x, ground - s * 0.155f)); drawCircle(Color(0xFFFFF59D), s * 0.005f, Offset(x, ground - s * 0.155f))
        }
        "toy_wateringcan" -> {
            val blue = Color(0xFF4FA3E0)
            drawRoundRect(blue, Offset(x - s * 0.05f, ground - s * 0.09f), Size(s * 0.09f, s * 0.09f), CornerRadius(s * 0.012f))
            drawPath(Path().apply { moveTo(x + s * 0.04f, ground - s * 0.05f); lineTo(x + s * 0.1f, ground - s * 0.11f); lineTo(x + s * 0.11f, ground - s * 0.095f); lineTo(x + s * 0.04f, ground - s * 0.03f); close() }, blue)
            drawArc(blue, 90f, 180f, false, Offset(x - s * 0.085f, ground - s * 0.085f), Size(s * 0.07f, s * 0.07f), style = Stroke(s * 0.01f))
            drawRect(Color(0xFF2F7DB8), Offset(x - s * 0.05f, ground - s * 0.095f), Size(s * 0.09f, s * 0.012f))
            drawOval(Color(0x44FFFFFF), Offset(x - s * 0.04f, ground - s * 0.075f), Size(s * 0.02f, s * 0.05f))
            for (i in 0 until 3) {
                val t = (loop + i / 3f) % 1f
                drawCircle(Color(0xFF81D4FA).copy(alpha = 1f - t), s * 0.007f, Offset(x + s * (0.115f + t * 0.03f), ground - s * 0.1f + t * s * 0.1f))
            }
        }
        "toy_bunnyplush" -> {
            val c = Offset(x, ground - s * 0.05f); val pink = Color(0xFFFFB3C8)
            for (side in listOf(-1f, 1f)) { drawOval(pink, Offset(c.x + side * s * 0.022f - s * 0.014f, c.y - s * 0.17f), Size(s * 0.028f, s * 0.09f)); drawOval(Color(0xFFFFE0EB), Offset(c.x + side * s * 0.022f - s * 0.007f, c.y - s * 0.15f), Size(s * 0.014f, s * 0.06f)) }
            drawCircle(pink, s * 0.05f, c + Offset(0f, s * 0.02f)); drawCircle(pink, s * 0.04f, c - Offset(0f, s * 0.055f))
            drawCircle(Color(0xFFFFE0EB), s * 0.028f, c + Offset(0f, s * 0.03f))
            drawCircle(InkDark, s * 0.005f, c + Offset(-s * 0.014f, -s * 0.06f)); drawCircle(InkDark, s * 0.005f, c + Offset(s * 0.014f, -s * 0.06f))
            drawCircle(Color(0xFFE57399), s * 0.005f, c + Offset(0f, -s * 0.048f))
        }
        "toy_snowman" -> {
            val white = Color.White; val edge = Color(0xFFCFE3EE)
            drawCircle(white, s * 0.05f, Offset(x, ground - s * 0.05f)); drawCircle(edge, s * 0.05f, Offset(x, ground - s * 0.05f), style = Stroke(s * 0.004f))
            drawCircle(white, s * 0.036f, Offset(x, ground - s * 0.116f)); drawCircle(edge, s * 0.036f, Offset(x, ground - s * 0.116f), style = Stroke(s * 0.004f))
            drawCircle(InkDark, s * 0.004f, Offset(x - s * 0.013f, ground - s * 0.122f)); drawCircle(InkDark, s * 0.004f, Offset(x + s * 0.013f, ground - s * 0.122f))
            drawPath(Path().apply { moveTo(x, ground - s * 0.114f); lineTo(x + s * 0.04f, ground - s * 0.108f); lineTo(x, ground - s * 0.104f); close() }, Color(0xFFFF8A3D))
            drawRoundRect(Color(0xFFE53935), Offset(x - s * 0.04f, ground - s * 0.09f), Size(s * 0.08f, s * 0.014f), CornerRadius(s * 0.006f))
            for (i in 0..1) drawCircle(InkDark, s * 0.005f, Offset(x, ground - s * (0.06f - i * 0.02f)))
            drawLine(Color(0xFF6D4C41), Offset(x - s * 0.045f, ground - s * 0.07f), Offset(x - s * 0.085f, ground - s * 0.095f), s * 0.005f, StrokeCap.Round)
            drawLine(Color(0xFF6D4C41), Offset(x + s * 0.045f, ground - s * 0.07f), Offset(x + s * 0.085f, ground - s * 0.095f), s * 0.005f, StrokeCap.Round)
        }
        "toy_cupcake" -> {
            val wrap = Path().apply { moveTo(x - s * 0.045f, ground - s * 0.06f); lineTo(x + s * 0.045f, ground - s * 0.06f); lineTo(x + s * 0.032f, ground); lineTo(x - s * 0.032f, ground); close() }
            drawPath(wrap, Color(0xFFFFCC80))
            for (i in -2..2) drawLine(Color(0xFFE6A24B), Offset(x + i * s * 0.017f, ground - s * 0.06f), Offset(x + i * s * 0.012f, ground), s * 0.004f)
            drawOval(Color(0xFFFF9EBB), Offset(x - s * 0.05f, ground - s * 0.095f), Size(s * 0.1f, s * 0.055f))
            drawOval(Color(0xFFFFB6CB), Offset(x - s * 0.037f, ground - s * 0.125f), Size(s * 0.074f, s * 0.045f))
            drawOval(Color(0xFFFFCDDA), Offset(x - s * 0.022f, ground - s * 0.15f), Size(s * 0.044f, s * 0.035f))
            drawCircle(Color(0xFFE53935), s * 0.013f, Offset(x, ground - s * 0.158f))
            listOf(-0.03f to -0.1f, 0.02f to -0.11f, 0.035f to -0.095f).forEach { (dx, dy) -> drawCircle(Color(0xFFFFEB3B), s * 0.004f, Offset(x + s * dx, ground + s * dy)) }
        }
        "toy_ukulele" -> rotate(-14f, Offset(x, ground)) {
            val wood = Color(0xFFB9783F)
            drawLine(Color(0xFF5D4037), Offset(x, ground - s * 0.045f), Offset(x, ground - s * 0.19f), s * 0.014f, StrokeCap.Round)
            drawRoundRect(Color(0xFF5D4037), Offset(x - s * 0.014f, ground - s * 0.215f), Size(s * 0.028f, s * 0.035f), CornerRadius(s * 0.005f))
            drawOval(wood, Offset(x - s * 0.04f, ground - s * 0.095f), Size(s * 0.08f, s * 0.06f))
            drawOval(wood, Offset(x - s * 0.05f, ground - s * 0.055f), Size(s * 0.1f, s * 0.07f))
            drawCircle(InkDark, s * 0.014f, Offset(x, ground - s * 0.055f))
            drawOval(Color(0x33FFFFFF), Offset(x - s * 0.035f, ground - s * 0.045f), Size(s * 0.025f, s * 0.04f))
            for (i in -1..1) drawLine(Color(0xFFF5F5F5), Offset(x + i * s * 0.004f, ground - s * 0.19f), Offset(x + i * s * 0.004f, ground - s * 0.03f), s * 0.002f)
        }
        "toy_camera" -> {
            val body = Color(0xFF455A64)
            drawRoundRect(body, Offset(x - s * 0.06f, ground - s * 0.08f), Size(s * 0.12f, s * 0.08f), CornerRadius(s * 0.01f))
            drawRoundRect(Color(0xFFECEFF1), Offset(x - s * 0.06f, ground - s * 0.08f), Size(s * 0.12f, s * 0.02f), CornerRadius(s * 0.008f))
            drawCircle(Color(0xFF263238), s * 0.027f, Offset(x, ground - s * 0.04f)); drawCircle(Color(0xFF4FC3F7), s * 0.017f, Offset(x, ground - s * 0.04f))
            drawCircle(Color.White.copy(alpha = 0.8f), s * 0.005f, Offset(x - s * 0.006f, ground - s * 0.046f))
            drawRect(Color(0xFFFFEB3B), Offset(x + s * 0.03f, ground - s * 0.095f), Size(s * 0.022f, s * 0.014f))
            val flash = (sin(loop * 2 * PI).toFloat() + 1) / 2
            drawStar(Offset(x + s * 0.045f, ground - s * 0.12f), s * 0.016f * (0.5f + flash), Color.White.copy(alpha = flash), 4, 0.3f)
        }
        "toy_umbrella" -> {
            val cols = listOf(Color(0xFFFF8A80), Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFF64B5F6))
            val c = Offset(x, ground - s * 0.14f); val r = s * 0.09f
            drawLine(Color(0xFF6D4C41), Offset(x, ground - s * 0.14f), Offset(x, ground - s * 0.015f), s * 0.006f)
            drawArc(Color(0xFF6D4C41), 0f, 180f, false, Offset(x - s * 0.014f, ground - s * 0.04f), Size(s * 0.028f, s * 0.03f), style = Stroke(s * 0.006f))
            for (i in 0 until 4) drawArc(cols[i], 180f + i * 45f, 45f, true, c - Offset(r, r), Size(r * 2, r * 2))
        }
    }
}

/** Effects from 1.39: drawn over the whole picture around the companion, each on its own 0..1 [loop]. */
internal fun DrawScope.drawEffectExtra(id: String, g: PetGeo, loop: Float) {
    val s = g.s; val cx = g.cx; val cy = g.cy; val bw = g.bw; val bh = g.bh
    when (id) {
        "fx_snow" -> for (i in 0 until 16) {
            val t = (loop * (0.7f + (i % 3) * 0.2f) + i / 16f) % 1f
            val x = cx + bw * (-1.0f + i * 0.13f) + sin((t * 2 + i) * PI).toFloat() * s * 0.02f
            val y = cy - bh * 1.05f + t * bh * 1.7f
            drawCircle(Color.White.copy(alpha = 0.9f), s * (0.006f + (i % 3) * 0.003f), Offset(x, y))
        }
        "fx_leaves" -> {
            val cols = listOf(Color(0xFFE65100), Color(0xFFF9A825), Color(0xFFC62828), Color(0xFF8D6E63))
            for (i in 0 until 9) {
                val t = (loop + i / 9f) % 1f
                val x = cx + bw * (-0.95f + i * 0.24f) + sin((t * 2 + i) * PI).toFloat() * s * 0.035f
                val y = cy - bh * 1.0f + t * bh * 1.55f
                rotate(t * 300f + i * 50f, Offset(x, y)) {
                    drawOval(cols[i % cols.size], Offset(x - s * 0.016f, y - s * 0.009f), Size(s * 0.032f, s * 0.018f))
                    drawLine(Color(0x66000000), Offset(x - s * 0.016f, y), Offset(x + s * 0.016f, y), s * 0.002f)
                }
            }
        }
        "fx_petals" -> for (i in 0 until 12) {
            val t = (loop + i / 12f) % 1f
            val x = cx + bw * (-1.0f + i * 0.17f) + t * s * 0.1f
            val y = cy - bh * 1.0f + t * bh * 1.6f
            rotate(t * 240f + i * 30f, Offset(x, y)) { drawOval(Color(0xFFFFB7C9).copy(alpha = 0.9f), Offset(x - s * 0.012f, y - s * 0.007f), Size(s * 0.024f, s * 0.014f)) }
        }
        "fx_notes" -> for (i in 0 until 5) {
            val t = (loop + i / 5f) % 1f
            val x = cx + bw * (-0.75f + i * 0.38f) + sin((t * 2 + i) * PI).toFloat() * s * 0.03f
            val y = cy + bh * 0.1f - t * bh * 1.25f
            val a = sin(t * PI).toFloat()
            val col = listOf(Color(0xFF7E57C2), Color(0xFFEC407A), Color(0xFF29B6F6))[i % 3].copy(alpha = a)
            drawCircle(col, s * 0.014f, Offset(x, y))
            drawLine(col, Offset(x + s * 0.012f, y), Offset(x + s * 0.012f, y - s * 0.05f), s * 0.005f)
            drawPath(Path().apply { moveTo(x + s * 0.012f, y - s * 0.05f); quadraticTo(x + s * 0.035f, y - s * 0.04f, x + s * 0.03f, y - s * 0.02f) }, col, style = Stroke(s * 0.005f))
        }
        "fx_sunrays" -> {
            // Soft beams from the top corner, like light through a window.
            val centre = Offset(size.width * 0.97f, -size.height * 0.06f)
            for (k in 0..4) {
                val a = (118f + k * 11f + sin((loop + k * 0.2f) * 2 * PI).toFloat() * 2f) * (PI / 180).toFloat()
                val len = size.height * 1.25f
                val p = Path().apply {
                    moveTo(centre.x, centre.y)
                    lineTo(centre.x + cos(a - 0.035f) * len, centre.y + sin(a - 0.035f) * len)
                    lineTo(centre.x + cos(a + 0.035f) * len, centre.y + sin(a + 0.035f) * len)
                    close()
                }
                drawPath(p, Brush.radialGradient(listOf(Color(0x40FFF59D), Color(0x00FFF59D)), centre, len))
            }
        }
        "fx_comet" -> for (i in 0 until 2) {
            val t = (loop + i * 0.5f) % 1f
            val a = sin(t * PI).toFloat()
            val head = Offset(cx + bw * (0.9f - t * 2.2f - i * 0.4f), cy - bh * (1.0f - t * 0.3f - i * 0.15f))
            drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f * a), Color.Transparent), head, head + Offset(s * 0.16f, -s * 0.05f)), head, head + Offset(s * 0.16f, -s * 0.05f), s * 0.008f, StrokeCap.Round)
            drawStar(head, s * 0.02f, Color(0xFFFFF59D).copy(alpha = a), 4, 0.35f)
        }
        "fx_orbit" -> for (i in 0 until 3) {
            val a = (loop + i / 3f) * 2 * PI
            val p = Offset(cx + (cos(a) * bw * 0.78f).toFloat(), cy - bh * 0.05f + (sin(a) * bh * 0.2f).toFloat())
            drawStar(p, s * (0.022f + 0.008f * sin(a).toFloat()), Color(0xFFFFE082), 5, 0.45f)
            drawCircle(Color(0x33FFE082), s * 0.035f, p)
        }
    }
}
