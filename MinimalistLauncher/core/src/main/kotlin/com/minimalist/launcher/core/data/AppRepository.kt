package com.minimalist.launcher.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
}
