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
    val green: Color = Color(0xFF10B981),
    val greenLight: Color,
    val red: Color = Color(0xFFED4956),
    val redLight: Color,
    val amber: Color = Color(0xFFF77737),
    val amberLight: Color,
    val isDark: Boolean
)

val LightFbColorScheme = FbColorScheme(
    background = Color(0xFFFFFFFF),       // Modern Instagram Pure Crisp White Canvas
    surface = Color(0xFFFFFFFF),          // Clean Flat Surface
    surfaceElevated = Color(0xFFF7F7F7),  // Ultra-subtle neutral elevated surface
    border = Color(0xFFEFEFEF),           // Modern Hairline Micro-Border
    divider = Color(0xFFF0F0F0),          // Clean Separator
    textPrimary = Color(0xFF000000),      // Pure Sharp Black
    textSecondary = Color(0xFF737373),    // Balanced Instagram Subtitle Grey
    textTertiary = Color(0xFF8E8E8E),     // Light Metadata Grey
    blue = Color(0xFF0095F6),             // Instagram Signature Action Blue
    blueHover = Color(0xFF1877F2),
    blueLight = Color(0xFFEBF5FF),        // Soft Ice Tint
    green = Color(0xFF00BA88),            // Crisp Emerald
    greenLight = Color(0xFFE6F9F3),
    red = Color(0xFFED4956),              // Iconic Instagram Heart Red
    redLight = Color(0xFFFDE8E9),
    amber = Color(0xFFF77737),            // Warm Sunset Amber
    amberLight = Color(0xFFFFF1EB),
    isDark = false
)

val DarkFbColorScheme = FbColorScheme(
    background = Color(0xFF18191A),       // Deep Dark Charcoal Canvas
    surface = Color(0xFF242526),          // Distinct Elevated Surface
    surfaceElevated = Color(0xFF3A3B3C),  // Interactive Dark Action Chip / Pill
    border = Color(0xFF393A3B),           // Refined Soft Dark Border
    divider = Color(0xFF2E2F30),          // Subtle Hairline Separator
    textPrimary = Color(0xFFE4E6EB),      // Crisp Balanced Off-White
    textSecondary = Color(0xFFB0B3B8),    // Soft Secondary Grey
    textTertiary = Color(0xFF8A8D91),     // Slate Metadata Grey
    blue = Color(0xFF0095F6),             // Vibrant Action Blue
    blueHover = Color(0xFF60A5FA),
    blueLight = Color(0x260095F6),
    green = Color(0xFF10B981),            // Vibrant Mint
    greenLight = Color(0x2410B981),
    red = Color(0xFFED4956),              // Iconic Heart Red
    redLight = Color(0x26ED4956),
    amber = Color(0xFFF77737),
    amberLight = Color(0x26F77737),
    isDark = true
)

val AmoledFbColorScheme = FbColorScheme(
    background = Color(0xFF000000),       // Pure Pitch Black (OLED Zero Power)
    surface = Color(0xFF000000),          // Pure Flat Pitch Black
    surfaceElevated = Color(0xFF161616),  // Deep Pitch Dark Action Chip / Pill
    border = Color(0xFF262626),           // Crisp Hairline Border
    divider = Color(0xFF1A1A1A),          // Deep Hairline Separator
    textPrimary = Color(0xFFFFFFFF),      // High-Contrast Pure White
    textSecondary = Color(0xFFA8A8A8),    // Cool Light Grey
    textTertiary = Color(0xFF737373),     // Slate-500 Metadata
    blue = Color(0xFF0095F6),             // Action Blue
    blueHover = Color(0xFF60A5FA),
    blueLight = Color(0x260095F6),
    green = Color(0xFF10B981),
    greenLight = Color(0x2610B981),
    red = Color(0xFFED4956),
    redLight = Color(0x26ED4956),
    amber = Color(0xFFF77737),
    amberLight = Color(0x26F77737),
    isDark = true
)

fun FbColorScheme.withAccent(accent: String): FbColorScheme {
    return when (accent) {
        "EMERALD" -> this.copy(
            blue = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
            blueHover = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
            blueLight = if (isDark) Color(0x2610B981) else Color(0xFFECFDF5)
        )
        "PURPLE" -> this.copy(
            blue = if (isDark) Color(0xFFA855F7) else Color(0xFF7C3AED),
            blueHover = if (isDark) Color(0xFFC084FC) else Color(0xFF6D28D9),
            blueLight = if (isDark) Color(0x26A855F7) else Color(0xFFF5F3FF)
        )
        "ROSE" -> this.copy(
            blue = if (isDark) Color(0xFFF43F5E) else Color(0xFFE11D48),
            blueHover = if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C),
            blueLight = if (isDark) Color(0x26F43F5E) else Color(0xFFFFF1F2)
        )
        "AMBER" -> this.copy(
            blue = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706),
            blueHover = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
            blueLight = if (isDark) Color(0x26F59E0B) else Color(0xFFFEF3C7)
        )
        else -> this // Default Blue
    }
}

val LocalFbColors = staticCompositionLocalOf { DarkFbColorScheme }

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

// Accents & Badges (Standard dynamic or static colors)
val FbGreen: Color
    @Composable get() = LocalFbColors.current.green

val FbGreenLight: Color
    @Composable get() = LocalFbColors.current.greenLight

val FbRed: Color
    @Composable get() = LocalFbColors.current.red

val FbRedLight: Color
    @Composable get() = LocalFbColors.current.redLight

val FbAmber: Color
    @Composable get() = LocalFbColors.current.amber

val FbAmberLight: Color
    @Composable get() = LocalFbColors.current.amberLight

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

// Iconic Instagram Story Gradient
val InstagramStoryGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    listOf(
        Color(0xFF833AB4), // Purple
        Color(0xFFC13584), // Magenta
        Color(0xFFE1306C), // Crimson
        Color(0xFFFD1D1D), // Red
        Color(0xFFF77737), // Warm Orange
        Color(0xFFFCAF45)  // Golden Yellow
    )
)
