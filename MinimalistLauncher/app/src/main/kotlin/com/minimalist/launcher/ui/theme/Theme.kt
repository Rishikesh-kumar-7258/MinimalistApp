package com.minimalist.launcher.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.minimalist.launcher.core.model.AppearanceSettings
import com.minimalist.launcher.core.model.ThemeMode
import com.minimalist.launcher.feature.LocalAppearance

private fun String.toColor(): Color? = runCatching {
    Color(android.graphics.Color.parseColor(this))
}.getOrNull()

@Composable
fun MinimalistLauncherTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val isDark = appearance.themeMode != ThemeMode.LIGHT

    val customBg   = appearance.customBgColor?.toColor()
    val customText = appearance.customTextColor?.toColor()

    val colorScheme = when (appearance.themeMode) {
        ThemeMode.LIGHT -> lightColorScheme(
            background   = customBg   ?: White,
            surface      = customBg   ?: LightSurface,
            onBackground = customText ?: Black,
            onSurface    = customText ?: Black,
            primary      = customText ?: Black,
            onPrimary    = customBg   ?: White,
        )
        ThemeMode.DARK -> darkColorScheme(
            background   = customBg   ?: NearBlack,
            surface      = customBg   ?: DarkSurface,
            onBackground = customText ?: White,
            onSurface    = customText ?: White,
            primary      = customText ?: White,
            onPrimary    = customBg   ?: Black,
        )
        ThemeMode.AMOLED -> darkColorScheme(
            background   = customBg   ?: Black,
            surface      = customBg   ?: Black,
            onBackground = customText ?: White,
            onSurface    = customText ?: White,
            primary      = customText ?: White,
            onPrimary    = customBg   ?: Black,
        )
    }

    val typography = buildTypography(appearance.fontSize, appearance.fontFamily)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = typography,
            content     = content,
        )
    }
}
