package com.mysocial.mesh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FacebookLightColorScheme = lightColorScheme(
    primary = LightFbColorScheme.blue,
    onPrimary = LightFbColorScheme.surface,
    primaryContainer = LightFbColorScheme.blueLight,
    onPrimaryContainer = LightFbColorScheme.blue,
    secondary = LightFbColorScheme.textSecondary,
    onSecondary = LightFbColorScheme.surface,
    secondaryContainer = LightFbColorScheme.surfaceElevated,
    onSecondaryContainer = LightFbColorScheme.textPrimary,
    tertiary = LightFbColorScheme.green,
    background = LightFbColorScheme.background,
    onBackground = LightFbColorScheme.textPrimary,
    surface = LightFbColorScheme.surface,
    onSurface = LightFbColorScheme.textPrimary,
    surfaceVariant = LightFbColorScheme.surfaceElevated,
    onSurfaceVariant = LightFbColorScheme.textSecondary,
    outline = LightFbColorScheme.border
)

private val FacebookDarkColorScheme = darkColorScheme(
    primary = DarkFbColorScheme.blue,
    onPrimary = DarkFbColorScheme.textPrimary,
    primaryContainer = DarkFbColorScheme.blueLight,
    onPrimaryContainer = DarkFbColorScheme.blue,
    secondary = DarkFbColorScheme.textSecondary,
    onSecondary = DarkFbColorScheme.surface,
    secondaryContainer = DarkFbColorScheme.surfaceElevated,
    onSecondaryContainer = DarkFbColorScheme.textPrimary,
    tertiary = DarkFbColorScheme.green,
    background = DarkFbColorScheme.background,
    onBackground = DarkFbColorScheme.textPrimary,
    surface = DarkFbColorScheme.surface,
    onSurface = DarkFbColorScheme.textPrimary,
    surfaceVariant = DarkFbColorScheme.surfaceElevated,
    onSurfaceVariant = DarkFbColorScheme.textSecondary,
    outline = DarkFbColorScheme.border
)

@Composable
fun MySocialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val fbColors = if (darkTheme) DarkFbColorScheme else LightFbColorScheme
    val colorScheme = if (darkTheme) FacebookDarkColorScheme else FacebookLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = fbColors.surface.toArgb()
                window.navigationBarColor = fbColors.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalFbColors provides fbColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
