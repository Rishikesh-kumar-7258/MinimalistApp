package com.minimalist.launcher.feature.settings

import com.minimalist.launcher.core.model.AppearanceSettings
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.GestureSettings
import com.minimalist.launcher.core.model.SortOrder

data class SettingsUiState(
    // Step 5
    val appearance: AppearanceSettings = AppearanceSettings(),
    val sortOrder: SortOrder = SortOrder.ALPHABETICAL,
    val clockFormat: ClockFormat = ClockFormat.HOUR_12,
    // Step 6
    val weatherEnabled: Boolean = false,
    val calendarEnabled: Boolean = false,
    val weatherApiKey: String = "",
    val weatherCity: String = "",
    // Step 7
    val gestureSettings: GestureSettings = GestureSettings(),
    // Step 10
    val backupMessage: String = "",
)
