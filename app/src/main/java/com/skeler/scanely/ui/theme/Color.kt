@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// ============================================================================
// SEED COLOR TRIPLETS (matching aShellYou PaletteWheel pattern)
// ============================================================================
data class SeedColor(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int
)

// Predefined color palettes (subset of aShellYou's 20 colors for Scanly)
object SeedPalettes {
    val Color01 = SeedColor(
        primary = 0xFFB5353F.toInt(),
        secondary = 0xFFB78483.toInt(),
        tertiary = 0xFFB38A45.toInt()
    )
    val Color02 = SeedColor(
        primary = 0xFFF06435.toInt(),
        secondary = 0xFFB98474.toInt(),
        tertiary = 0xFFA48F42.toInt()
    )
    val Color03 = SeedColor(
        primary = 0xFFE07200.toInt(),
        secondary = 0xFFB2886C.toInt(),
        tertiary = 0xFF929553.toInt()
    )
    val Color04 = SeedColor(
        primary = 0xFFB28B00.toInt(),
        secondary = 0xFF9E8F6D.toInt(),
        tertiary = 0xFF789978.toInt()
    )
    val Color05 = SeedColor(
        primary = 0xFF169EB7.toInt(),
        secondary = 0xFF7F949B.toInt(),
        tertiary = 0xFF898FB0.toInt()
    )
    val Color06 = SeedColor(
        primary = 0xFF8C88D8.toInt(),
        secondary = 0xFF918EA4.toInt(),
        tertiary = 0xFFAF8599.toInt()
    )
    
    val ALL = listOf(Color01, Color02, Color03, Color04, Color05, Color06)
    val DEFAULT = Color06 // Purple-ish, M3 default feel
}

@RequiresApi(Build.VERSION_CODES.S)
fun highContrastDynamicDarkColorScheme(context: Context): ColorScheme {
    return dynamicDarkColorScheme(context = context).copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = dynamicDarkColorScheme(context).surfaceContainerLowest,
        surfaceContainer = dynamicDarkColorScheme(context).surfaceContainerLow,
        surfaceContainerHigh = dynamicDarkColorScheme(context).surfaceContainer,
        surfaceContainerHighest = dynamicDarkColorScheme(context).surfaceContainerHigh,
    )
}

val DarkGrayBackground = Color(0xFF141218)

@RequiresApi(Build.VERSION_CODES.S)
fun standardDynamicDarkColorScheme(context: Context): ColorScheme {
    val dynamic = dynamicDarkColorScheme(context)
    return dynamic.copy(
        background = DarkGrayBackground,
        surface = DarkGrayBackground,
        surfaceContainerLowest = DarkGrayBackground,
    )
}

// Fallback / Manual Schemes using SeedColor triplet
fun schemeFromSeed(seedColor: SeedColor, dark: Boolean, isHighContrast: Boolean = false): ColorScheme {
    val primary = Color(seedColor.primary)
    val secondary = Color(seedColor.secondary)
    val tertiary = Color(seedColor.tertiary)

    return if (dark) {
         val background = if (isHighContrast) Color.Black else DarkGrayBackground
         val surface = if (isHighContrast) Color.Black else DarkGrayBackground
         
         darkColorScheme(
             primary = primary,
             onPrimary = Color.White,
             primaryContainer = primary.copy(alpha = 0.3f),
             onPrimaryContainer = Color(0xFFEADDFF),
             secondary = secondary,
             onSecondary = Color.White,
             secondaryContainer = secondary.copy(alpha = 0.3f),
             onSecondaryContainer = Color(0xFFE8DEF8),
             tertiary = tertiary,
             onTertiary = Color.White,
             tertiaryContainer = tertiary.copy(alpha = 0.3f),
             onTertiaryContainer = Color(0xFFFFD8E4),
             background = background,
             surface = surface,
             surfaceContainerLowest = if (isHighContrast) Color.Black else background
         )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.1f),
            onPrimaryContainer = Color(0xFF21005D),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondary.copy(alpha = 0.1f),
            onSecondaryContainer = Color(0xFF1D192B),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = tertiary.copy(alpha = 0.1f),
            onTertiaryContainer = Color(0xFF31111D)
        )
    }
}