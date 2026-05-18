package com.minimalist.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minimalist.launcher.LauncherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val app = context.applicationContext as LauncherApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val configs = app.preferencesRepository.allProfileConfigs.first()
                app.alarmScheduler.scheduleAll(configs)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
