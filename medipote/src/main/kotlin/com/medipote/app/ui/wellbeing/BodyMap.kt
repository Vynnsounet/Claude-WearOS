package com.medipote.app.ui.wellbeing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medipote.app.domain.BodyView
import com.medipote.app.domain.BodyZone

/** Where each zone sits on a 100 × 220 drawing seen from the front; the back view is its mirror image. */
private fun frontRect(zone: BodyZone): Rect = when (zone) {
    BodyZone.HEAD -> Rect(38f, 2f, 62f, 28f)
    BodyZone.NECK -> Rect(44f, 29f, 56f, 38f)
    BodyZone.SHOULDER_R -> Rect(22f, 38f, 38f, 52f)
    BodyZone.SHOULDER_L -> Rect(62f, 38f, 78f, 52f)
    BodyZone.CHEST -> Rect(38f, 40f, 62f, 70f)
    BodyZone.ABDOMEN -> Rect(38f, 71f, 62f, 92f)
    BodyZone.PELVIS -> Rect(36f, 93f, 64f, 112f)
    BodyZone.ARM_R -> Rect(8f, 54f, 24f, 112f)
    BodyZone.ARM_L -> Rect(76f, 54f, 92f, 112f)
    BodyZone.LEG_R -> Rect(36f, 114f, 49f, 214f)
    BodyZone.LEG_L -> Rect(51f, 114f, 64f, 214f)
    BodyZone.UPPER_BACK -> Rect(38f, 40f, 62f, 66f)
    BodyZone.LOWER_BACK -> Rect(38f, 67f, 62f, 92f)
    BodyZone.GLUTES -> Rect(36f, 93f, 64f, 112f)
}

/**
 * Seen from the front, the person's right is on the viewer's left; from the back it is the other way round,
 * so "Épaule droite" is always the person's own right shoulder.
 */
internal fun zoneRect(zone: BodyZone, view: BodyView): Rect {
    val r = frontRect(zone)
    val right = zone.id.endsWith("_r"); val left = zone.id.endsWith("_l")
    val mirror = view == BodyView.BACK && (right || left)
    // In the drawing, "_r" zones are on the left (front view); on the back they move to the right.
    return if (mirror) Rect(100f - r.right, r.top, 100f - r.left, r.bottom) else r
}

/** The zone under a touch at ([x], [y]) in drawing units; the smallest one wins when shapes touch. */
internal fun zoneAt(view: BodyView, x: Float, y: Float): BodyZone? =
    BodyZone.entries.filter { view in it.views }.filter { zoneRect(it, view).let { r -> x in r.left..r.right && y in r.top..r.bottom } }
        .minByOrNull { zoneRect(it, view).let { r -> r.width * r.height } }

/**
 * A simple body to touch: front and back, one tap per place, a second tap removes it. The same places are
 * listed below as buttons, for those who prefer them or use a screen reader.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyMap(selected: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf(BodyView.FRONT) }
    val toggle = { zone: BodyZone -> onChange(if (zone.id in selected) selected - zone.id else selected + zone.id) }
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val picked = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = view == BodyView.FRONT, onClick = { view = BodyView.FRONT }, label = { Text("De face") })
            FilterChip(selected = view == BodyView.BACK, onClick = { view = BodyView.BACK }, label = { Text("De dos") })
        }
        Canvas(
            Modifier.width(150.dp).height(330.dp)
                .semantics { contentDescription = "Schéma du corps, vue ${if (view == BodyView.FRONT) "de face" else "de dos"}. Touche l'endroit qui te fait mal." }
                .pointerInput(view) {
                    detectTapGestures { p ->
                        val unit = size.width / 100f
                        zoneAt(view, p.x / unit, p.y / unit)?.let(toggle)
                    }
                },
        ) {
            val unit = size.width / 100f
            BodyZone.entries.filter { view in it.views }.forEach { zone ->
                val r = zoneRect(zone, view)
                val tl = Offset(r.left * unit, r.top * unit)
                val sz = Size(r.width * unit, r.height * unit)
                val corner = CornerRadius(minOf(sz.width, sz.height) / 2.2f)
                drawRoundRect(if (zone.id in selected) picked else base, tl, sz, corner)
                drawRoundRect(edge, tl, sz, corner, style = Stroke(width = 1.5f))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            BodyZone.entries.filter { view in it.views }.forEach { zone ->
                FilterChip(selected = zone.id in selected, onClick = { toggle(zone) }, label = { Text(zone.label) })
            }
        }
        val other = selected.mapNotNull { id -> BodyZone.entries.firstOrNull { it.id == id } }.filter { view !in it.views }
        if (other.isNotEmpty()) {
            Text(
                "Aussi, de l'autre côté : ${other.joinToString(", ") { it.label.lowercase() }}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
