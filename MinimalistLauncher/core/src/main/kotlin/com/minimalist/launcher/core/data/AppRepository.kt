package com.minimalist.launcher.core.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import com.minimalist.launcher.core.model.AppInfo

class AppRepository(private val context: Context) {

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        // queryIntentActivities returns one entry per Activity, not per package.
        // A single package can expose multiple launcher activities (common on OEM devices).
        // distinctBy ensures each package appears exactly once so LazyColumn keys never collide.
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                AppInfo(pkg, info.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
    }

    fun launch(packageName: String) {
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?.let { context.startActivity(it) }
    }

    fun launchDialer() {
        val intent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) }
        catch (e: ActivityNotFoundException) { Log.w("AppRepository", "No dialer app found") }
    }

    fun launchContact(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("AppRepository", "No dialer found for $phoneNumber")
        }
    }

    fun launchSetting(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("AppRepository", "Settings action not available: $action")
        }
    }

    fun launchClockApp() {
        val pm = context.packageManager
        // Try actions in priority order; the first one that resolves is used.
        val candidates = listOf(
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(AlarmClock.ACTION_SHOW_TIMERS),
            Intent(AlarmClock.ACTION_SET_ALARM),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                try { context.startActivity(intent); return }
                catch (_: ActivityNotFoundException) { /* try next */ }
            }
        }
        Log.w("AppRepository", "No clock app resolved for any alarm intent")
    }

    fun launchCalendarApp() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) }
        catch (e: ActivityNotFoundException) { Log.w("AppRepository", "No calendar app") }
    }

    fun launchGoogleSearch() {
        // Prefer the Google quicksearch activity; fall back to browser.
        val pm = context.packageManager
        val googlePkg = "com.google.android.googlequicksearchbox"
        val primary = pm.getLaunchIntentForPackage(googlePkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (primary != null) {
            try { context.startActivity(primary); return }
            catch (_: ActivityNotFoundException) { }
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Log.w("AppRepository", "No browser for Google fallback")
        }
    }
}
