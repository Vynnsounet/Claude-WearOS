package com.radaroccaz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radaroccaz.app.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A2472),
    secondary = Color(0xFF4B5675),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E7F5),
    onSecondaryContainer = Color(0xFF19213A),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7C2),
    onTertiaryContainer = Color(0xFF4A2300),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF151A26),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF151A26),
    surfaceVariant = Color(0xFFE3E7F0),
    onSurfaceVariant = Color(0xFF4A5163),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFE9ECF4),
    surfaceContainerHighest = Color(0xFFE2E6EF),
    outline = Color(0xFF7A8296),
    outlineVariant = Color(0xFFD5DAE5),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF5F0B07),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF062069),
    primaryContainer = Color(0xFF1E3A99),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFBCC5DF),
    onSecondary = Color(0xFF263049),
    secondaryContainer = Color(0xFF353F5A),
    onSecondaryContainer = Color(0xFFDCE2F7),
    tertiary = Color(0xFFFFB866),
    onTertiary = Color(0xFF4A2300),
    tertiaryContainer = Color(0xFF6B3A06),
    onTertiaryContainer = Color(0xFFFFE7C2),
    background = Color(0xFF0F1218),
    onBackground = Color(0xFFE3E6EE),
    surface = Color(0xFF0F1218),
    onSurface = Color(0xFFE3E6EE),
    surfaceVariant = Color(0xFF2C3242),
    onSurfaceVariant = Color(0xFFC0C6D6),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF171B23),
    surfaceContainer = Color(0xFF1B2029),
    surfaceContainerHigh = Color(0xFF242A35),
    surfaceContainerHighest = Color(0xFF2E3440),
    outline = Color(0xFF8A92A6),
    outlineVariant = Color(0xFF3A4152),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7A1A12),
    onErrorContainer = Color(0xFFFFDAD5),
)

/** Colours outside the Material scheme: deal levels and source brands. */
@Immutable
data class ExtraColors(
    val great: Color,
    val greatContainer: Color,
    val good: Color,
    val goodContainer: Color,
    val fair: Color,
    val fairContainer: Color,
    val weak: Color,
    val weakContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val LightExtra = ExtraColors(
    great = Color(0xFF0F7A3D), greatContainer = Color(0xFFD4F5E0),
    good = Color(0xFF2E7D32), goodContainer = Color(0xFFE6F4D9),
    fair = Color(0xFF9A6200), fairContainer = Color(0xFFFFF0CC),
    weak = Color(0xFFB3261E), weakContainer = Color(0xFFFFE3E0),
    heroStart = Color(0xFF1D4ED8), heroEnd = Color(0xFF4338CA),
)

private val DarkExtra = ExtraColors(
    great = Color(0xFF7BE0A2), greatContainer = Color(0xFF0E3D22),
    good = Color(0xFFA5D98A), goodContainer = Color(0xFF233A12),
    fair = Color(0xFFFFCD6B), fairContainer = Color(0xFF3F2E00),
    weak = Color(0xFFFFB4AB), weakContainer = Color(0xFF4F120D),
    heroStart = Color(0xFF1E3A99), heroEnd = Color(0xFF312E81),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Big tabular price figures. */
val PriceStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.2).sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RadarTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
