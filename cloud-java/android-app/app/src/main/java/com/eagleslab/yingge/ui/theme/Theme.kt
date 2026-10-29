package com.eagleslab.yingge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.eagleslab.yingge.data.ThemePreference

private val LightColors = lightColorScheme(
    primary = YinggeBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = YinggeBlueSoft,
    onPrimaryContainer = YinggeBlueDeep,
    background = YinggeBackground,
    onBackground = YinggeInk,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = YinggeInk,
    onSurfaceVariant = YinggeMuted,
    outlineVariant = YinggeHairline,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9DB2FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00236E),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF1E3B85),
    background = YinggeDarkBackground,
    onBackground = YinggeDarkInk,
    surface = YinggeDarkSurface,
    onSurface = YinggeDarkInk,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB7C1D8),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF30394F),
    error = androidx.compose.ui.graphics.Color(0xFFFFB3B8),
)

@Composable
fun YinggeTheme(preference: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = (if (dark) YinggeDarkBackground else YinggeBackground).toArgb()
        window.navigationBarColor = (if (dark) YinggeDarkSurface else androidx.compose.ui.graphics.Color.White).toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = YinggeTypography, content = content)
}

