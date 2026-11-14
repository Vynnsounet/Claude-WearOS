package com.radaroccaz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radaroccaz.app.data.Format
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.domain.ScoredListing
import com.radaroccaz.app.ui.theme.LocalExtraColors
import com.radaroccaz.app.ui.theme.PriceStyle

fun Listing.specLine(): String = listOfNotNull(
    year?.toString(),
    mileageKm?.let(Format::km),
    fuel?.label,
    gearbox?.label,
).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListingCard(
    listing: Listing,
    scored: ScoredListing?,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    wantedOptions: Int = 0,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        onClick = onClick,
    ) {
        Row(Modifier.padding(10.dp)) {
            Box(Modifier.size(width = 116.dp, height = 118.dp).clip(RoundedCornerShape(14.dp))) {
                ListingImage(listing.imageUrls.firstOrNull(), Modifier.fillMaxWidth().height(118.dp))
                if (isNew) {
                    Pill(
                        "Nouveau", MaterialTheme.colorScheme.onTertiary, MaterialTheme.colorScheme.tertiary,
                        Modifier.align(Alignment.TopStart).padding(6.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(30.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f))
                        .clickable(role = Role.Button, onClickLabel = if (favorite) "Retirer des favoris" else "Ajouter aux favoris", onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (favorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (scored != null) {
                        Spacer(Modifier.width(8.dp))
                        ScoreRing(scored.score, size = 38.dp, stroke = 3.5.dp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Format.price(listing.price), style = PriceStyle, color = MaterialTheme.colorScheme.primary)
                    DealBadge(scored?.dealPercent)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    listing.specLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(listing.source, extra = listing.alsoOn.size)
                    val where = listOfNotNull(listing.location, listing.sellerType?.let { if (it == SellerType.PRO) "Pro" else "Particulier" })
                    if (where.isNotEmpty()) {
                        Text(
                            " · " + where.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                val x = LocalExtraColors.current
                val owners = listing.history.owners?.value
                val headline = scored?.analysis?.headline
                val strength = scored?.analysis?.positives?.firstOrNull { it.topic == com.radaroccaz.app.analysis.Topic.HISTORY }
                val showOptions = scored != null && wantedOptions > 0
                if (showOptions || owners != null || headline != null || strength != null) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (showOptions) {
                            val all = scored!!.matchedOptions.size == wantedOptions
                            Pill(
                                "${scored.matchedOptions.size}/$wantedOptions options",
                                if (all) x.great else MaterialTheme.colorScheme.onSecondaryContainer,
                                if (all) x.greatContainer else MaterialTheme.colorScheme.secondaryContainer,
                                icon = Icons.Rounded.CheckCircle,
                            )
                        }
                        if (owners != null && headline?.title?.contains("propriétaires") != true) {
                            Pill(
                                if (owners == 1) "1re main" else "$owners propr.",
                                MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer,
                                icon = Icons.Rounded.People,
                            )
                        }
                        when {
                            headline != null -> {
                                val critical = headline.severity == com.radaroccaz.app.analysis.Severity.CRITICAL
                                Pill(
                                    headline.title,
                                    if (critical) x.weak else x.fair,
                                    if (critical) x.weakContainer else x.fairContainer,
                                    icon = if (critical) Icons.Rounded.Error else Icons.Rounded.Warning,
                                )
                            }
                            strength != null && strength.title != "Première main" ->
                                Pill(strength.title, x.great, x.greatContainer, icon = Icons.Rounded.VerifiedUser)
                        }
                    }
                }
            }
        }
    }
}
