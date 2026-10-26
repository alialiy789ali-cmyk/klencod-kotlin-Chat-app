package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SovereignDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = SovereignVoid,
    primaryContainer = EmeraldDim,
    onPrimaryContainer = TitaniumWhite,
    secondary = CyanSignal,
    onSecondary = SovereignVoid,
    secondaryContainer = SovereignSurfaceVariant,
    onSecondaryContainer = TitaniumWhite,
    tertiary = AmberWarning,
    onTertiary = SovereignVoid,
    background = SovereignVoid,
    onBackground = TitaniumWhite,
    surface = SovereignSurface,
    onSurface = TitaniumWhite,
    surfaceVariant = SovereignSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = SovereignBorder,
    error = RoseDestruct,
    onError = TitaniumWhite
)

private val SovereignLightColorScheme = lightColorScheme(
    primary = EmeraldDim,
    onPrimary = TitaniumWhite,
    primaryContainer = EmeraldGlow.copy(alpha = 0.2f),
    onPrimaryContainer = SovereignLightText,
    secondary = CyanSignal,
    onSecondary = SovereignLightText,
    secondaryContainer = SovereignLightBorder,
    onSecondaryContainer = SovereignLightText,
    tertiary = AmberWarning,
    onTertiary = SovereignLightText,
    background = SovereignLightBg,
    onBackground = SovereignLightText,
    surface = SovereignLightSurface,
    onSurface = SovereignLightText,
    surfaceVariant = SovereignLightBorder,
    onSurfaceVariant = SlateSubtle,
    outline = SovereignLightBorder,
    error = RoseDestruct,
    onError = TitaniumWhite
)

@Composable
fun SovereignTheme(
    darkTheme: Boolean = true, // Default to Sovereign Dark
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SovereignDarkColorScheme else SovereignLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
