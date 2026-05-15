package com.minimalist.launcher.core.data

import android.os.Build
import android.provider.Settings
import com.minimalist.launcher.core.model.SearchResult

object SettingsActions {

    private val all: List<SearchResult.Setting> = buildList {
        add(SearchResult.Setting("Wi-Fi", Settings.ACTION_WIFI_SETTINGS))
        add(SearchResult.Setting("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS))
        add(SearchResult.Setting("Display", Settings.ACTION_DISPLAY_SETTINGS))
        add(SearchResult.Setting("Sound", Settings.ACTION_SOUND_SETTINGS))
        add(SearchResult.Setting("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS))
        add(SearchResult.Setting("Apps", Settings.ACTION_APPLICATION_SETTINGS))
        add(SearchResult.Setting("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS))
        add(SearchResult.Setting("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        add(SearchResult.Setting("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        add(SearchResult.Setting("Security", Settings.ACTION_SECURITY_SETTINGS))
        add(SearchResult.Setting("Accounts", Settings.ACTION_SYNC_SETTINGS))
        add(SearchResult.Setting("Date & Time", Settings.ACTION_DATE_SETTINGS))
        add(SearchResult.Setting("Language", Settings.ACTION_LOCALE_SETTINGS))
        add(SearchResult.Setting("NFC", Settings.ACTION_NFC_SETTINGS))
        add(SearchResult.Setting("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(SearchResult.Setting("Privacy", Settings.ACTION_PRIVACY_SETTINGS))
        }
    }

    // Normalise both sides so "wifi" matches "Wi-Fi", "bt" matches "Bluetooth", etc.
    fun search(query: String): List<SearchResult.Setting> {
        if (query.isBlank()) return emptyList()
        val q = query.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return all.filter { setting ->
            setting.label.replace(Regex("[^a-zA-Z0-9]"), "").lowercase().contains(q)
        }
    }
}
