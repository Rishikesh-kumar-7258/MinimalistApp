package com.minimalist.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.minimalist.launcher.core.model.FocusProfile
import com.minimalist.launcher.core.model.ProfileConfig
import com.minimalist.launcher.receiver.DailyReportReceiver
import com.minimalist.launcher.receiver.ProfileAlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAll(configs: Map<FocusProfile, ProfileConfig>) {
        FocusProfile.entries.filter { it != FocusProfile.NONE }.forEach { profile ->
            schedule(profile, configs[profile] ?: ProfileConfig())
        }
    }

    fun schedule(profile: FocusProfile, config: ProfileConfig) {
        cancelAlarms(profile)
        if (!config.scheduleEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        setExactAlarm(profile, config.startTime, activate = true)
        setExactAlarm(profile, config.endTime,   activate = false)
    }

    fun cancelAlarms(profile: FocusProfile) {
        for (activate in listOf(true, false)) {
            PendingIntent.getBroadcast(
                context,
                requestCode(profile, activate),
                Intent(context, ProfileAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { alarmManager.cancel(it) }
        }
    }

    private fun setExactAlarm(profile: FocusProfile, time: String, activate: Boolean) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(profile, activate),
            Intent(context, ProfileAlarmReceiver::class.java).apply {
                putExtra(ProfileAlarmReceiver.EXTRA_PROFILE,  profile.name)
                putExtra(ProfileAlarmReceiver.EXTRA_ACTIVATE, activate)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextOccurrence(time), pi)
    }

    // ── Daily report alarm (request codes 100+) ───────────────────────────────

    fun scheduleDailyReport(time: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return
        val pi = PendingIntent.getBroadcast(
            context,
            100,
            Intent(context, DailyReportReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextOccurrence(time), pi)
    }

    fun cancelDailyReport() {
        PendingIntent.getBroadcast(
            context, 100,
            Intent(context, DailyReportReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { alarmManager.cancel(it) }
    }

    private fun requestCode(profile: FocusProfile, activate: Boolean) =
        profile.ordinal * 2 + if (activate) 0 else 1

    private fun nextOccurrence(timeStr: String): Long {
        val parts = timeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }
}
