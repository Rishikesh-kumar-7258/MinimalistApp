package com.minimalist.launcher.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.PinnedItem
import com.minimalist.launcher.core.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val HIDDEN_APPS   = stringSetPreferencesKey("hidden_apps")
        private val SORT_ORDER    = stringPreferencesKey("sort_order")
        private val LAUNCH_COUNTS = stringSetPreferencesKey("launch_counts")
        // Step 4 keys
        private val CLOCK_FORMAT  = stringPreferencesKey("clock_format")
        // 5 independent slot keys so individual slots can be updated atomically.
        private fun pinnedKey(slot: Int) = stringPreferencesKey("pinned_$slot")
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

    // ── Step 4: clock format ─────────────────────────────────────────────────

    val clockFormat: Flow<ClockFormat> = dataStore.data.map { prefs ->
        runCatching { ClockFormat.valueOf(prefs[CLOCK_FORMAT] ?: "") }
            .getOrDefault(ClockFormat.HOUR_12)
    }

    suspend fun setClockFormat(format: ClockFormat) {
        dataStore.edit { it[CLOCK_FORMAT] = format.name }
    }

    // ── Step 4: pinned shortcuts (5 slots, null = empty) ─────────────────────

    // Encoded as "app::packageName::Label" or "contact::number::Name".
    // The "::" separator is safe: package names forbid colons, phone numbers
    // don't contain double-colons, and display names that do are extremely rare.

    fun pinnedItems(): Flow<List<PinnedItem?>> = dataStore.data.map { prefs ->
        (0 until 5).map { slot ->
            prefs[pinnedKey(slot)]
                ?.takeIf { it.isNotBlank() }
                ?.let { decode(it) }
        }
    }

    suspend fun setPinnedItem(slot: Int, item: PinnedItem?) {
        dataStore.edit { prefs ->
            val key = pinnedKey(slot)
            if (item == null) prefs.remove(key) else prefs[key] = encode(item)
        }
    }

    private fun decode(raw: String): PinnedItem? {
        val parts = raw.split("::")
        return when (parts.getOrNull(0)) {
            "app"     -> if (parts.size >= 3) PinnedItem.App(parts[1], parts[2]) else null
            "contact" -> if (parts.size >= 3) PinnedItem.Contact(parts[1], parts[2]) else null
            else      -> null
        }
    }

    private fun encode(item: PinnedItem): String = when (item) {
        is PinnedItem.App     -> "app::${item.packageName}::${item.label}"
        is PinnedItem.Contact -> "contact::${item.number}::${item.name}"
    }

}
