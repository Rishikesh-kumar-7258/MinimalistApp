package com.minimalist.launcher.core.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar

class CalendarRepository(private val context: Context) {

    fun nextEvent(): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val now     = System.currentTimeMillis()
        val endTime = now + 7L * 24 * 60 * 60 * 1_000   // look 7 days ahead

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, now)
        ContentUris.appendId(uriBuilder, endTime)

        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
        )

        return try {
            context.contentResolver.query(
                uriBuilder.build(),
                projection,
                // Skip all-day events — they have no meaningful start time to display.
                "${CalendarContract.Instances.ALL_DAY} = 0",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val begin = cursor.getLong(0)
                    val title = cursor.getString(1)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return null
                    "${formatTime(begin)} — $title"
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTime(ms: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        val hour   = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val h      = when {
            hour == 0  -> 12
            hour > 12  -> hour - 12
            else       -> hour
        }
        val ampm = if (hour < 12) "am" else "pm"
        return if (minute == 0) "$h$ampm"
        else "$h:${minute.toString().padStart(2, '0')}$ampm"
    }
}
