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
    onPrimary = DarkFbColorScheme.background,
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
    themeMode: String = "DARK",
    accentColor: String = "BLUE",
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "SYSTEM" -> systemInDark
        "LIGHT" -> false
        "AMOLED", "DARK" -> true
        else -> true
    }

    val baseScheme = when (themeMode) {
        "AMOLED" -> AmoledFbColorScheme
        "LIGHT" -> LightFbColorScheme
        "DARK" -> DarkFbColorScheme
        "SYSTEM" -> if (systemInDark) DarkFbColorScheme else LightFbColorScheme
        else -> DarkFbColorScheme
    }

    val fbColors = baseScheme.withAccent(accentColor)
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = fbColors.blue,
            onPrimary = fbColors.background,
            primaryContainer = fbColors.blueLight,
            onPrimaryContainer = fbColors.blue,
            secondary = fbColors.textSecondary,
            onSecondary = fbColors.surface,
            secondaryContainer = fbColors.surfaceElevated,
            onSecondaryContainer = fbColors.textPrimary,
            tertiary = fbColors.green,
            background = fbColors.background,
            onBackground = fbColors.textPrimary,
            surface = fbColors.surface,
            onSurface = fbColors.textPrimary,
            surfaceVariant = fbColors.surfaceElevated,
            onSurfaceVariant = fbColors.textSecondary,
            outline = fbColors.border
        )
    } else {
        lightColorScheme(
            primary = fbColors.blue,
            onPrimary = fbColors.surface,
            primaryContainer = fbColors.blueLight,
            onPrimaryContainer = fbColors.blue,
            secondary = fbColors.textSecondary,
            onSecondary = fbColors.surface,
            secondaryContainer = fbColors.surfaceElevated,
            onSecondaryContainer = fbColors.textPrimary,
            tertiary = fbColors.green,
            background = fbColors.background,
            onBackground = fbColors.textPrimary,
            surface = fbColors.surface,
            onSurface = fbColors.textPrimary,
            surfaceVariant = fbColors.surfaceElevated,
            onSurfaceVariant = fbColors.textSecondary,
            outline = fbColors.border
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = fbColors.surface.toArgb()
                window.navigationBarColor = fbColors.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalFbColors provides fbColors,
        androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = AppFontFamily)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MySocialTypography,
            content = content
        )
    }
}

@Composable
fun MySocialTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MySocialTheme(
        themeMode = if (darkTheme) "DARK" else "LIGHT",
        accentColor = "BLUE",
        content = content
    )
}
