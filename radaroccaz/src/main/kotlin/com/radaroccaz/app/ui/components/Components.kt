package com.radaroccaz.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.radaroccaz.app.domain.Verdict
import com.radaroccaz.app.ui.theme.LocalExtraColors

/** Display name and colour of each website. */
object SourceInfo {
    fun name(id: String) = when (id) {
        "leboncoin" -> "Leboncoin"
        "lacentrale" -> "La Centrale"
        "autoscout24" -> "AutoScout24"
        "demo" -> "Démo"
        else -> id
    }

    fun color(id: String) = when (id) {
        "leboncoin" -> Color(0xFFEC5A13)
        "lacentrale" -> Color(0xFF1B64C8)
        "autoscout24" -> Color(0xFFD9A400)
        "demo" -> Color(0xFF7C4DFF)
        else -> Color(0xFF6B7280)
    }
}

@Composable
fun verdictColors(verdict: Verdict): Pair<Color, Color> {
    val x = LocalExtraColors.current
    return when (verdict) {
        Verdict.EXCELLENT -> x.great to x.greatContainer
        Verdict.GOOD -> x.good to x.goodContainer
        Verdict.FAIR -> x.fair to x.fairContainer
        Verdict.WEAK -> x.weak to x.weakContainer
    }
}

/** Circular gauge with the score in the middle. */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, stroke: Dp = 4.dp) {
    val (color, track) = verdictColors(Verdict.of(score))
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val arcSize = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * score / 100f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(
            "$score",
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.34f).sp,
        )
    }
}

/** "−12 % vs marché" in green, "+9 %" in red, "Prix du marché" in between. */
@Composable
fun DealBadge(dealPercent: Int?, modifier: Modifier = Modifier, long: Boolean = false) {
    if (dealPercent == null) return
    val x = LocalExtraColors.current
    val (fg, bg, text) = when {
        dealPercent <= -12 -> Triple(x.great, x.greatContainer, "−${-dealPercent} %" + if (long) " sous le marché" else "")
        dealPercent <= -4 -> Triple(x.good, x.goodContainer, "−${-dealPercent} %" + if (long) " sous le marché" else "")
        dealPercent < 4 -> Triple(x.fair, x.fairContainer, "Prix du marché")
        else -> Triple(x.weak, x.weakContainer, "+$dealPercent %" + if (long) " au-dessus du marché" else "")
    }
    Pill(text, fg, bg, modifier)
}

@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SourceBadge(sourceId: String, modifier: Modifier = Modifier, extra: Int = 0) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(SourceInfo.color(sourceId)))
        Spacer(Modifier.width(5.dp))
        Text(
            SourceInfo.name(sourceId) + if (extra > 0) " +$extra" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Photo with a neutral car placeholder while loading, on error or without photo. */
@Composable
fun ListingImage(url: String?, modifier: Modifier = Modifier, iconSize: Dp = 36.dp) {
    val placeholder = @Composable {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainer)),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DirectionsCar, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(iconSize))
        }
    }
    Box(modifier) {
        if (url == null) placeholder()
        else SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondary: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
        secondary?.invoke()
    }
}
