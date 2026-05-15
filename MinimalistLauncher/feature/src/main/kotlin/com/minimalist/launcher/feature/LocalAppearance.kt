package com.minimalist.launcher.feature

import androidx.compose.runtime.compositionLocalOf
import com.minimalist.launcher.core.model.AppearanceSettings

val LocalAppearance = compositionLocalOf { AppearanceSettings() }
