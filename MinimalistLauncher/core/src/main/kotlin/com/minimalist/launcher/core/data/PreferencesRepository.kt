package com.minimalist.launcher.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.minimalist.launcher.core.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        private val SORT_ORDER = stringPreferencesKey("sort_order")
        // Stored as "packageName:count" entries — colons are illegal in Android package names.
        private val LAUNCH_COUNTS = stringSetPreferencesKey("launch_counts")
    }

    val hiddenApps: Flow<Set<String>> =
        dataStore.data.map { it[HIDDEN_APPS] ?: emptySet() }

    val sortOrder: Flow<SortOrder> =
        dataStore.data.map { prefs ->
            runCatching { SortOrder.valueOf(prefs[SORT_ORDER] ?: "") }
                .getOrDefault(SortOrder.ALPHABETICAL)
        }

    val launchCounts: Flow<Map<String, Int>> =
        dataStore.data.map { prefs ->
            prefs[LAUNCH_COUNTS]?.mapNotNull { entry ->
                val colon = entry.indexOf(':')
                if (colon >= 0) {
                    val packageName = entry.substring(0, colon)
                    val count = entry.substring(colon + 1).toIntOrNull() ?: 0
                    if (packageName.isNotEmpty()) {
                        packageName to count
                    } else {
                        null
                    }
                } else {
                    null
                }
            }?.associate { it } ?: emptyMap()
        }

    suspend fun setHidden(packageName: String, hidden: Boolean) {
        dataStore.edit { prefs ->
            val set = prefs[HIDDEN_APPS]?.toMutableSet() ?: mutableSetOf()
            if (hidden) set.add(packageName) else set.remove(packageName)
            prefs[HIDDEN_APPS] = set
        }
    }

    suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[SORT_ORDER] = order.name }
    }

    suspend fun recordLaunch(packageName: String) {
        dataStore.edit { prefs ->
            val set = prefs[LAUNCH_COUNTS]?.toMutableSet() ?: mutableSetOf()
            val existing = set.find { it.startsWith("$packageName:") }
            val count = existing?.substringAfter(':')?.toIntOrNull() ?: 0
            if (existing != null) set.remove(existing)
            set.add("$packageName:${count + 1}")
            prefs[LAUNCH_COUNTS] = set
        }
    }

}
