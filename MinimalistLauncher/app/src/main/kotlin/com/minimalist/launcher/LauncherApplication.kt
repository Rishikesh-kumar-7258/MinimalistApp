package com.minimalist.launcher

import android.app.Application
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
import com.minimalist.launcher.worker.WeatherWorker
import java.util.concurrent.TimeUnit

private val Application.dataStore by preferencesDataStore(name = "launcher_prefs")

class LauncherApplication : Application() {
    val appRepository         by lazy { AppRepository(this) }
    val preferencesRepository by lazy { PreferencesRepository(dataStore) }
    val contactsRepository    by lazy { ContactsRepository(this) }
    val calendarRepository    by lazy { CalendarRepository(this) }

    override fun onCreate() {
        super.onCreate()
        schedulePeriodicWeatherFetch()
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
