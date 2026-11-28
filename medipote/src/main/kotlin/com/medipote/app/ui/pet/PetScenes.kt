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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The scenes added in 1.39. Like the others they fill the picture; the floor (where the companion stands) is at about 0.72 of the height. */
internal val EXTRA_SCENES = setOf("bg_forest", "bg_desert", "bg_mountain", "bg_library", "bg_kitchen", "bg_garden", "bg_sunset", "bg_clouds")

/** Scenes lit by their own light: no tint for the time of day. */
internal val OWN_LIGHT_SCENES = setOf("bg_sunset", "bg_library", "bg_kitchen")

internal fun DrawScope.drawSceneExtra(id: String?) {
    val w = size.width
    val h = size.height
    when (id) {
        "bg_forest" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFBFE8C9), Color(0xFFE9F7DC))))
            for (i in 0 until 4) drawPath(
                Path().apply { moveTo(w * (0.2f + i * 0.2f), 0f); lineTo(w * (0.28f + i * 0.2f), 0f); lineTo(w * (0.5f + i * 0.2f), h * 0.75f); lineTo(w * (0.3f + i * 0.2f), h * 0.75f); close() },
                Color(0x22FFF59D),
            )
            listOf(0.06f to 0.9f, 0.94f to 1.0f).forEach { (x, k) ->
                drawRect(Color(0xFF7B5A3C), Offset(w * x - w * 0.035f * k, h * 0.05f), Size(w * 0.07f * k, h * 0.7f))
                drawCircle(Color(0xFF4FA36A), w * 0.17f * k, Offset(w * x, h * 0.12f))
                drawCircle(Color(0xFF3E8E5A), w * 0.12f * k, Offset(w * x + (if (x < 0.5f) w * 0.1f else -w * 0.1f), h * 0.18f))
            }
            drawOval(Color(0xFF6FBF73), Offset(-w * 0.2f, h * 0.72f), Size(w * 1.4f, h * 0.5f))
            drawOval(Color(0xFF8ED08B), Offset(w * 0.1f, h * 0.8f), Size(w * 0.8f, h * 0.3f))
            for (i in 0 until 5) { // ferns and mushrooms
                val x = w * (0.12f + i * 0.2f)
                drawArc(Color(0xFF2E7D32), 200f, 140f, false, Offset(x - w * 0.03f, h * 0.84f), Size(w * 0.06f, h * 0.06f), style = Stroke(w * 0.006f, cap = StrokeCap.Round))
            }
            drawArc(Color(0xFFE53935), 180f, 180f, true, Offset(w * 0.8f, h * 0.83f), Size(w * 0.06f, h * 0.05f))
            drawRect(Color(0xFFFFF3E0), Offset(w * 0.822f, h * 0.855f), Size(w * 0.016f, h * 0.035f))
        }
        "bg_desert" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFC9A0), Color(0xFFFFEBC9))))
            drawCircle(Color(0xFFFFE082), w * 0.11f, Offset(w * 0.78f, h * 0.2f))
            drawOval(Color(0xFFF2C27B), Offset(-w * 0.3f, h * 0.58f), Size(w * 1.1f, h * 0.4f))
            drawOval(Color(0xFFE8B16A), Offset(w * 0.3f, h * 0.62f), Size(w * 1.0f, h * 0.35f))
            drawRect(Color(0xFFF4CE8F), Offset(0f, h * 0.76f), Size(w, h * 0.24f))
            drawLine(Color(0xFFE0B070), Offset(0f, h * 0.76f), Offset(w, h * 0.76f), w * 0.006f)
            val cactus = Color(0xFF5BAA6F)
            drawRoundRect(cactus, Offset(w * 0.82f, h * 0.5f), Size(w * 0.06f, h * 0.27f), CornerRadius(w * 0.03f))
            drawRoundRect(cactus, Offset(w * 0.76f, h * 0.58f), Size(w * 0.035f, h * 0.1f), CornerRadius(w * 0.018f))
            drawRoundRect(cactus, Offset(w * 0.89f, h * 0.55f), Size(w * 0.035f, h * 0.09f), CornerRadius(w * 0.018f))
            drawCircle(Color(0xFFFF7A9A), w * 0.014f, Offset(w * 0.85f, h * 0.5f))
            drawOval(Color(0xFFC79A66), Offset(w * 0.1f, h * 0.84f), Size(w * 0.1f, h * 0.05f))
            drawOval(Color(0xFFD9AD7A), Offset(w * 0.13f, h * 0.82f), Size(w * 0.06f, h * 0.04f))
            drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.5f), w * 0.012f, Offset(w * 0.3f, h * 0.12f))
        }
        "bg_mountain" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8FD0F4), Color(0xFFE3F4FC))))
            drawCircle(Color(0xFFFFF59D), w * 0.07f, Offset(w * 0.82f, h * 0.15f))
            fun peak(x: Float, top: Float, half: Float, col: Color) = Path().apply { moveTo(w * (x - half), h * 0.74f); lineTo(w * x, h * top); lineTo(w * (x + half), h * 0.74f); close() }.also { drawPath(it, col) }
            peak(0.25f, 0.2f, 0.3f, Color(0xFF8FA6C4))
            peak(0.68f, 0.28f, 0.34f, Color(0xFF7C93B5))
            drawPath(Path().apply { moveTo(w * 0.25f, h * 0.2f); lineTo(w * 0.205f, h * 0.29f); lineTo(w * 0.24f, h * 0.27f); lineTo(w * 0.26f, h * 0.31f); lineTo(w * 0.29f, h * 0.27f); close() }, Color.White)
            drawPath(Path().apply { moveTo(w * 0.68f, h * 0.28f); lineTo(w * 0.635f, h * 0.37f); lineTo(w * 0.67f, h * 0.35f); lineTo(w * 0.69f, h * 0.39f); lineTo(w * 0.72f, h * 0.35f); close() }, Color.White)
            drawOval(Color(0xFF8BC98A), Offset(-w * 0.2f, h * 0.7f), Size(w * 1.4f, h * 0.5f))
            for (x in listOf(0.08f, 0.16f, 0.9f, 0.82f)) {
                drawRect(Color(0xFF6D4C41), Offset(w * x - w * 0.006f, h * 0.7f), Size(w * 0.012f, h * 0.05f))
                drawPath(Path().apply { moveTo(w * x, h * 0.54f); lineTo(w * x - w * 0.04f, h * 0.72f); lineTo(w * x + w * 0.04f, h * 0.72f); close() }, Color(0xFF3E8E5A))
            }
        }
        "bg_library" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFF7E3C4), Color(0xFFEBCB9F))))
            val cols = listOf(Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFD54F), Color(0xFFBA68C8), Color(0xFFFF8A65), Color(0xFF4DB6AC))
            for (row in 0..2) {
                val y = h * (0.1f + row * 0.2f)
                var x = w * 0.04f
                var k = 0
                while (x < w * 0.96f) {
                    val bw = w * (0.018f + (k % 3) * 0.006f); val bh = h * (0.11f + (k % 4) * 0.012f)
                    if (!(x in w * 0.36f..w * 0.64f && row == 0)) drawRect(cols[(k + row) % cols.size], Offset(x, y + h * 0.13f - bh), Size(bw, bh))
                    x += bw + w * 0.004f; k++
                }
                drawRect(Color(0xFFB98A5E), Offset(w * 0.03f, y + h * 0.13f), Size(w * 0.94f, h * 0.015f))
            }
            drawRoundRect(Color(0xFFFFFFFF), Offset(w * 0.4f, h * 0.1f), Size(w * 0.2f, h * 0.12f), CornerRadius(w * 0.01f))
            drawRoundRect(Color(0xFFB98A5E), Offset(w * 0.4f, h * 0.1f), Size(w * 0.2f, h * 0.12f), CornerRadius(w * 0.01f), style = Stroke(w * 0.008f))
            drawCircle(Color(0xFFFFD54F), w * 0.03f, Offset(w * 0.5f, h * 0.16f))
            drawRect(Color(0xFFD9B184), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
            for (i in 0..8) drawLine(Color(0xFFC79A66), Offset(w * i / 8f, h * 0.78f), Offset(w * (i / 8f - 0.1f), h), w * 0.004f)
            drawOval(Color(0xFFE57373).copy(alpha = 0.5f), Offset(w * 0.2f, h * 0.84f), Size(w * 0.6f, h * 0.1f))
        }
        "bg_kitchen" -> {
            drawRect(Color(0xFFEAF6F6))
            for (r in 0..5) for (c in 0..9) drawRect(Color(0xFFD3E9EA), Offset(w * c * 0.1f + (if (r % 2 == 0) 0f else w * 0.05f) - w * 0.05f, h * r * 0.11f), Size(w * 0.098f, h * 0.108f), style = Stroke(w * 0.002f))
            val win = androidx.compose.ui.geometry.Rect(Offset(w * 0.36f, h * 0.12f), Size(w * 0.28f, h * 0.26f))
            drawRoundRect(Color(0xFFBFE6FA), win.topLeft, win.size, CornerRadius(w * 0.02f))
            drawRoundRect(Color(0xFFFFFFFF), win.topLeft, win.size, CornerRadius(w * 0.02f), style = Stroke(w * 0.012f))
            drawLine(Color.White, Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), w * 0.008f)
            for (i in 0..2) {
                val x = w * (0.08f + i * 0.08f)
                drawLine(Color(0xFF78909C), Offset(x, h * 0.04f), Offset(x, h * 0.12f), w * 0.004f)
                drawCircle(listOf(Color(0xFFB0BEC5), Color(0xFFFFB74D), Color(0xFF90A4AE))[i], w * 0.03f, Offset(x, h * 0.16f))
            }
            drawRect(Color(0xFFB98A5E), Offset(0f, h * 0.62f), Size(w, h * 0.1f))
            drawRect(Color(0xFFD9B184), Offset(0f, h * 0.62f), Size(w, h * 0.02f))
            drawRoundRect(Color(0xFFE57373), Offset(w * 0.78f, h * 0.5f), Size(w * 0.12f, h * 0.12f), CornerRadius(w * 0.01f))
            drawCircle(Color(0xFF81C784), w * 0.02f, Offset(w * 0.84f, h * 0.48f))
            for (r in 0..3) for (c in 0..9) drawRect(if ((r + c) % 2 == 0) Color(0xFFF5F5F5) else Color(0xFFE0A89B), Offset(w * c * 0.1f, h * (0.72f + r * 0.07f)), Size(w * 0.1f, h * 0.07f))
        }
        "bg_garden" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF9ADBF5), Color(0xFFE6F6FC))))
            drawCircle(Color.White.copy(alpha = 0.9f), w * 0.05f, Offset(w * 0.25f, h * 0.15f)); drawCircle(Color.White.copy(alpha = 0.9f), w * 0.06f, Offset(w * 0.3f, h * 0.14f)); drawCircle(Color.White.copy(alpha = 0.9f), w * 0.045f, Offset(w * 0.35f, h * 0.16f))
            drawOval(Color(0xFF7CC47F), Offset(-w * 0.2f, h * 0.7f), Size(w * 1.4f, h * 0.5f))
            for (i in 0..8) { // fence
                val x = w * (0.02f + i * 0.125f)
                drawRoundRect(Color(0xFFFFFFFF), Offset(x, h * 0.5f), Size(w * 0.05f, h * 0.24f), CornerRadius(w * 0.012f))
                drawRoundRect(Color(0xFFE3D5C0), Offset(x, h * 0.5f), Size(w * 0.05f, h * 0.24f), CornerRadius(w * 0.012f), style = Stroke(w * 0.003f))
            }
            drawRect(Color(0xFFFFFFFF), Offset(0f, h * 0.57f), Size(w, h * 0.02f)); drawRect(Color(0xFFFFFFFF), Offset(0f, h * 0.67f), Size(w, h * 0.02f))
            val cols = listOf(Color(0xFFFF7A9A), Color(0xFFFFD54F), Color(0xFFBA68C8), Color(0xFFFF8A65), Color(0xFF4FC3F7))
            for (i in 0 until 14) {
                val x = w * (0.04f + (i * 0.073f)); val y = h * (0.82f + (i % 3) * 0.05f)
                drawLine(Color(0xFF3E8E5A), Offset(x, y), Offset(x, y + h * 0.05f), w * 0.004f)
                for (k in 0 until 5) { val a = k * 2 * PI / 5; drawCircle(cols[i % cols.size], w * 0.012f, Offset(x + (cos(a) * w * 0.016f).toFloat(), y + (sin(a) * w * 0.016f).toFloat())) }
                drawCircle(Color(0xFFFFF59D), w * 0.008f, Offset(x, y))
            }
        }
        "bg_sunset" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF5B3F9A), Color(0xFFE5619C), Color(0xFFFFB36B), Color(0xFFFFE0A3)), 0f, h * 0.75f))
            drawCircle(Color(0xFFFFF3C4), w * 0.12f, Offset(w * 0.5f, h * 0.66f))
            drawCircle(Color(0x55FFE082), w * 0.2f, Offset(w * 0.5f, h * 0.66f))
            drawOval(Color(0xFF7A3F8A), Offset(-w * 0.3f, h * 0.68f), Size(w * 0.9f, h * 0.35f))
            drawOval(Color(0xFF6A3380), Offset(w * 0.35f, h * 0.7f), Size(w * 0.95f, h * 0.35f))
            drawRect(Color(0xFF55286A), Offset(0f, h * 0.76f), Size(w, h * 0.24f))
            listOf(0.1f to 0.1f, 0.3f to 0.07f, 0.72f to 0.12f, 0.9f to 0.06f).forEach { (x, y) -> drawCircle(Color.White.copy(alpha = 0.7f), w * 0.004f, Offset(w * x, h * y)) }
            for (i in 0..2) drawArc(Color(0xFF3B1E4D), 200f, 140f, false, Offset(w * (0.15f + i * 0.08f), h * (0.28f + i * 0.03f)), Size(w * 0.06f, h * 0.03f), style = Stroke(w * 0.004f))
        }
        "bg_clouds" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8FD0F4), Color(0xFFD8EEFB))))
            fun cloud(x: Float, y: Float, k: Float) { listOf(-0.5f to 0f, 0f to -0.25f, 0.5f to 0f, 0.15f to 0.1f, -0.2f to 0.1f).forEach { (dx, dy) -> drawCircle(Color.White, w * 0.06f * k, Offset(w * x + w * 0.07f * k * dx * 2, h * y + h * 0.06f * k * dy * 2)) } }
            cloud(0.2f, 0.2f, 1.0f); cloud(0.75f, 0.14f, 0.8f); cloud(0.55f, 0.36f, 0.6f)
            drawCircle(Color(0xFFFFF59D), w * 0.06f, Offset(w * 0.88f, h * 0.3f))
            for (i in 0..6) drawOval(Color.White, Offset(w * (-0.05f + i * 0.17f), h * 0.74f - (i % 2) * h * 0.02f), Size(w * 0.28f, h * 0.2f))
            drawRect(Color.White, Offset(0f, h * 0.84f), Size(w, h * 0.16f))
            drawOval(Color(0xFFEAF4FB), Offset(w * 0.2f, h * 0.82f), Size(w * 0.6f, h * 0.1f))
            drawStar(Offset(w * 0.12f, h * 0.45f), w * 0.015f, Color(0xFFFFF59D), 4, 0.35f)
        }
    }
}
