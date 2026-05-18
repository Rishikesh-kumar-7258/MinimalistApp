package com.minimalist.launcher.core.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.minimalist.launcher.core.data.room.AppUsageDao
import com.minimalist.launcher.core.data.room.AppUsageEntity
import com.minimalist.launcher.core.model.AppUsageStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

class UsageRepository(
    private val context: Context,
    private val dao: AppUsageDao,
) {

    // ── Live UsageStatsManager query ──────────────────────────────────────────

    fun queryTodayRawMs(): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis(),
        ).filter { it.totalTimeInForeground > 0 }
            .associate { it.packageName to it.totalTimeInForeground }
    }

    fun todayStats(): List<AppUsageStat> {
        val pm    = context.packageManager
        val usage = queryTodayRawMs()
        return usage.mapNotNull { (pkg, ms) ->
            val label = try {
                pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA).loadLabel(pm).toString()
            } catch (_: PackageManager.NameNotFoundException) { null } ?: return@mapNotNull null
            AppUsageStat(packageName = pkg, appLabel = label, usageMs = ms)
        }.sortedByDescending { it.usageMs }
    }

    // ── Room snapshot ─────────────────────────────────────────────────────────

    suspend fun snapshotToday() {
        val today    = LocalDate.now().toString()
        val usage    = queryTodayRawMs()
        val entities = usage.map { (pkg, ms) -> AppUsageEntity(pkg, today, ms) }
        if (entities.isNotEmpty()) dao.upsertAll(entities)
    }

    fun todayStatsFlow(): Flow<List<AppUsageStat>> {
        val today = LocalDate.now().toString()
        val pm    = context.packageManager
        return dao.getUsageForDate(today).map { entities ->
            entities.mapNotNull { e ->
                val label = try {
                    pm.getApplicationInfo(e.packageName, 0).loadLabel(pm).toString()
                } catch (_: PackageManager.NameNotFoundException) { null } ?: return@mapNotNull null
                AppUsageStat(e.packageName, label, e.usageMs)
            }
        }
    }

    // ── Weekly data for trends ────────────────────────────────────────────────

    suspend fun weekStats(startDate: String, endDate: String): List<AppUsageStat> =
        withContext(Dispatchers.IO) {
            val pm       = context.packageManager
            val entities = dao.getUsageForRange(startDate, endDate)
            // Sum per package across the range
            entities.groupBy { it.packageName }
                .map { (pkg, list) ->
                    val label = try {
                        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                    } catch (_: PackageManager.NameNotFoundException) { pkg }
                    AppUsageStat(pkg, label, list.sumOf { it.usageMs })
                }
                .sortedByDescending { it.usageMs }
        }

    suspend fun totalDayMs(date: String): Long = dao.getTotalUsageMs(date)
}
