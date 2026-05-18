package com.minimalist.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minimalist.launcher.LauncherApplication
import com.minimalist.launcher.core.model.FocusProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val profile = intent.getStringExtra(EXTRA_PROFILE)
            ?.let { runCatching { FocusProfile.valueOf(it) }.getOrNull() }
            ?: run { pendingResult.finish(); return }
        val activate = intent.getBooleanExtra(EXTRA_ACTIVATE, true)

        val app = context.applicationContext as LauncherApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val target = if (activate) profile else FocusProfile.NONE
                app.preferencesRepository.setActiveProfile(target)
                // Re-schedule for next day so the alarm repeats daily.
                val configs = app.preferencesRepository.allProfileConfigs.first()
                app.alarmScheduler.schedule(profile, configs[profile] ?: return@launch)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PROFILE  = "profile"
        const val EXTRA_ACTIVATE = "activate"
    }
}
