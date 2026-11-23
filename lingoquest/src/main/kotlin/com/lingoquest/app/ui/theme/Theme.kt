package com.lingoquest.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3DFFF),
    onPrimaryContainer = Color(0xFF17005B),
    secondary = Color(0xFF605C7D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E0FF),
    onSecondaryContainer = Color(0xFF1C1836),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFBF9FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF9FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC6BFFF),
    onPrimary = Color(0xFF2B14A3),
    primaryContainer = Color(0xFF4331BD),
    onPrimaryContainer = Color(0xFFE3DFFF),
    secondary = Color(0xFFC9C3E9),
    onSecondary = Color(0xFF312E4D),
    secondaryContainer = Color(0xFF484464),
    onSecondaryContainer = Color(0xFFE6E0FF),
    tertiary = Color(0xFFFFB961),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF121018),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF121018),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceContainerLowest = Color(0xFF0D0B12),
    surfaceContainerLow = Color(0xFF1B1920),
    surfaceContainer = Color(0xFF1F1D25),
    surfaceContainerHigh = Color(0xFF2A2830),
    surfaceContainerHighest = Color(0xFF35333B),
    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colours the Material scheme has no slot for: success and XP. */
@Immutable
data class GameColors(
    val good: Color,
    val goodContainer: Color,
    val onGoodContainer: Color,
    val xp: Color,
)

private val LightGame = GameColors(
    good = Color(0xFF2E7D32),
    goodContainer = Color(0xFFD7F0D8),
    onGoodContainer = Color(0xFF0B3A10),
    xp = Color(0xFFB26A00),
)

private val DarkGame = GameColors(
    good = Color(0xFF81C995),
    goodContainer = Color(0xFF1E3A24),
    onGoodContainer = Color(0xFFC8EACB),
    xp = Color(0xFFFFD166),
)

val LocalGameColors = staticCompositionLocalOf { LightGame }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun LingoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalGameColors provides if (dark) DarkGame else LightGame) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            content = content,
        )
    }
}

val MaterialTheme.game: GameColors
    @Composable get() = LocalGameColors.current
