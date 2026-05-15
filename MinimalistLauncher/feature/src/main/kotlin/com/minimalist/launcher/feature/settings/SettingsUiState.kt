package com.minimalist.launcher.feature.settings

import com.minimalist.launcher.core.model.AppearanceSettings
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.SortOrder

data class SettingsUiState(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val sortOrder: SortOrder = SortOrder.ALPHABETICAL,
    val clockFormat: ClockFormat = ClockFormat.HOUR_12,
)
