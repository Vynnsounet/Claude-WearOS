package com.medipote.app.ui.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/**
 * Where the parts of the companion are, for the things put on it. The body is a dome (its outline is the cubic of `drawPetBody`),
 * the eyes, mouth and feet sit at fixed fractions of it: everything worn is placed from here, so it follows the squash and hop.
 */
internal class PetGeo(val cx: Float, val cy: Float, val bw: Float, val bh: Float, val s: Float) {
    val hw: Float get() = bw / 2
    val top: Float get() = cy - bh / 2
    val bottom: Float get() = cy + bh / 2
    val eyeY: Float get() = cy - bh * 0.06f
    val eyeDx: Float get() = bw * 0.19f
    val mouth: Offset get() = Offset(cx, cy + bh * 0.1f)

    /**
     * A point on the upper outline of the body: [t] = 0 at the top, 1 at the widest point (the side, a little below the middle);
     * [side] = -1 for the left and 1 for the right; [out] pushes it away from the body, as a fraction of the size.
     */
    fun dome(t: Float, side: Float, out: Float = 0f): Offset {
        val p0 = Offset(cx, top)
        val p1 = Offset(cx + hw * 0.6f, top)
        val p2 = Offset(cx + hw, cy - bh * 0.25f)
        val p3 = Offset(cx + hw, cy + bh * 0.08f)
        val u = 1 - t
        val x = u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x
        val y = u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y
        val centre = Offset(cx, cy + bh * 0.1f)
        val dx = (x - cx) * side
        val v = Offset(dx, y - centre.y)
        val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y).coerceAtLeast(1f)
        return Offset(cx + dx + v.x / len * out * s, y + v.y / len * out * s)
    }

    /** The dome from the left at [t] over the top to the right at [t]: a band that hugs the head. */
    fun band(t: Float, out: Float = 0f, steps: Int = 24): Path = Path().apply {
        val pts = (steps downTo 0).map { dome(t * it / steps, -1f, out) } + (1..steps).map { dome(t * it / steps, 1f, out) }
        moveTo(pts.first().x, pts.first().y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
    }
}
