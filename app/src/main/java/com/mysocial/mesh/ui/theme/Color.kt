package com.mysocial.mesh.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class FbColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val blue: Color,
    val blueHover: Color,
    val blueLight: Color,
    val green: Color = Color(0xFF31A24C),
    val greenLight: Color,
    val red: Color = Color(0xFFFA3E3E),
    val redLight: Color,
    val isDark: Boolean
)

val LightFbColorScheme = FbColorScheme(
    background = Color(0xFFF0F2F5),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF7F8FA),
    border = Color(0xFFE4E6EB),
    divider = Color(0xFFCED0D4),
    textPrimary = Color(0xFF050505),
    textSecondary = Color(0xFF65676B),
    textTertiary = Color(0xFF8A8D91),
    blue = Color(0xFF1877F2),
    blueHover = Color(0xFF166FE5),
    blueLight = Color(0xFFE7F3FF),
    greenLight = Color(0xFFEBF7EE),
    redLight = Color(0xFFFDE8E8),
    isDark = false
)

val DarkFbColorScheme = FbColorScheme(
    background = Color(0xFF18191A),
    surface = Color(0xFF242526),
    surfaceElevated = Color(0xFF3A3B3C),
    border = Color(0xFF393A3B),
    divider = Color(0xFF3E4042),
    textPrimary = Color(0xFFE4E6EB),
    textSecondary = Color(0xFFB0B3B8),
    textTertiary = Color(0xFF8A8D91),
    blue = Color(0xFF2D88FF),
    blueHover = Color(0xFF3B92FF),
    blueLight = Color(0xFF263951),
    greenLight = Color(0xFF1A3826),
    redLight = Color(0xFF3E2020),
    isDark = true
)

val LocalFbColors = staticCompositionLocalOf { LightFbColorScheme }

// Dynamic Composable Color Accessors for seamless auto dark/light mode across entire app:
val FbBackground: Color
    @Composable get() = LocalFbColors.current.background

val FbSurface: Color
    @Composable get() = LocalFbColors.current.surface

val FbSurfaceElevated: Color
    @Composable get() = LocalFbColors.current.surfaceElevated

val FbBorder: Color
    @Composable get() = LocalFbColors.current.border

val FbDivider: Color
    @Composable get() = LocalFbColors.current.divider

val FbTextPrimary: Color
    @Composable get() = LocalFbColors.current.textPrimary

val FbTextSecondary: Color
    @Composable get() = LocalFbColors.current.textSecondary

val FbTextTertiary: Color
    @Composable get() = LocalFbColors.current.textTertiary

val FacebookBlue: Color
    @Composable get() = LocalFbColors.current.blue

val FacebookBlueHover: Color
    @Composable get() = LocalFbColors.current.blueHover

val FacebookBlueLight: Color
    @Composable get() = LocalFbColors.current.blueLight

// Accents & Badges (Standard static colors)
val FbGreen = Color(0xFF31A24C)
val FbGreenLight: Color
    @Composable get() = LocalFbColors.current.greenLight
val FbRed = Color(0xFFFA3E3E)
val FbRedLight: Color
    @Composable get() = LocalFbColors.current.redLight
val FbAmber = Color(0xFFF7B125)

// Audience & Badge Colors
val BadgeNearbyBg: Color
    @Composable get() = LocalFbColors.current.blueLight
val BadgeNearbyText: Color
    @Composable get() = LocalFbColors.current.blue
val BadgeFriendsBg: Color
    @Composable get() = LocalFbColors.current.greenLight
val BadgeFriendsText: Color
    @Composable get() = LocalFbColors.current.green
val BadgeYouBg: Color
    @Composable get() = LocalFbColors.current.surfaceElevated
val BadgeYouText: Color
    @Composable get() = LocalFbColors.current.textSecondary
