package com.minimalist.launcher.core.data.room

import androidx.room.Entity

@Entity(
    tableName = "app_usage",
    primaryKeys = ["packageName", "date"],
)
data class AppUsageEntity(
    val packageName: String,
    val date: String,       // "yyyy-MM-dd"
    val usageMs: Long,      // foreground milliseconds for that calendar day
)
