package com.minimalist.launcher.core.model

data class AppUsageStat(
    val packageName: String,
    val appLabel: String,
    val usageMs: Long,
)
