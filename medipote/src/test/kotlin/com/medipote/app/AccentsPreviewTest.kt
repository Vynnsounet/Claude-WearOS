package com.medipote.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.medipote.app.data.Accent
import com.medipote.app.data.Look
import com.medipote.app.ui.theme.appColorScheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A contact sheet of every accent, light or dark, to look at the colours side by side. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w820dp-h2300dp-xhdpi")
class AccentsPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Preview(accent: Accent, dark: Boolean, modifier: Modifier) {
        MaterialTheme(colorScheme = appColorScheme(accent, Look(), dark)) {
            Column(modifier.background(MaterialTheme.colorScheme.background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(accent.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Prochaine prise 12:00", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Amlodipine 5 mg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {}) { Text("Pris") }
                            FilterChip(selected = true, onClick = {}, label = { Text("Choisi") })
                            Switch(checked = true, onCheckedChange = {})
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Conteneur", Modifier.background(MaterialTheme.colorScheme.primaryContainer).padding(6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelMedium)
                    Text("Second", Modifier.background(MaterialTheme.colorScheme.secondaryContainer).padding(6.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelMedium)
                    Text("Tiers", Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).padding(6.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.labelMedium)
                    Text("Haut", Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(6.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    private fun sheet(name: String, dark: Boolean) {
        compose.setContent {
            Column(Modifier.background(androidx.compose.ui.graphics.Color.Gray).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Accent.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        pair.forEach { Preview(it, dark, Modifier.weight(1f)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun dark() = sheet("accents_dark", true)
    @Test fun light() = sheet("accents_light", false)
}
