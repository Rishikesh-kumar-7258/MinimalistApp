package com.minimalist.launcher.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.minimalist.launcher.LauncherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as LauncherApplication

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val stats = app.usageRepository.todayStats().take(5)
                val body = if (stats.isEmpty()) {
                    "No usage data available."
                } else {
                    stats.joinToString("\n") { "${it.appLabel}: ${it.usageMs.toMinutesStr()}" }
                }

                val nm = context.getSystemService(NotificationManager::class.java)
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                    .setContentTitle("Today's screen time")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .build()
                nm.notify(NOTIFICATION_ID, notification)

                // Re-schedule for tomorrow at the same time.
                val reportTime = app.preferencesRepository.reportTime.first()
                app.alarmScheduler.scheduleDailyReport(reportTime)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val CHANNEL_ID     = "daily_report"
        const val NOTIFICATION_ID = 1001

        private fun Long.toMinutesStr(): String {
            val m = this / 60_000
            return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
        }
    }
}
