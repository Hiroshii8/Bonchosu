package com.hiroshi.moneyapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palette from the UI design: deep green brand, soft grey-green ground, amber for "needs attention".
 * [Green] is the accent for text, icons and borders; [Hero] is the filled green behind white content.
 */
@Immutable
data class BrandColors(
    val isDark: Boolean,
    val Green: Color,
    val Hero: Color,
    val GreenSoft: Color,
    val GreenOnDark: Color,
    val GreenRaised: Color,
    val IncomeText: Color,
    val Ground: Color,
    val Card: Color,
    val Ink: Color,
    val InkMuted: Color,
    val Line: Color,
    val Amber: Color,
    val AmberBar: Color,
    val AmberSoft: Color,
    val AmberInk: Color,
    val Danger: Color,
)

private val LightBrand = BrandColors(
    isDark = false,
    Green = Color(0xFF0E5A47),
    Hero = Color(0xFF0E5A47),
    GreenSoft = Color(0xFFE3EFEA),
    GreenOnDark = Color(0xFFCFE3DB),
    GreenRaised = Color(0xFF17695A),
    IncomeText = Color(0xFF0E7A50),
    Ground = Color(0xFFF3F5F4),
    Card = Color.White,
    Ink = Color(0xFF14201C),
    InkMuted = Color(0xFF5A6862),
    Line = Color(0xFFE2E7E4),
    Amber = Color(0xFFB5630A),
    AmberBar = Color(0xFFD9822B),
    AmberSoft = Color(0xFFFBEFD9),
    AmberInk = Color(0xFF6B4A12),
    Danger = Color(0xFFB3261E),
)

private val DarkBrand = BrandColors(
    isDark = true,
    Green = Color(0xFF7ACBAE),
    Hero = Color(0xFF16584A),
    GreenSoft = Color(0xFF1C3A31),
    GreenOnDark = Color(0xFFCFE3DB),
    GreenRaised = Color(0xFF21705E),
    IncomeText = Color(0xFF6BD6A0),
    Ground = Color(0xFF0F1513),
    Card = Color(0xFF1A221F),
    Ink = Color(0xFFE3EBE7),
    InkMuted = Color(0xFF9DADA6),
    Line = Color(0xFF2B3531),
    Amber = Color(0xFFE79A45),
    AmberBar = Color(0xFFD9822B),
    AmberSoft = Color(0xFF3A2C17),
    AmberInk = Color(0xFFF1C98B),
    Danger = Color(0xFFF2B8B5),
)

private val LocalBrand = staticCompositionLocalOf { LightBrand }

/** The brand colours for the current light/dark theme. */
val Brand: BrandColors
    @Composable @ReadOnlyComposable get() = LocalBrand.current

/** Category colours: (tint background, ink foreground). Indexed by CategoryEntity.colorIndex. */
object CategoryPalette {
    private val light = listOf(
        Color(0xFFFDEBD3) to Color(0xFF8A4B08), // 0 food
        Color(0xFFDDF1E6) to Color(0xFF0E5A47), // 1 groceries
        Color(0xFFDCEBF7) to Color(0xFF1E4E79), // 2 transport
        Color(0xFFEEE1F5) to Color(0xFF5B2A78), // 3 shopping
        Color(0xFFE8E8E0) to Color(0xFF4A4A3A), // 4 bills
        Color(0xFFFBE0E0) to Color(0xFF8A2323), // 5 entertainment
        Color(0xFFDFF3F3) to Color(0xFF0F5E5E), // 6 health
        Color(0xFFE6E4F8) to Color(0xFF3B3480), // 7 education
        Color(0xFFFCE4EF) to Color(0xFF7D2150), // 8 family
        Color(0xFFFFF1C9) to Color(0xFF6E5300), // 9 gifts
        Color(0xFFE2E7E4) to Color(0xFF3A4741), // 10 other
        Color(0xFFD8F0DF) to Color(0xFF14653A), // 11 salary
    )

    // Same hues with dark tints and light inks, so badges stay readable on dark cards.
    private val dark = listOf(
        Color(0xFF43301A) to Color(0xFFF5C489),
        Color(0xFF173A2E) to Color(0xFF8ED8B9),
        Color(0xFF1A3044) to Color(0xFF9CC6EC),
        Color(0xFF34253F) to Color(0xFFD5B2EA),
        Color(0xFF33332B) to Color(0xFFD4D4C2),
        Color(0xFF452324) to Color(0xFFF2A9A9),
        Color(0xFF173B3B) to Color(0xFF8ED6D6),
        Color(0xFF26244A) to Color(0xFFB5AEF0),
        Color(0xFF432236) to Color(0xFFF0A8CB),
        Color(0xFF3F3414) to Color(0xFFF0D27A),
        Color(0xFF2B3531) to Color(0xFFC3CFC9),
        Color(0xFF1A3B26) to Color(0xFF8FDCA8),
    )

    /** For pickers that show every colour. */
    val pairs: List<Pair<Color, Color>>
        @Composable @ReadOnlyComposable get() = if (Brand.isDark) dark else light

    @Composable @ReadOnlyComposable
    fun tint(i: Int?) = pairs[(i ?: 10).mod(light.size)].first

    @Composable @ReadOnlyComposable
    fun ink(i: Int?) = pairs[(i ?: 10).mod(light.size)].second

    /** Stronger colours for chart segments (lightness steps, so they stay distinguishable). */
    val chart: List<Color>
        @Composable @ReadOnlyComposable get() = if (Brand.isDark) darkChart else lightChart

    private val lightChart = listOf(
        Color(0xFF0E5A47), Color(0xFF3E9C7F), Color(0xFFE0A040), Color(0xFF1E4E79),
        Color(0xFF9C5BB8), Color(0xFFC9D6CF), Color(0xFF8A4B08),
    )
    private val darkChart = listOf(
        Color(0xFF7ACBAE), Color(0xFF3E9C7F), Color(0xFFE0A040), Color(0xFF6FA8DC),
        Color(0xFFB98AD0), Color(0xFF5E6B65), Color(0xFFC88A4A),
    )
}

private fun scheme(b: BrandColors) = if (b.isDark) {
    darkColorScheme(
        primary = b.Green,
        onPrimary = Color(0xFF00382B),
        primaryContainer = b.GreenSoft,
        onPrimaryContainer = b.Green,
        secondary = b.Green,
        secondaryContainer = b.GreenSoft,
        onSecondaryContainer = b.Green,
        tertiary = b.Amber,
        tertiaryContainer = b.AmberSoft,
        onTertiaryContainer = b.AmberInk,
        background = b.Ground,
        onBackground = b.Ink,
        surface = b.Ground,
        onSurface = b.Ink,
        surfaceVariant = b.Card,
        onSurfaceVariant = b.InkMuted,
        surfaceContainerLowest = b.Card,
        surfaceContainerLow = b.Card,
        surfaceContainer = b.Card,
        surfaceContainerHigh = Color(0xFF222B27),
        surfaceContainerHighest = b.Line,
        outline = Color(0xFF5C6A64),
        outlineVariant = b.Line,
        error = b.Danger,
        errorContainer = Color(0xFF5C1F1B),
        onErrorContainer = Color(0xFFF9DEDC),
    )
} else {
    lightColorScheme(
        primary = b.Green,
        onPrimary = Color.White,
        primaryContainer = b.GreenSoft,
        onPrimaryContainer = b.Green,
        secondary = b.Green,
        secondaryContainer = b.GreenSoft,
        onSecondaryContainer = b.Green,
        tertiary = b.Amber,
        tertiaryContainer = b.AmberSoft,
        onTertiaryContainer = b.AmberInk,
        background = b.Ground,
        onBackground = b.Ink,
        surface = b.Ground,
        onSurface = b.Ink,
        surfaceVariant = b.Card,
        onSurfaceVariant = b.InkMuted,
        surfaceContainerLowest = b.Card,
        surfaceContainerLow = b.Card,
        surfaceContainer = b.Card,
        surfaceContainerHigh = b.Card,
        surfaceContainerHighest = b.Line,
        outline = Color(0xFFB8C4BE),
        outlineVariant = b.Line,
        error = b.Danger,
    )
}

private val type = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

val AmountStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp)

/** Light or dark follows the AppCompat night mode, which MainActivity sets from the Theme setting. */
@Composable
fun MoneyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val brand = if (dark) DarkBrand else LightBrand
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme(brand), typography = type, content = content)
    }
}
