package com.hiroshi.bonchosu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.hiroshi.bonchosu.data.settings.AccentColor
import com.hiroshi.bonchosu.data.settings.ThemeMode

/*
 * Theme = base (Light / Dark / AMOLED) × accent (or wallpaper colours when dynamic colour is on).
 * Screens read only the semantic tokens in [BrandColors] (or MaterialTheme roles), never raw hex.
 */

/** The four appearance settings; everything else is derived from these. */
@Immutable
data class ThemeSpec(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val accent: AccentColor = AccentColor.EMERALD,
    val dynamic: Boolean = false,
    /** Multiplies every dp and sp the app draws (see [UiSize]). */
    val scale: Float = 1f,
)

@Immutable
data class BrandColors(
    val isDark: Boolean,
    // Accent: buttons, highlights, charts, links.
    val Accent: Color,
    val OnAccent: Color,
    val AccentSoft: Color,
    val OnAccentSoft: Color,
    // The filled hero card (total balance): the accent, with content in OnHero.
    val Hero: Color,
    val OnHero: Color,
    val OnHeroMuted: Color,
    /** Tiles placed on the hero card. */
    val HeroRaised: Color,
    // Base palette.
    val Bg: Color,
    val Surface: Color,
    val Surface2: Color,
    val Text: Color,
    val Muted: Color,
    val Border: Color,
    val Income: Color,
    val Expense: Color,
    // "Needs attention" (review items, warnings).
    val Amber: Color,
    val AmberBar: Color,
    val AmberSoft: Color,
    val AmberInk: Color,
) {
    val Danger: Color get() = Expense
}

private class Base(
    val bg: Color, val surface: Color, val surface2: Color, val text: Color, val muted: Color,
    val border: Color, val income: Color, val expense: Color,
)

private val LightBase = Base(
    bg = Color(0xFFF4F5F7), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF0F3), text = Color(0xFF111418),
    muted = Color(0xFF5B6470), border = Color(0xFFE2E5EA), income = Color(0xFF047857), expense = Color(0xFFB42318),
)
private val DarkBase = Base(
    bg = Color(0xFF0E1114), surface = Color(0xFF171B20), surface2 = Color(0xFF20262D), text = Color(0xFFF2F4F7),
    muted = Color(0xFF9AA4B2), border = Color(0xFF2A3038), income = Color(0xFF4ADE80), expense = Color(0xFFF87171),
)
private val AmoledBase = Base(
    bg = Color(0xFF000000), surface = Color(0xFF0B0D10), surface2 = Color(0xFF15191E), text = Color(0xFFF2F4F7),
    muted = Color(0xFF9AA4B2), border = Color(0xFF1F242A), income = Color(0xFF4ADE80), expense = Color(0xFFF87171),
)

/** primary / onPrimary / container / onContainer. */
private class Accent(val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color)

private fun accentOf(accent: AccentColor, dark: Boolean): Accent = when (accent) {
    AccentColor.EMERALD -> if (dark) Accent(Color(0xFF34D399), Color(0xFF022C22), Color(0xFF0B3B2E), Color(0xFF6EE7B7))
    else Accent(Color(0xFF047857), Color.White, Color(0xFFD1FAE5), Color(0xFF065F46))
    AccentColor.BLUE -> if (dark) Accent(Color(0xFF60A5FA), Color(0xFF0B1B3A), Color(0xFF172A4A), Color(0xFF93C5FD))
    else Accent(Color(0xFF1D4ED8), Color.White, Color(0xFFDBEAFE), Color(0xFF1E40AF))
    AccentColor.VIOLET -> if (dark) Accent(Color(0xFFA78BFA), Color(0xFF1E1638), Color(0xFF2C2350), Color(0xFFC4B5FD))
    else Accent(Color(0xFF6D28D9), Color.White, Color(0xFFEDE9FE), Color(0xFF5B21B6))
    AccentColor.ORANGE -> if (dark) Accent(Color(0xFFFB923C), Color(0xFF3A1A08), Color(0xFF3B2416), Color(0xFFFDBA74))
    else Accent(Color(0xFFC2410C), Color.White, Color(0xFFFFEDD5), Color(0xFF9A3412))
}

/** The swatch shown in the accent picker. */
fun accentSwatch(accent: AccentColor, dark: Boolean): Color = accentOf(accent, dark).primary

private fun brand(base: Base, a: Accent, dark: Boolean) = BrandColors(
    isDark = dark,
    Accent = a.primary, OnAccent = a.onPrimary, AccentSoft = a.container, OnAccentSoft = a.onContainer,
    Hero = a.primary, OnHero = a.onPrimary, OnHeroMuted = a.onPrimary.copy(alpha = 0.78f),
    HeroRaised = a.onPrimary.copy(alpha = 0.10f),
    Bg = base.bg, Surface = base.surface, Surface2 = base.surface2, Text = base.text, Muted = base.muted,
    Border = base.border, Income = base.income, Expense = base.expense,
    Amber = if (dark) Color(0xFFF5B547) else Color(0xFFB5630A),
    AmberBar = Color(0xFFD9822B),
    AmberSoft = if (dark) Color(0xFF3A2C12) else Color(0xFFFBEFD9),
    AmberInk = if (dark) Color(0xFFF7CF7C) else Color(0xFF6B4A12),
)

/** Builds the colours for a theme. [dynamicScheme] is the wallpaper scheme when dynamic colour is on and supported. */
private fun resolve(spec: ThemeSpec, dark: Boolean, dynamicScheme: ColorScheme?): BrandColors {
    val base = when {
        !dark -> LightBase
        spec.amoled -> AmoledBase
        else -> DarkBase
    }
    val accent = dynamicScheme?.let { Accent(it.primary, it.onPrimary, it.primaryContainer, it.onPrimaryContainer) }
        ?: accentOf(spec.accent, dark)
    return brand(base, accent, dark)
}

private val LocalBrand = staticCompositionLocalOf { brand(LightBase, accentOf(AccentColor.EMERALD, false), false) }

/** The colours for the current theme. */
val Brand: BrandColors
    @Composable @ReadOnlyComposable get() = LocalBrand.current

/**
 * Category colours stay the same in every theme, so you learn them once. They're solid, with a white letter on top
 * (at least 4.5:1 contrast). Indexed by CategoryEntity.colorIndex.
 */
object CategoryPalette {
    private val solid = listOf(
        Color(0xFFB45309), // 0 food: orange
        Color(0xFF15803D), // 1 groceries: green
        Color(0xFF1D4ED8), // 2 transport: blue
        Color(0xFFBE185D), // 3 shopping: pink
        Color(0xFF6D28D9), // 4 bills: violet
        Color(0xFFB91C1C), // 5 entertainment: red
        Color(0xFF0F766E), // 6 health: teal
        Color(0xFF4338CA), // 7 education: indigo
        Color(0xFFA21CAF), // 8 family: fuchsia
        Color(0xFF92400E), // 9 gifts: brown
        Color(0xFF475569), // 10 other: slate
        Color(0xFF047857), // 11 salary: emerald
    )

    /** (background, letter) for pickers that show every colour. */
    val pairs: List<Pair<Color, Color>> = solid.map { it to Color.White }

    fun tint(i: Int?) = solid[(i ?: 10).mod(solid.size)]
    fun ink(@Suppress("UNUSED_PARAMETER") i: Int?) = Color.White

    /** Chart segments: the accent first, then fixed hues that stay apart from each other. */
    val chart: List<Color>
        @Composable @ReadOnlyComposable get() = listOf(
            Brand.Accent, Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFFEC4899),
            Color(0xFF8B5CF6), Color(0xFF14B8A6), Brand.Muted,
        )
}

private fun scheme(b: BrandColors): ColorScheme {
    val common: ColorScheme = if (b.isDark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = b.Accent,
        onPrimary = b.OnAccent,
        primaryContainer = b.AccentSoft,
        onPrimaryContainer = b.OnAccentSoft,
        secondary = b.Accent,
        onSecondary = b.OnAccent,
        secondaryContainer = b.AccentSoft,
        onSecondaryContainer = b.OnAccentSoft,
        tertiary = b.Amber,
        tertiaryContainer = b.AmberSoft,
        onTertiaryContainer = b.AmberInk,
        background = b.Bg,
        onBackground = b.Text,
        surface = b.Bg,
        onSurface = b.Text,
        surfaceVariant = b.Surface2,
        onSurfaceVariant = b.Muted,
        surfaceContainerLowest = b.Surface,
        surfaceContainerLow = b.Surface,
        surfaceContainer = b.Surface,
        surfaceContainerHigh = b.Surface,
        surfaceContainerHighest = b.Surface2,
        surfaceBright = b.Surface,
        surfaceDim = b.Bg,
        outline = b.Muted.copy(alpha = 0.6f),
        outlineVariant = b.Border,
        error = b.Expense,
        onError = if (b.isDark) Color(0xFF3B0A0A) else Color.White,
        errorContainer = if (b.isDark) Color(0xFF3B1716) else Color(0xFFFDE8E6),
        onErrorContainer = if (b.isDark) Color(0xFFFECACA) else Color(0xFF7A1A12),
        inverseSurface = b.Text,
        inverseOnSurface = b.Bg,
        scrim = Color.Black,
    )
}

/** Whether [spec] means dark right now (System follows the phone). */
@Composable
fun isDarkTheme(spec: ThemeSpec): Boolean = when (spec.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Supported on Android 12+ only; older phones keep the chosen accent. */
val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The resolved colours for [spec], e.g. for a live preview of a theme that isn't applied yet. */
@Composable
fun brandFor(spec: ThemeSpec, dark: Boolean = isDarkTheme(spec)): BrandColors {
    val context = LocalContext.current
    val dynamic = if (spec.dynamic && dynamicColorSupported) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
    return resolve(spec, dark, dynamic)
}

/** Applies [spec] to everything inside. Changes show at once, without restarting the screen. */
@Composable
fun BonchosuTheme(spec: ThemeSpec = ThemeSpec(), content: @Composable () -> Unit) {
    val brand = brandFor(spec)
    // The size setting scales the density: text, icons, padding and touch targets all grow or shrink together,
    // on top of the phone's own font size setting.
    val density = LocalDensity.current
    val scaled = Density(density.density * spec.scale, density.fontScale)
    CompositionLocalProvider(LocalBrand provides brand, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme(brand), typography = AppTypography, shapes = AppShapes, content = content)
    }
}
