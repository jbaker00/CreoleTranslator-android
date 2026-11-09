package com.creole.translator.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// App brand colors
val BrandPurple = Color(0xFF7B2D8B)
val BrandPink = Color(0xFFE91E8C)
val BrandPurpleLight = Color(0xFF9C4DB8)
val BrandPinkLight = Color(0xFFFF6BB5)
val CardBackground = Color(0xFFF8F0FF)
val RecordingRed = Color(0xFFE53935)
val WarningOrange = Color(0xFFFB8C00)

// Header gradient end. BrandPink (E91E8C) under white text measured 4.2:1
// (3.3:1 for the 85% subtitle); this darker pink keeps the look at 6.6:1.
val HeaderPink = Color(0xFFB0186B)

/**
 * Colors checked against WCAG contrast (4.5:1 text, 3:1 icons) on every
 * background they sit on, in light and dark mode — see scripts/contrast_audit.py
 * (rerun after any color change). Same roles as the iOS Palette extension
 * (ContentView.swift): accent, secondaryText, errorText, warningText, recordingRed.
 * BrandPurple on the dark surface was 2.1:1, so views use [AppPalette.accent].
 */
data class AppPalette(
    val accent: Color,
    val secondaryText: Color,
    val errorText: Color,
    val warningText: Color,
    /** Stop Recording button background; white text on it in both modes. */
    val recordingRed: Color,
)

val LightPalette = AppPalette(
    accent = Color(0xFF7B2D8B),
    secondaryText = Color(0xFF5E5866),
    errorText = Color(0xFFC4001A),
    warningText = Color(0xFFA84300),
    recordingRed = Color(0xFFC4001A),
)

val DarkPalette = AppPalette(
    accent = Color(0xFFE0B6EE),
    secondaryText = Color(0xFFB5AEBD),
    errorText = Color(0xFFFF6961),
    warningText = Color(0xFFFFB340),
    recordingRed = Color(0xFFC4001A),
)
