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
    val red: Color = Color(0xFFF43F5E),
    val redLight: Color,
    val amber: Color = Color(0xFFF59E0B),
    val amberLight: Color,
    val isDark: Boolean
)

val LightFbColorScheme = FbColorScheme(
    background = Color(0xFFF1F5F9),       // Soft soothing Slate-100 (never harsh glare)
    surface = Color(0xFFFFFFFF),          // Clean Card Surface
    surfaceElevated = Color(0xFFE2E8F0),  // Slate-200 Chip / Action Container
    border = Color(0xFFCBD5E1),           // Slate-300 Crisp Outline
    divider = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),      // Deep Slate 900
    textSecondary = Color(0xFF475569),    // Slate 600
    textTertiary = Color(0xFF64748B),     // Slate 500
    blue = Color(0xFF2563EB),             // Royal Electric Blue
    blueHover = Color(0xFF1D4ED8),
    blueLight = Color(0xFFEFF6FF),        // Soft Blue Wash
    green = Color(0xFF059669),            // Rich Emerald
    greenLight = Color(0xFFECFDF5),
    red = Color(0xFFE11D48),              // Vibrant Rose
    redLight = Color(0xFFFFF1F2),
    amber = Color(0xFFD97706),
    amberLight = Color(0xFFFEF3C7),
    isDark = false
)

val DarkFbColorScheme = FbColorScheme(
    background = Color(0xFF090D16),       // Ultra-Deep Midnight Obsidian Canvas (Zero Eye Strain)
    surface = Color(0xFF111827),          // Elevated Slate-900 Card with Subtle Depth
    surfaceElevated = Color(0xFF1E293B),  // Slate-800 Pill / Chip / Elevated Action
    border = Color(0xFF1E2E4A),           // Subtle Luminous Navy Micro-Border
    divider = Color(0xFF162238),          // Refined Hairline Separator
    textPrimary = Color(0xFFF8FAFC),      // Crisp Soft White (Sharp, Non-Glare)
    textSecondary = Color(0xFF94A3B8),    // Cool Slate-400
    textTertiary = Color(0xFF64748B),     // Slate-500 Metadata
    blue = Color(0xFF38BDF8),             // Electric Cyan / Radiant Sapphire
    blueHover = Color(0xFF60A5FA),        // Soft Sky
    blueLight = Color(0x2638BDF8),        // Luminous Cyber Tint
    green = Color(0xFF10B981),            // Vibrant Mint / Emerald
    greenLight = Color(0x2410B981),       // Translucent Green Glow
    red = Color(0xFFF43F5E),              // Neon Rose / Ruby for SOS Emergency
    redLight = Color(0x26F43F5E),         // Translucent Rose Glow
    amber = Color(0xFFF59E0B),            // Radiant Amber for Polls
    amberLight = Color(0x26F59E0B),
    isDark = true
)

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
