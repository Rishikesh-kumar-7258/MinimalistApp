package com.minimalist.launcher.core.model

data class ProfileConfig(
    val blockList: Set<String> = emptySet(),
    val scheduleEnabled: Boolean = false,
    val startTime: String = "09:00",
    val endTime: String = "17:00",
)
