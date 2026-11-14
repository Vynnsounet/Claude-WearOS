package com.medipote.app.ui.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medipote.app.data.GameState
import com.medipote.app.domain.PetMood
import com.medipote.app.domain.Shop
import com.medipote.app.domain.ShopItem
import com.medipote.app.domain.Slot
import com.medipote.app.ui.components.PointsBadge
import com.medipote.app.ui.pet.ItemPreview
import com.medipote.app.ui.pet.InteractivePet
import com.medipote.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(game: GameState, onBuy: (ShopItem) -> Unit, onEquip: (ShopItem) -> Unit, animate: Boolean = true) {
    var slot by rememberSaveable { mutableStateOf(Slot.HAT) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedId?.let(Shop::item)
    // Preview: what's equipped, with the selected item tried on.
    val preview = remember(game.equipped, selected) {
        if (selected == null) game.equipped else game.equipped + (selected.slot.name to selected.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boutique") },
                actions = { PointsBadge(game.points, Modifier.padding(end = 16.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(MaterialTheme.shapes.extraLarge)) {
                        InteractivePet(
                            mood = PetMood.HAPPY,
                            equipped = preview,
                            stage = com.medipote.app.domain.Evolution.stageFor(game.growth),
                            talk = { r ->
                                when (r) {
                                    com.medipote.app.domain.PetReaction.JUMP -> listOf("Ça me va bien ?", "Trop stylé !", "On le prend ? 😍", "Nouveau look !").random()
                                    else -> com.medipote.app.domain.PetTalk.line(r, com.medipote.app.data.AppState(game = game), java.time.LocalDateTime.now())
                                }
                            },
                            petName = game.petName,
                            animate = animate,
                            description = "Aperçu de ${game.petName}",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    SelectionBar(game, selected, onBuy, onEquip)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Slot.entries) { s ->
                        FilterChip(selected = s == slot, onClick = { slot = s; selectedId = null }, label = { Text(s.label) })
                    }
                }
            }
            items(Shop.items.filter { it.slot == slot }.sortedBy { it.price }, key = { it.id }) { item ->
                ItemCell(
                    item = item,
                    owned = item.id in game.owned,
                    equipped = game.equipped[item.slot.name] == item.id,
                    selected = item.id == selectedId,
                    affordable = game.points >= item.price,
                    onClick = { selectedId = if (selectedId == item.id) null else item.id },
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(game: GameState, selected: ShopItem?, onBuy: (ShopItem) -> Unit, onEquip: (ShopItem) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selected == null) {
            Text(
                "Touche un objet pour l'essayer sur ${game.petName}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Row
        }
        Column(Modifier.weight(1f)) {
            Text(selected.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (selected.price == 0) "Gratuit" else "${selected.price} points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val owned = selected.id in game.owned
        val equipped = game.equipped[selected.slot.name] == selected.id
        val removable = selected.slot != Slot.COLOR && selected.slot != Slot.BACKGROUND
        when {
            owned && equipped && removable -> OutlinedButton(onClick = { onEquip(selected) }) { Text("Retirer") }
            owned && equipped -> FilledTonalButton(onClick = {}, enabled = false) { Text("Équipé") }
            owned -> FilledTonalButton(onClick = { onEquip(selected) }) { Text("Équiper") }
            game.points >= selected.price -> Button(onClick = { onBuy(selected) }) { Text("Acheter") }
            else -> FilledTonalButton(onClick = {}, enabled = false) {
                Text("Encore ${selected.price - game.points} pts")
            }
        }
    }
}

@Composable
private fun ItemCell(item: ShopItem, owned: Boolean, equipped: Boolean, selected: Boolean, affordable: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
                ItemPreview(item.id, item.slot.name, Modifier.fillMaxSize())
                if (equipped) {
                    Icon(
                        Icons.Rounded.CheckCircle, "Équipé",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    owned -> Text(
                        if (equipped) "Équipé" else "Possédé",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    else -> {
                        Icon(
                            if (affordable) Icons.Rounded.Star else Icons.Rounded.Lock, null,
                            tint = if (affordable) AppTheme.status.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "${item.price}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (affordable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
