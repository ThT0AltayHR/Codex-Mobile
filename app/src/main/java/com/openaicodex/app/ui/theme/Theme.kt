package com.openaicodex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CodexColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    secondary = MutedWhite,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = OffWhite,
    surface = PanelBlack,
    onSurface = OffWhite,
    surfaceVariant = CardBlack,
    onSurfaceVariant = MutedWhite,
    outline = BorderGray,
    error = ErrorRed,
    onError = PureWhite
)

/**
 * Codex is intentionally monochrome and always-dark: the theme does not
 * follow system light/dark mode. This is a deliberate brand decision, not
 * an oversight — see AGENTS/spec notes for rationale.
 */
@Composable
fun CodexMobileTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        window?.let {
            it.statusBarColor = PureBlack.toArgb()
            it.navigationBarColor = PureBlack.toArgb()
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = CodexColorScheme,
        typography = CodexTypography,
        content = content
    )
}
