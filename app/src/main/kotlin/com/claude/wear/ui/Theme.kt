package com.claude.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val Coral = Color(0xFFD97757)
private val CoralContainer = Color(0xFF5C2E21)
private val OnCoralContainer = Color(0xFFFFDBD0)
private val Ink = Color(0xFF1F1E1D)

private val ClaudeColors = ColorScheme(
    primary = Coral,
    onPrimary = Ink,
    primaryContainer = CoralContainer,
    onPrimaryContainer = OnCoralContainer,
)

@Composable
fun ClaudeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ClaudeColors, content = content)
}
