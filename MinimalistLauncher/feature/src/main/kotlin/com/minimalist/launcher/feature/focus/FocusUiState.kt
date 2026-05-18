package com.minimalist.launcher.feature.focus

import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.FocusProfile
import com.minimalist.launcher.core.model.ProfileConfig

data class FocusUiState(
    val activeProfile: FocusProfile = FocusProfile.NONE,
    val configs: Map<FocusProfile, ProfileConfig> = emptyMap(),
    val allApps: List<AppInfo> = emptyList(),
    val expandedProfile: FocusProfile? = null,
    val isLoadingApps: Boolean = true,
)
