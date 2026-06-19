package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryNeonBlue,
    secondary = SecondaryNeonCyan,
    tertiary = PulseVocalCyan,
    background = OLEDBlack,
    surface = CardSlate,
    onPrimary = DeepContrastingText,
    onSecondary = DeepContrastingText,
    onTertiary = DeepContrastingText,
    onBackground = SlateLightText,
    onSurface = SlateLightText,
    outline = CardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryNeonBlue,
    secondary = SecondaryNeonCyan,
    tertiary = PulseVocalCyan,
    background = DarkGreyBg,
    surface = CardSlate,
    onPrimary = DeepContrastingText,
    onSecondary = DeepContrastingText,
    onTertiary = DeepContrastingText,
    onBackground = SlateLightText,
    onSurface = SlateLightText,
    outline = CardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark theme or default to true for premium experience
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce the crisp accessible high-contrast values
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

