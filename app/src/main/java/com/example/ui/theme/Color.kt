package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Observable flag for theme state. Switches between requested Sand and Green themes.
// Default theme MUST be Light Mode as per user request.
var isThemeAmbientDark by mutableStateOf(false)

// Custom Color Palette:
// #99cc66 -> Soft Olive Green (Primary)
// #254222 -> Deep Forest Green (Text / Dark Bg)
// #ece2b1 -> Warm Sand Cream (Light Bg)
// #cae4c5 -> Pastel Sage Green (Elevated glass card)

// Dynamic theme color getters satisfying both light & dark look-and-feels:
val OLEDBlack: Color 
    get() = if (isThemeAmbientDark) Color(0xFF1D291D) else Color(0xFFECE2B1) // Dark forest green / warm sand canvas

val DarkGreyBg: Color 
    get() = if (isThemeAmbientDark) Color(0xFF121B12) else Color(0xFFE2D6A2) // Ambient canvas bases

val CardSlate: Color 
    get() = if (isThemeAmbientDark) Color(0xFF254222).copy(alpha = 0.85f) else Color(0xFFCAE4C5).copy(alpha = 0.85f) // Glassmorphism container layer

val CardBorder: Color 
    get() = if (isThemeAmbientDark) Color(0xFF99CC66).copy(alpha = 0.5f) else Color(0xFF99CC66).copy(alpha = 0.6f) // High refractive frosted boundaries

val PrimaryNeonBlue: Color 
    get() = if (isThemeAmbientDark) Color(0xFF99CC66) else Color(0xFF254222) // Primary contrast brand color

val SecondaryNeonCyan: Color 
    get() = if (isThemeAmbientDark) Color(0xFFCAE4C5) else Color(0xFF254222) // Secondary action keys

val PulseVocalCyan: Color 
    get() = if (isThemeAmbientDark) Color(0xFF99CC66) else Color(0xFF254222) // Adaptive voice brand accent

val AccentuatingGreen: Color 
    get() = if (isThemeAmbientDark) Color(0xFF99CC66) else Color(0xFF254222) // Contrast indicators

val StatusOfflineGrey: Color 
    get() = Color(0xFF7F8C7F)

val WarningRed: Color 
    get() = Color(0xFFB3261E) // Safe warning tone

val SlateLightText: Color 
    get() = if (isThemeAmbientDark) Color(0xFFECE2B1) else Color(0xFF254222) // Slate-balanced high-contrast reading text

val SlateMutedText: Color 
    get() = if (isThemeAmbientDark) Color(0xFFCAE4C5).copy(alpha = 0.8f) else Color(0xFF254222).copy(alpha = 0.7f) // Subtitles

val DeepContrastingText: Color 
    get() = if (isThemeAmbientDark) Color(0xFF254222) else Color(0xFFECE2B1) // Readable inner button labels

val VeryDeepPurpleBg: Color 
    get() = if (isThemeAmbientDark) Color(0xFF152215) else Color(0xFFECE2B1)
