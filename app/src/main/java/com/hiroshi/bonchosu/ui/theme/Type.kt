package com.hiroshi.bonchosu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One place to swap the app's font. */
val AppFont: FontFamily = FontFamily.Default

private val base = Typography()

private fun TextStyle.app(weight: FontWeight? = null, size: Int? = null, spacing: Double? = null) = copy(
    fontFamily = AppFont,
    fontWeight = weight ?: fontWeight,
    fontSize = size?.sp ?: fontSize,
    letterSpacing = spacing?.sp ?: letterSpacing,
)

val AppTypography = Typography(
    displayLarge = base.displayLarge.app(FontWeight.ExtraBold, spacing = -1.0),
    displayMedium = base.displayMedium.app(FontWeight.ExtraBold, spacing = -0.8),
    displaySmall = base.displaySmall.app(FontWeight.ExtraBold, spacing = -0.6),
    headlineLarge = base.headlineLarge.app(FontWeight.ExtraBold, size = 30, spacing = -0.6),
    headlineMedium = base.headlineMedium.app(FontWeight.ExtraBold, size = 26, spacing = -0.4),
    headlineSmall = base.headlineSmall.app(FontWeight.ExtraBold, size = 24, spacing = -0.3),
    titleLarge = base.titleLarge.app(FontWeight.ExtraBold, size = 20, spacing = -0.2),
    titleMedium = base.titleMedium.app(FontWeight.Bold, size = 16),
    titleSmall = base.titleSmall.app(FontWeight.Bold),
    bodyLarge = base.bodyLarge.app(spacing = 0.0),
    bodyMedium = base.bodyMedium.app(spacing = 0.0),
    bodySmall = base.bodySmall.app(spacing = 0.0),
    labelLarge = base.labelLarge.app(FontWeight.Bold, spacing = 0.0),
    labelMedium = base.labelMedium.app(FontWeight.SemiBold),
    labelSmall = base.labelSmall.app(FontWeight.SemiBold),
)

/** Big money figures (total balance, the amount being typed). */
val AmountStyle = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-1).sp)

/** Small upper-case section labels ("THEME", "LIVE PREVIEW"). */
val OverlineStyle = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp)

val AppShapes = Shapes(
    // Text fields use extraSmall, so this rounds every input in the app.
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Corner radius of cards and grouped lists. */
val CardRadius = 22.dp
