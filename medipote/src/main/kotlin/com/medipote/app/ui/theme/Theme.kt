package com.medipote.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.core.graphics.ColorUtils
import com.medipote.app.data.Accent
import com.medipote.app.data.Backdrop
import com.medipote.app.data.Corners
import com.medipote.app.data.FontChoice
import com.medipote.app.data.Look
import com.medipote.app.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F7F69),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4EEDB),
    onPrimaryContainer = Color(0xFF00382C),
    secondary = Color(0xFF4A635B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DE),
    onSecondaryContainer = Color(0xFF062019),
    tertiary = Color(0xFF6750A4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF22005D),
    background = Color(0xFFF8FAF8),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF8FAF8),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5F3),
    surfaceContainer = Color(0xFFECF0ED),
    surfaceContainerHigh = Color(0xFFE6EAE7),
    surfaceContainerHighest = Color(0xFFE0E4E1),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD8BE),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFB4EEDB),
    secondary = Color(0xFFB1CCC2),
    onSecondary = Color(0xFF1C352E),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCDE9DE),
    tertiary = Color(0xFFCFBCFF),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F378A),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF0B0F0E),
    surfaceContainerLow = Color(0xFF191C1B),
    surfaceContainer = Color(0xFF1D201F),
    surfaceContainerHigh = Color(0xFF272B29),
    surfaceContainerHighest = Color(0xFF323634),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB),
)

/** Status colours for dose states, chosen per theme so they keep enough contrast. */
@Immutable
data class StatusColors(
    val good: Color,
    val warning: Color,
    val critical: Color,
    val goodContainer: Color,
    val warningContainer: Color,
    val criticalContainer: Color,
    val period: Color,
    val fertile: Color,
    val gold: Color,
    val streak: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF2E7D32),
    warning = Color(0xFFB26A00),
    critical = Color(0xFFC62828),
    goodContainer = Color(0xFFD7F0D8),
    warningContainer = Color(0xFFFFE5BF),
    criticalContainer = Color(0xFFFFDAD6),
    period = Color(0xFFD94A64),
    fertile = Color(0xFF1E88A8),
    gold = Color(0xFFC08A00),
    streak = Color(0xFFE8590C),
)

private val DarkStatus = StatusColors(
    good = Color(0xFF81C995),
    warning = Color(0xFFF2B65A),
    critical = Color(0xFFFF8A80),
    goodContainer = Color(0xFF1E3A24),
    warningContainer = Color(0xFF45320F),
    criticalContainer = Color(0xFF4A1C1A),
    period = Color(0xFFFF8FA3),
    fertile = Color(0xFF6CC7E3),
    gold = Color(0xFFFFD166),
    streak = Color(0xFFFF9E5E),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Accent tones: primary and secondary roles for light and dark themes. */
private data class AccentTones(
    val primary: Long, val onPrimary: Long, val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondary: Long, val secondaryContainer: Long, val onSecondaryContainer: Long,
)

private fun hex(c: Int): Long = c.toLong() and 0xFFFFFFFFL

/**
 * Tones generated from any colour: same hue, lightness searched so that text on it stays readable
 * (at least 4.5:1 for the primary colour and its "on" colour, like Material's own palettes).
 */
private fun seedTones(seed: Long, dark: Boolean): AccentTones {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toInt(), hsl)
    val h = hsl[0]
    val s = hsl[1].coerceIn(0.2f, 0.85f)
    fun tone(sat: Float, l: Float) = ColorUtils.HSLToColor(floatArrayOf(h, sat.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
    val white = android.graphics.Color.WHITE
    return if (!dark) {
        var l = 0.42f
        while (l > 0.15f && ColorUtils.calculateContrast(white, tone(s, l)) < 4.6) l -= 0.01f
        AccentTones(
            primary = hex(tone(s, l)), onPrimary = 0xFFFFFFFF,
            primaryContainer = hex(tone(minOf(s, 0.7f), 0.89f)), onPrimaryContainer = hex(tone(s, 0.13f)),
            secondary = hex(tone(s * 0.3f, 0.38f)), secondaryContainer = hex(tone(s * 0.45f, 0.89f)), onSecondaryContainer = hex(tone(s * 0.4f, 0.12f)),
        )
    } else {
        val surface = 0xFF101413.toInt()
        var l = 0.70f
        while (l < 0.92f && ColorUtils.calculateContrast(tone(minOf(s, 0.75f), l), surface) < 7.0) l += 0.01f
        AccentTones(
            primary = hex(tone(minOf(s, 0.75f), l)), onPrimary = hex(tone(s, 0.15f)),
            primaryContainer = hex(tone(s * 0.8f, 0.28f)), onPrimaryContainer = hex(tone(s, 0.9f)),
            secondary = hex(tone(s * 0.3f, 0.76f)), secondaryContainer = hex(tone(s * 0.3f, 0.29f)), onSecondaryContainer = hex(tone(s * 0.4f, 0.9f)),
        )
    }
}

private fun tones(accent: Accent, dark: Boolean): AccentTones = when (accent) {
    Accent.MINT -> if (dark) AccentTones(0xFF7FD8BE, 0xFF00382C, 0xFF005141, 0xFFB4EEDB, 0xFFB1CCC2, 0xFF334B44, 0xFFCDE9DE)
    else AccentTones(0xFF1F7F69, 0xFFFFFFFF, 0xFFB4EEDB, 0xFF00382C, 0xFF4A635B, 0xFFCDE9DE, 0xFF062019)
    Accent.OCEAN -> if (dark) AccentTones(0xFFA0CAFF, 0xFF003258, 0xFF00497D, 0xFFD2E4FF, 0xFFBBC7DB, 0xFF3C4858, 0xFFD7E3F7)
    else AccentTones(0xFF0B61A4, 0xFFFFFFFF, 0xFFD2E4FF, 0xFF001C37, 0xFF535F70, 0xFFD7E3F7, 0xFF101C2B)
    Accent.LAVENDER -> if (dark) AccentTones(0xFFCFBCFF, 0xFF381E72, 0xFF4F378A, 0xFFE9DDFF, 0xFFCCC2DC, 0xFF4A4458, 0xFFE8DEF8)
    else AccentTones(0xFF6750A4, 0xFFFFFFFF, 0xFFE9DDFF, 0xFF22005D, 0xFF625B71, 0xFFE8DEF8, 0xFF1E192B)
    Accent.CORAL -> if (dark) AccentTones(0xFFFFB4A6, 0xFF5F150B, 0xFF7E2B1F, 0xFFFFDAD3, 0xFFE7BDB6, 0xFF5D3F3B, 0xFFFFDAD4)
    else AccentTones(0xFF9C4234, 0xFFFFFFFF, 0xFFFFDAD3, 0xFF3E0400, 0xFF775651, 0xFFFFDAD4, 0xFF2C1512)
    Accent.AMBER -> if (dark) AccentTones(0xFFFFB870, 0xFF4A2800, 0xFF693C00, 0xFFFFDCBE, 0xFFE1C1A4, 0xFF5A4330, 0xFFFFDCBE)
    else AccentTones(0xFF8B5000, 0xFFFFFFFF, 0xFFFFDCBE, 0xFF2C1600, 0xFF725A42, 0xFFFFDCBE, 0xFF291806)
    Accent.ROSE -> if (dark) AccentTones(0xFFFFB0C8, 0xFF5E1133, 0xFF7B2949, 0xFFFFD9E2, 0xFFE3BDC6, 0xFF5A3F47, 0xFFFFD9E2)
    else AccentTones(0xFF984061, 0xFFFFFFFF, 0xFFFFD9E2, 0xFF3E001D, 0xFF74565F, 0xFFFFD9E2, 0xFF2B151C)
    else -> seedTones(accent.seed, dark)
}

/** The app's colour scheme for an accent, also used by the home-screen widgets. */
fun appColorScheme(accent: Accent, dark: Boolean): ColorScheme =
    (if (dark) DarkColors else LightColors).withAccent(tones(accent, dark))

/** The colour scheme with the user's look (custom colour, tinted background, pure black). */
fun appColorScheme(accent: Accent, look: Look, dark: Boolean): ColorScheme {
    val t = look.customColor?.let { seedTones(it, dark) } ?: tones(accent, dark)
    var scheme = (if (dark) DarkColors else LightColors).withAccent(t)
    if (look.backdrop == Backdrop.TINTED) {
        val p = Color(t.primary)
        fun tint(c: Color, f: Float) = lerp(c, p, f)
        scheme = scheme.copy(
            background = tint(scheme.background, if (dark) 0.07f else 0.06f),
            surface = tint(scheme.surface, if (dark) 0.07f else 0.06f),
            surfaceContainerLowest = tint(scheme.surfaceContainerLowest, 0.04f),
            surfaceContainerLow = tint(scheme.surfaceContainerLow, if (dark) 0.08f else 0.07f),
            surfaceContainer = tint(scheme.surfaceContainer, 0.08f),
            surfaceContainerHigh = tint(scheme.surfaceContainerHigh, 0.09f),
            surfaceContainerHighest = tint(scheme.surfaceContainerHighest, 0.10f),
        )
    }
    if (dark && look.pureBlack) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0F1110), surfaceContainer = Color(0xFF151817),
            surfaceContainerHigh = Color(0xFF1C1F1E), surfaceContainerHighest = Color(0xFF242826),
        )
    }
    return scheme
}

/** Swatch shown in the settings for an accent (its light primary). */
fun accentSwatch(accent: Accent): Color = Color(tones(accent, false).primary)

/** Swatch of a custom colour, as it will look as the app's main colour. */
fun customSwatch(seed: Long, dark: Boolean = false): Color = Color(seedTones(seed, dark).primary)

private fun ColorScheme.withAccent(t: AccentTones): ColorScheme {
    return copy(
        primary = Color(t.primary), onPrimary = Color(t.onPrimary),
        primaryContainer = Color(t.primaryContainer), onPrimaryContainer = Color(t.onPrimaryContainer),
        secondary = Color(t.secondary), secondaryContainer = Color(t.secondaryContainer),
        onSecondaryContainer = Color(t.onSecondaryContainer),
        surfaceTint = Color(t.primary),
    )
}

private fun fontFamily(f: FontChoice): FontFamily = when (f) {
    FontChoice.MODERN -> FontFamily.Default
    FontChoice.ELEGANT -> FontFamily.Serif
    FontChoice.HANDWRITTEN -> FontFamily.Cursive
    FontChoice.TYPEWRITER -> FontFamily.Monospace
}

/** Titles and big numbers in the chosen font; body text stays in the system font, for reading. */
fun typography(font: FontChoice): Typography {
    if (font == FontChoice.MODERN) return AppTypography
    val f = fontFamily(font)
    val t = AppTypography
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f),
        headlineLarge = t.headlineLarge.copy(fontFamily = f), headlineMedium = t.headlineMedium.copy(fontFamily = f),
        headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
    )
}

fun fontSample(font: FontChoice): FontFamily = fontFamily(font)

fun shapes(corners: Corners): Shapes = when (corners) {
    Corners.ROUNDED -> AppShapes
    Corners.SOFT -> Shapes(
        extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp), extraLarge = RoundedCornerShape(40.dp),
    )
    Corners.STRAIGHT -> Shapes(
        extraSmall = RoundedCornerShape(3.dp), small = RoundedCornerShape(5.dp), medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(16.dp),
    )
}

@Composable
fun MediPoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    accent: Accent = Accent.MINT,
    look: Look = Look(),
    content: @Composable () -> Unit,
) {
    val dark = isDark(themeMode)
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            val context = LocalContext.current
            (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).let {
                if (dark && look.pureBlack) it.copy(background = Color.Black, surface = Color.Black) else it
            }
        }
        else -> appColorScheme(accent, look, dark)
    }
    val density = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
        LocalDensity provides Density(density.density, density.fontScale * look.textScale.coerceIn(0.8f, 1.4f)),
    ) {
        MaterialTheme(colorScheme = colors, typography = typography(look.headingFont), shapes = shapes(look.corners), content = content)
    }
}

object AppTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
