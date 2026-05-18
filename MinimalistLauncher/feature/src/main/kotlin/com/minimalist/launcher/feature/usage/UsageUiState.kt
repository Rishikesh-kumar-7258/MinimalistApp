package com.minimalist.launcher.feature.usage

import com.minimalist.launcher.core.model.AppUsageStat

data class UsageUiState(
    val todayStats: List<AppUsageStat> = emptyList(),
    val thisWeekStats: List<AppUsageStat> = emptyList(),
    val lastWeekStats: List<AppUsageStat> = emptyList(),
    val streakCount: Int = 0,
    val streakLastDate: String = "",
    val screenTimeGoalMinutes: Int = 0,
    val reportEnabled: Boolean = false,
    val reportTime: String = "21:00",
    val frictionMessage: String = "Take a breath. Do you really need this right now?",
    val isLoading: Boolean = true,
)
