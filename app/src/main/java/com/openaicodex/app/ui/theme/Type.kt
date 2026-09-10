package com.openaicodex.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Elegant italic serif display face for headers/onboarding copy.
//
// NOTE FOR BUILDER: for the authentic premium look, add a real italic serif
// .ttf (e.g. "PlayfairDisplay-Italic.ttf") as res/font/display_italic.ttf,
// then swap DisplayItalicFamily below for:
//   FontFamily(Font(R.font.display_italic, style = FontStyle.Italic))
// Until then this uses the system serif in italic so the project builds
// out of the box with zero missing-resource errors.
val DisplayItalicFamily = FontFamily.Serif

// Monospace for code blocks / terminal output — the working voice of the app.
// Swap in JetBrains Mono / Berkeley Mono as res/font/mono_regular.ttf for the
// authentic terminal look; falls back to system monospace otherwise.
val TerminalMonoFamily = FontFamily.Monospace

// Clean grotesque for body chat text.
val BodyFamily = FontFamily.SansSerif

val CodexTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayItalicFamily,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.2.sp
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayItalicFamily,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = TerminalMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

// Used directly (not part of Material Typography) for code/terminal blocks.
val CodeBlockStyle = TextStyle(
    fontFamily = TerminalMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.5.sp,
    lineHeight = 19.sp
)
