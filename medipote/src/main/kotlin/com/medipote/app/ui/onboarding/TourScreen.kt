package com.medipote.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medipote.app.ui.Interest

private data class TourPage(val emoji: String, val title: String, val text: String)

// One idea per page, a few words each: the tour breathes.
private val PAGES = listOf(
    TourPage("👋", "Un petit tour", "Ici, on prend soin de soi, sans pression.\nPasse quand tu veux."),
    TourPage("🏠", "L'accueil", "Ce qui compte aujourd'hui.\nEt le « + » pour tout noter."),
    TourPage("🗂️", "Les onglets", "Tout ce que tu regardes,\nchaque chose à une seule place."),
    TourPage("🔍", "En haut de l'écran", "❤️  quand ça ne va pas\n🔍  pour tout retrouver"),
)

/**
 * The first-run tour: a few calm pages saying where things are, then "what matters to you", which decides
 * which cards start visible. It can be skipped at any time and seen again from the settings or the search.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TourScreen(onFinish: (Set<Interest>) -> Unit, onSkip: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var chosen by rememberSaveable(stateSaver = listSaver(save = { it }, restore = { it })) { mutableStateOf(emptyList<String>()) }
    val last = PAGES.size
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..last).forEach { i ->
                        Surface(
                            color = if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape, modifier = Modifier.size(width = if (i == page) 22.dp else 8.dp, height = 8.dp),
                        ) {}
                    }
                }
                TextButton(onClick = onSkip) { Text("Passer") }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (page < last) {
                    val p = PAGES[page]
                    Text(p.emoji, style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(32.dp))
                    Text(p.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        p.text, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 30.sp), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    Text("🌿", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(32.dp))
                    Text("Qu'est-ce qui compte pour toi ?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Plusieurs choix possibles, ou aucun.\nLe reste reste à un toucher.",
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 30.sp), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Interest.entries.forEach { i ->
                            FilterChip(
                                selected = i.name in chosen,
                                onClick = { chosen = if (i.name in chosen) chosen - i.name else chosen + i.name },
                                label = { Text("${i.emoji}  ${i.label}") },
                            )
                        }
                    }
                }
            }
            if (page < last) {
                Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Suivant") }
                if (page > 0) TextButton(onClick = { page-- }, modifier = Modifier.fillMaxWidth()) { Text("Revenir en arrière") }
            } else {
                Button(onClick = { onFinish(chosen.map(Interest::valueOf).toSet()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("C'est parti !") }
                OutlinedButton(onClick = { page-- }, modifier = Modifier.fillMaxWidth()) { Text("Revenir en arrière") }
            }
        }
    }
}
