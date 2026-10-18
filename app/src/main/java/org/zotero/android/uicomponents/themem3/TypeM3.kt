package org.zotero.android.uicomponents.themem3

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Serif for titles and headings (book titles, screen titles); the system sans for everything else. */
val SerifTitles = FontFamily.Serif

private val base = Typography()

val AppTypographyM3 = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SerifTitles),
    displayMedium = base.displayMedium.copy(fontFamily = SerifTitles),
    displaySmall = base.displaySmall.copy(fontFamily = SerifTitles),
    headlineLarge = base.headlineLarge.copy(fontFamily = SerifTitles, fontWeight = FontWeight.Medium),
    headlineMedium = base.headlineMedium.copy(fontFamily = SerifTitles, fontWeight = FontWeight.Medium),
    headlineSmall = base.headlineSmall.copy(fontFamily = SerifTitles, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(
        fontFamily = SerifTitles,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        letterSpacing = 0.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = base.titleMedium,
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
    ),
    titleSmallEmphasized = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
    ),
)
