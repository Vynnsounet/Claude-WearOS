package com.radaroccaz.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.VehicleOptions
import com.radaroccaz.app.analysis.Analysis
import com.radaroccaz.app.analysis.Severity
import com.radaroccaz.app.domain.ScoredListing
import com.radaroccaz.app.ui.DeepStatus
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import com.radaroccaz.app.ui.components.DealBadge
import com.radaroccaz.app.ui.components.ListingImage
import com.radaroccaz.app.ui.components.Pill
import com.radaroccaz.app.ui.components.ScoreRing
import com.radaroccaz.app.ui.components.SectionCard
import com.radaroccaz.app.ui.components.SourceBadge
import com.radaroccaz.app.ui.components.SourceInfo
import com.radaroccaz.app.ui.components.verdictColors
import com.radaroccaz.app.ui.theme.LocalExtraColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListingDetailScreen(
    listing: Listing,
    scored: ScoredListing?,
    analysis: Analysis,
    wantedOptions: Set<String>,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    deep: DeepStatus? = null,
    onRetryDeep: () -> Unit = {},
    onEditHistory: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
            Gallery(listing, onBack, onShare, onToggleFavorite, favorite)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SourceBadge(listing.source)
                        listing.publishedAt?.let { published(it) }?.let {
                            Text(" · $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(listing.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            Format.price(listing.price),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        DealBadge(scored?.dealPercent, long = true)
                    }
                    scored?.marketPrice?.let {
                        Text(
                            "Prix estimé du marché pour cette année et ce kilométrage : ${Format.price(it / 50 * 50)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (scored != null) ScoreCard(scored, wantedOptions.isNotEmpty())

                ReliabilityCard(analysis, deep, listing.detailsLoaded, onRetryDeep, onEditHistory)
                FindingsCard(
                    "Points de vigilance", Icons.Rounded.Warning,
                    analysis.findings.filter { it.severity == Severity.CRITICAL || it.severity == Severity.WARNING },
                )
                FindingsCard("Points forts", Icons.Rounded.ThumbUp, analysis.positives)
                HistoryFactsCard(analysis)
                analysis.history.reportUrl?.let { ReportLink(it, onOpenUrl) }
                MileageCard(analysis)
                FindingsCard("Autres remarques", Icons.Rounded.Info, analysis.findings.filter { it.severity == Severity.INFO })
                QuestionsCard(analysis.questions)

                SectionCard("Caractéristiques") {
                    val specs = buildList {
                        listing.year?.let { add(Triple(Icons.Rounded.CalendarMonth, "Année", "$it")) }
                        listing.mileageKm?.let { add(Triple(Icons.Rounded.Speed, "Kilométrage", Format.km(it))) }
                        listing.fuel?.let { add(Triple(Icons.Rounded.LocalGasStation, "Carburant", it.label)) }
                        listing.gearbox?.let { add(Triple(Icons.Rounded.Settings, "Boîte", it.label)) }
                        listing.powerHp?.let { add(Triple(Icons.Rounded.Bolt, "Puissance", "$it ch")) }
                        listing.sellerType?.let { add(Triple(Icons.Rounded.Person, "Vendeur", it.label)) }
                        listing.location?.let { add(Triple(Icons.Rounded.Place, "Lieu", it)) }
                    }
                    if (specs.isEmpty()) {
                        Text("Le site ne donne pas de détails structurés : lisez l'annonce.", style = MaterialTheme.typography.bodyMedium)
                    }
                    specs.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            row.forEach { (icon, label, value) -> SpecTile(icon, label, value, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (wantedOptions.isNotEmpty() || listing.options.isNotEmpty()) {
                    SectionCard("Équipements") {
                        val x = LocalExtraColors.current
                        if (wantedOptions.isNotEmpty()) {
                            Text("Vos options", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                wantedOptions.forEach { id ->
                                    val ok = id in listing.options
                                    Pill(
                                        VehicleOptions.label(id),
                                        if (ok) x.great else MaterialTheme.colorScheme.onSurfaceVariant,
                                        if (ok) x.greatContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        icon = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                    )
                                }
                            }
                            Text(
                                "« Non mentionnée » ne veut pas dire absente : vérifiez auprès du vendeur.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        val others = listing.options - wantedOptions
                        if (others.isNotEmpty()) {
                            if (wantedOptions.isNotEmpty()) Spacer(Modifier.height(12.dp))
                            Text("Également mentionné", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                others.forEach { id ->
                                    Pill(VehicleOptions.label(id), MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                                }
                            }
                        }
                    }
                }

                if (listing.description.isNotBlank()) {
                    SectionCard("Description") {
                        var expanded by rememberSaveable { mutableStateOf(false) }
                        Text(
                            listing.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (listing.description.length > 280) {
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Réduire" else "Lire la suite") }
                        }
                    }
                }

                if (listing.alsoOn.isNotEmpty()) {
                    Text(
                        "Également publiée sur " + listing.alsoOn.joinToString(", ") { SourceInfo.name(it) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Button(
                onClick = { onOpenUrl(listing.url) },
                contentPadding = PaddingValues(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("Voir l'annonce sur ${SourceInfo.name(listing.source)}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Gallery(listing: Listing, onBack: () -> Unit, onShare: () -> Unit, onToggleFavorite: () -> Unit, favorite: Boolean) {
    val images = listing.imageUrls.ifEmpty { listOf(null) }
    val pager = rememberPagerState { images.size }
    Box(Modifier.fillMaxWidth().height(290.dp)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            ListingImage(images[page], Modifier.fillMaxSize(), iconSize = 72.dp)
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack)
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Rounded.Share, "Partager", onShare)
            Spacer(Modifier.width(8.dp))
            RoundIcon(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                onToggleFavorite,
                tint = if (favorite) Color(0xFFE53935) else null,
            )
        }
        if (images.size > 1) {
            Pill(
                "${pager.currentPage + 1} / ${images.size}", Color.White, Color.Black.copy(alpha = 0.55f),
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color? = null) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f), shadowElevation = 2.dp) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, tint = tint ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ScoreCard(scored: ScoredListing, showOptions: Boolean) {
    val (color, container) = verdictColors(scored.verdict)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(scored.score, size = 64.dp, stroke = 6.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Pill(scored.verdict.label, color, container)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Note sur 100 selon vos priorités, comparée aux autres annonces trouvées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val b = scored.breakdown
            ScoreBar("Prix et bonne affaire", b.price)
            ScoreBar("Kilométrage", b.mileage)
            ScoreBar("Année", b.age)
            if (showOptions) b.options?.let { ScoreBar("Options souhaitées", it) }
            ScoreBar("Historique et fiabilité", b.history)
        }
    }
}

@Composable
private fun ScoreBar(label: String, value: Double) {
    val pct = (value * 100).toInt()
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$pct", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { value.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = verdictColors(com.radaroccaz.app.domain.Verdict.of(pct)).first,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun SpecTile(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun published(iso: String): String? = try {
    val date = LocalDate.parse(iso.take(10))
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now())
    when {
        days <= 0L -> "publiée aujourd'hui"
        days == 1L -> "publiée hier"
        days < 7 -> "publiée il y a $days jours"
        else -> "publiée le " + date.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
    }
} catch (_: Exception) {
    null
}
