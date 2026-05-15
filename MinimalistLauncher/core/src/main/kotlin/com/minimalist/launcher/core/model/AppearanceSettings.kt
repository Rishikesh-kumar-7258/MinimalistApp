package com.minimalist.launcher.core.model

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val fontSize: FontSize = FontSize.MEDIUM,
    val fontFamily: AppFontFamily = AppFontFamily.MONOSPACE,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val customBgColor: String? = null,
    val customTextColor: String? = null,
)
