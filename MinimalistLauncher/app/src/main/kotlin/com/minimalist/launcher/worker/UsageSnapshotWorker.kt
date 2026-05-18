package com.minimalist.launcher.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minimalist.launcher.LauncherApplication
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class UsageSnapshotWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app   = applicationContext as LauncherApplication
        val usage = app.usageRepository
        val prefs = app.preferencesRepository

        usage.snapshotToday()

        // Streak update: check if today's total is under the screen time goal.
        val goalMinutes = prefs.screenTimeGoalMinutes.first()
        if (goalMinutes > 0) {
            val today    = LocalDate.now()
            val todayStr = today.toString()
            val totalMs  = usage.totalDayMs(todayStr)
            if (totalMs / 60_000 < goalMinutes) {
                val streakCount = prefs.streakCount.first()
                val lastDate    = prefs.streakLastDate.first()
                val yesterday   = today.minusDays(1).toString()
                val newStreak = when (lastDate) {
                    todayStr  -> streakCount            // already updated today
                    yesterday -> streakCount + 1        // consecutive day
                    else      -> 1                      // streak broken, restart
                }
                prefs.updateStreak(newStreak, todayStr)
            }
        }

        return Result.success()
    }
}
