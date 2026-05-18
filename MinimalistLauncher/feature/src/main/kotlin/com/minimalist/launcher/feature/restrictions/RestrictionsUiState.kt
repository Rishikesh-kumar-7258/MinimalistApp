package com.minimalist.launcher.feature.restrictions

import com.minimalist.launcher.core.model.AppInfo

data class RestrictionsUiState(
    val apps: List<AppInfo> = emptyList(),
    val dailyLimits: Map<String, Int> = emptyMap(),
    val timeWindows: Map<String, Pair<String, String>> = emptyMap(),
    val lockedApps: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)
