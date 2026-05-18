package com.minimalist.launcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.minimalist.launcher.core.data.AppRepository
import com.minimalist.launcher.core.data.CalendarRepository
import com.minimalist.launcher.core.data.ContactsRepository
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.data.UsageRepository
import com.minimalist.launcher.core.data.room.AppDatabase
import com.minimalist.launcher.receiver.DailyReportReceiver
import com.minimalist.launcher.worker.UsageSnapshotWorker
import com.minimalist.launcher.worker.WeatherWorker
import java.util.concurrent.TimeUnit

private val Application.dataStore by preferencesDataStore(name = "launcher_prefs")

class LauncherApplication : Application() {
    val appRepository         by lazy { AppRepository(this) }
    val preferencesRepository by lazy { PreferencesRepository(dataStore) }
    val contactsRepository    by lazy { ContactsRepository(this) }
    val calendarRepository    by lazy { CalendarRepository(this) }
    val alarmScheduler        by lazy { AlarmScheduler(this) }
    val appDatabase           by lazy { AppDatabase.getInstance(this) }
    val usageRepository       by lazy { UsageRepository(this, appDatabase.appUsageDao()) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        schedulePeriodicWeatherFetch()
        schedulePeriodicUsageSnapshot()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DailyReportReceiver.CHANNEL_ID,
                "Daily usage report",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows your daily screen time summary" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun schedulePeriodicUsageSnapshot() {
        val request = PeriodicWorkRequestBuilder<UsageSnapshotWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_snapshot",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun schedulePeriodicWeatherFetch() {
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weather_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
