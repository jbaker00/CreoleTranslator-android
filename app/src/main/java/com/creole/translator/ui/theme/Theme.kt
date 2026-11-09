package com.creole.translator.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Explicit values so scripts/contrast_audit.py can check every pairing.
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0B6EE),
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF141218),
    surface = Color(0xFF211D26),
    surfaceVariant = Color(0xFF2E2934),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFF2B8B5),
    secondaryContainer = Color(0xFF5A2A63),
    onSecondaryContainer = Color(0xFFF7D8FF),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    secondary = BrandPink,
    tertiary = Pink40,
    background = CardBackground,
    surface = CardBackground,
    surfaceVariant = Color(0xFFE9DFF0),
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB3261E),
    secondaryContainer = Color(0xFFF3DAF5),
    onSecondaryContainer = Color(0xFF2E1033),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val LocalAppPalette = staticCompositionLocalOf { LightPalette }

/** The WCAG-audited palette for the current light/dark mode. */
val appPalette: AppPalette
    @Composable @ReadOnlyComposable get() = LocalAppPalette.current

@Composable
fun CreoleTranslatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off: Material You wallpaper colors made contrast unpredictable (brand
    // purple text on a dynamic dark surface was unreadable). The fixed scheme
    // below is what scripts/contrast_audit.py checks.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // No statusBarColor: it is deprecated and a no-op from Android 15
            // on, so setting it only made the two platforms disagree. The bar
            // is transparent under edge-to-edge and the top bar's own gradient
            // shows through it — MainScreen's header already applies
            // statusBarsPadding, so nothing is occluded.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAppPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
