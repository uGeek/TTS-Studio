package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = StudioDarkPrimary,
    onPrimary = StudioDarkOnPrimary,
    primaryContainer = StudioDarkPrimaryContainer,
    onPrimaryContainer = StudioDarkOnPrimaryContainer,
    secondary = StudioDarkSecondary,
    onSecondary = StudioDarkOnSecondary,
    secondaryContainer = StudioDarkSecondaryContainer,
    onSecondaryContainer = StudioDarkOnSecondaryContainer,
    tertiary = StudioDarkTertiary,
    onTertiary = StudioDarkOnTertiary,
    tertiaryContainer = StudioDarkTertiaryContainer,
    onTertiaryContainer = StudioDarkOnTertiaryContainer,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val LightColorScheme = lightColorScheme(
    primary = StudioPrimary,
    onPrimary = StudioOnPrimary,
    primaryContainer = StudioPrimaryContainer,
    onPrimaryContainer = StudioOnPrimaryContainer,
    secondary = StudioSecondary,
    onSecondary = StudioOnSecondary,
    secondaryContainer = StudioSecondaryContainer,
    onSecondaryContainer = StudioOnSecondaryContainer,
    tertiary = StudioTertiary,
    onTertiary = StudioOnTertiary,
    tertiaryContainer = StudioTertiaryContainer,
    onTertiaryContainer = StudioOnTertiaryContainer,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent audio branding by default
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
