package com.radaroccaz.app.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.ui.components.EmptyState
import com.radaroccaz.app.ui.components.ListingCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favorites: List<Listing>,
    onOpen: (Listing) -> Unit,
    onToggleFavorite: (Listing) -> Unit,
    onNewSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Favoris") }) }) { padding ->
        if (favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.FavoriteBorder,
                title = "Pas encore de favoris",
                message = "Touchez le cœur d'une annonce pour la garder ici et la comparer plus tard.",
                actionLabel = "Rechercher",
                onAction = onNewSearch,
                modifier = Modifier.padding(padding).padding(top = 40.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(favorites, key = { it.id }) { l ->
                ListingCard(
                    listing = l,
                    scored = null,
                    favorite = true,
                    onClick = { onOpen(l) },
                    onToggleFavorite = { onToggleFavorite(l) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
