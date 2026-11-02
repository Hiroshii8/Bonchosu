package com.example.moneyapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette from the UI design: deep green brand, soft grey-green ground, amber for "needs attention".
object Brand {
    val Green = Color(0xFF0E5A47)
    val GreenSoft = Color(0xFFE3EFEA)
    val GreenOnDark = Color(0xFFCFE3DB)
    val GreenRaised = Color(0xFF17695A)
    val IncomeText = Color(0xFF0E7A50)
    val Ground = Color(0xFFF3F5F4)
    val Ink = Color(0xFF14201C)
    val InkMuted = Color(0xFF5A6862)
    val Line = Color(0xFFE2E7E4)
    val Amber = Color(0xFFB5630A)
    val AmberBar = Color(0xFFD9822B)
    val AmberSoft = Color(0xFFFBEFD9)
    val AmberInk = Color(0xFF6B4A12)
    val Danger = Color(0xFFB3261E)
}

/** Category colours: (tint background, ink foreground). Indexed by CategoryEntity.colorIndex. */
object CategoryPalette {
    val pairs = listOf(
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

    fun tint(i: Int?) = pairs[(i ?: 10).mod(pairs.size)].first
    fun ink(i: Int?) = pairs[(i ?: 10).mod(pairs.size)].second

    /** Stronger colours for chart segments (lightness steps, so they stay distinguishable). */
    val chart = listOf(
        Color(0xFF0E5A47), Color(0xFF3E9C7F), Color(0xFFE0A040), Color(0xFF1E4E79),
        Color(0xFF9C5BB8), Color(0xFFC9D6CF), Color(0xFF8A4B08),
    )
}

private val colors = lightColorScheme(
    primary = Brand.Green,
    onPrimary = Color.White,
    primaryContainer = Brand.GreenSoft,
    onPrimaryContainer = Brand.Green,
    secondary = Brand.Green,
    secondaryContainer = Brand.GreenSoft,
    onSecondaryContainer = Brand.Green,
    tertiary = Brand.Amber,
    tertiaryContainer = Brand.AmberSoft,
    onTertiaryContainer = Brand.AmberInk,
    background = Brand.Ground,
    onBackground = Brand.Ink,
    surface = Brand.Ground,
    onSurface = Brand.Ink,
    surfaceVariant = Color.White,
    onSurfaceVariant = Brand.InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Brand.Line,
    outline = Color(0xFFB8C4BE),
    outlineVariant = Brand.Line,
    error = Brand.Danger,
)

private val type = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

val AmountStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp)

@Composable
fun MoneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
