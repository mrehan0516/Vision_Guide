package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Observable flag for theme state. Switches between Light and Dark High-Contrast themes.
// Default theme is Light Mode.
var isThemeAmbientDark by mutableStateOf(false)

// High-Contrast "Clarity" Accessibility Palette
// Dark Mode: Pure Black & Slate with Vibrant Cyan actions.
// Light Mode: Pure White & Soft Grey with High-Contrast Deep Navy/Royal Blue actions.

val OLEDBlack: Color 
    get() = if (isThemeAmbientDark) Color(0xFF000000) else Color(0xFFFFFFFF) // High contrast root canvas

val DarkGreyBg: Color 
    get() = if (isThemeAmbientDark) Color(0xFF101214) else Color(0xFFF5F7FA) // Ambient surface canvas

val CardSlate: Color 
    get() = if (isThemeAmbientDark) Color(0xFF1E2022) else Color(0xFFFFFFFF) // Elevated container

val CardBorder: Color 
    get() = if (isThemeAmbientDark) Color(0xFF33373B) else Color(0xFFE2E6EA) // Distinct structural borders

val PrimaryNeonBlue: Color 
    get() = if (isThemeAmbientDark) Color(0xFF00E5FF) else Color(0xFF0056D2) // Primary actionable contrast

val SecondaryNeonCyan: Color 
    get() = if (isThemeAmbientDark) Color(0xFF33EFFF) else Color(0xFF0073E6) // Secondary action

val PulseVocalCyan: Color 
    get() = if (isThemeAmbientDark) Color(0xFF00E5FF) else Color(0xFF0056D2) // Vivid vocal visualization

val AccentuatingGreen: Color 
    get() = if (isThemeAmbientDark) Color(0xFF00E676) else Color(0xFF0A7E07) // Positive state indicator

val StatusOfflineGrey: Color 
    get() = if (isThemeAmbientDark) Color(0xFF868E96) else Color(0xFF6C757D) // Inactive states

val WarningRed: Color 
    get() = if (isThemeAmbientDark) Color(0xFFFF4D4F) else Color(0xFFD32F2F) // Critical warning tone

val SlateLightText: Color 
    get() = if (isThemeAmbientDark) Color(0xFFF8F9FA) else Color(0xFF191C1F) // Absolute legibility text

val SlateMutedText: Color 
    get() = if (isThemeAmbientDark) Color(0xFFADB5BD) else Color(0xFF495057) // Scaled readable subtitles

val DeepContrastingText: Color 
    get() = if (isThemeAmbientDark) Color(0xFF001529) else Color(0xFFFFFFFF) // Text inside primary buttons

val VeryDeepPurpleBg: Color 
    get() = if (isThemeAmbientDark) Color(0xFF0A0C0E) else Color(0xFFF1F3F5)
