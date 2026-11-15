package com.medipote.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

// MédiPote's mint, on the watch's black background.
private val Mint = Color(0xFF7FD8BE)
private val MintContainer = Color(0xFF1F4F43)
private val OnMintContainer = Color(0xFFBDEBDD)
private val Ink = Color(0xFF06231B)

private val Colors = ColorScheme(
    primary = Mint,
    onPrimary = Ink,
    primaryContainer = MintContainer,
    onPrimaryContainer = OnMintContainer,
    secondary = Color(0xFF9CCFE8),
    onSecondary = Color(0xFF062430),
    tertiary = Color(0xFFF2B8C6),
    onTertiary = Color(0xFF3A0F1C),
)

@Composable
fun MediPoteWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
