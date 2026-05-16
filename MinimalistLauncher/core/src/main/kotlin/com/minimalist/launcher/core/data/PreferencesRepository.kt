package com.minimalist.launcher.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.minimalist.launcher.core.model.AppFontFamily
import com.minimalist.launcher.core.model.AppearanceSettings
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.FontSize
import com.minimalist.launcher.core.model.PinnedItem
import com.minimalist.launcher.core.model.SortOrder
import com.minimalist.launcher.core.model.TextAlignment
import com.minimalist.launcher.core.model.ThemeMode
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
        // Step 5 keys
        private val THEME_MODE        = stringPreferencesKey("theme_mode")
        private val FONT_SIZE         = stringPreferencesKey("font_size")
        private val FONT_FAMILY       = stringPreferencesKey("font_family")
        private val TEXT_ALIGNMENT    = stringPreferencesKey("text_alignment")
        private val BG_COLOR          = stringPreferencesKey("bg_color")
        private val TEXT_COLOR        = stringPreferencesKey("text_color")
        // Step 6 keys
        private val WEATHER_ENABLED   = booleanPreferencesKey("weather_enabled")
        private val CALENDAR_ENABLED  = booleanPreferencesKey("calendar_enabled")
        private val WEATHER_API_KEY   = stringPreferencesKey("weather_api_key")
        private val WEATHER_CITY      = stringPreferencesKey("weather_city")
        private val WEATHER_CACHE     = stringPreferencesKey("weather_cache")
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

    // ── Step 5: appearance settings ──────────────────────────────────────────

    val appearanceSettings: Flow<AppearanceSettings> = dataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode     = runCatching { ThemeMode.valueOf(prefs[THEME_MODE] ?: "") }.getOrDefault(ThemeMode.DARK),
            fontSize      = runCatching { FontSize.valueOf(prefs[FONT_SIZE] ?: "") }.getOrDefault(FontSize.MEDIUM),
            fontFamily    = runCatching { AppFontFamily.valueOf(prefs[FONT_FAMILY] ?: "") }.getOrDefault(AppFontFamily.MONOSPACE),
            textAlignment = runCatching { TextAlignment.valueOf(prefs[TEXT_ALIGNMENT] ?: "") }.getOrDefault(TextAlignment.LEFT),
            customBgColor   = prefs[BG_COLOR],
            customTextColor = prefs[TEXT_COLOR],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setFontSize(size: FontSize) {
        dataStore.edit { it[FONT_SIZE] = size.name }
    }

    suspend fun setFontFamily(family: AppFontFamily) {
        dataStore.edit { it[FONT_FAMILY] = family.name }
    }

    suspend fun setTextAlignment(alignment: TextAlignment) {
        dataStore.edit { it[TEXT_ALIGNMENT] = alignment.name }
    }

    suspend fun setCustomColors(bg: String?, text: String?) {
        dataStore.edit { prefs ->
            if (bg != null) prefs[BG_COLOR] = bg else prefs.remove(BG_COLOR)
            if (text != null) prefs[TEXT_COLOR] = text else prefs.remove(TEXT_COLOR)
        }
    }

    // ── Step 6: widget preferences ────────────────────────────────────────────

    val weatherEnabled: Flow<Boolean>  = dataStore.data.map { it[WEATHER_ENABLED]  ?: false }
    val calendarEnabled: Flow<Boolean> = dataStore.data.map { it[CALENDAR_ENABLED] ?: false }
    val weatherApiKey: Flow<String>    = dataStore.data.map { it[WEATHER_API_KEY]  ?: "" }
    val weatherCity: Flow<String>      = dataStore.data.map { it[WEATHER_CITY]     ?: "" }
    val weatherCache: Flow<String?>    = dataStore.data.map { it[WEATHER_CACHE] }

    suspend fun setWeatherEnabled(enabled: Boolean) {
        dataStore.edit { it[WEATHER_ENABLED] = enabled }
    }

    suspend fun setCalendarEnabled(enabled: Boolean) {
        dataStore.edit { it[CALENDAR_ENABLED] = enabled }
    }

    suspend fun setWeatherApiKey(key: String) {
        dataStore.edit { it[WEATHER_API_KEY] = key.trim() }
    }

    suspend fun setWeatherCity(city: String) {
        dataStore.edit { it[WEATHER_CITY] = city.trim() }
    }

    suspend fun setWeatherCache(line: String?) {
        dataStore.edit { prefs ->
            if (line != null) prefs[WEATHER_CACHE] = line else prefs.remove(WEATHER_CACHE)
        }
    }

}
