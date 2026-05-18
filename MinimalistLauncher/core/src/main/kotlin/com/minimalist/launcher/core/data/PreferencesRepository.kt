package com.minimalist.launcher.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.minimalist.launcher.core.model.AppFontFamily
import com.minimalist.launcher.core.model.AppearanceSettings
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.FontSize
import com.minimalist.launcher.core.model.FocusProfile
import com.minimalist.launcher.core.model.GestureAction
import com.minimalist.launcher.core.model.GestureSettings
import com.minimalist.launcher.core.model.GestureType
import com.minimalist.launcher.core.model.PinnedItem
import com.minimalist.launcher.core.model.ProfileConfig
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
        // Step 7 keys
        private val GESTURE_SWIPE_UP    = stringPreferencesKey("gesture_swipe_up")
        private val GESTURE_SWIPE_DOWN  = stringPreferencesKey("gesture_swipe_down")
        private val GESTURE_SWIPE_LEFT  = stringPreferencesKey("gesture_swipe_left")
        private val GESTURE_SWIPE_RIGHT = stringPreferencesKey("gesture_swipe_right")
        private val GESTURE_DOUBLE_TAP  = stringPreferencesKey("gesture_double_tap")
        // Step 8 keys
        private val PROFILE_ACTIVE = stringPreferencesKey("profile_active")
        private fun profileBlockListKey(p: FocusProfile)       = stringSetPreferencesKey("profile_${p.name.lowercase()}_blocklist")
        private fun profileScheduleEnabledKey(p: FocusProfile) = booleanPreferencesKey("profile_${p.name.lowercase()}_schedule_enabled")
        private fun profileStartTimeKey(p: FocusProfile)       = stringPreferencesKey("profile_${p.name.lowercase()}_start_time")
        private fun profileEndTimeKey(p: FocusProfile)         = stringPreferencesKey("profile_${p.name.lowercase()}_end_time")
        // Step 9 keys
        private val APP_DAILY_LIMITS         = stringSetPreferencesKey("app_daily_limits")
        private val APP_TIME_WINDOWS         = stringSetPreferencesKey("app_time_windows")
        private val FRICTION_MESSAGE         = stringPreferencesKey("friction_message")
        private val REPORT_ENABLED           = booleanPreferencesKey("report_enabled")
        private val REPORT_TIME              = stringPreferencesKey("report_time")
        private val SCREEN_TIME_GOAL_MINUTES = intPreferencesKey("screen_time_goal_minutes")
        private val STREAK_COUNT             = intPreferencesKey("streak_count")
        private val STREAK_LAST_DATE         = stringPreferencesKey("streak_last_date")
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

    // ── Step 7: gesture mappings ──────────────────────────────────────────────

    val gestureSettings: Flow<GestureSettings> = dataStore.data.map { prefs ->
        fun action(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: GestureAction): GestureAction =
            runCatching { GestureAction.valueOf(prefs[key] ?: "") }.getOrDefault(default)
        GestureSettings(
            swipeUp    = action(GESTURE_SWIPE_UP,    GestureAction.APP_DRAWER),
            swipeDown  = action(GESTURE_SWIPE_DOWN,  GestureAction.SEARCH),
            swipeLeft  = action(GESTURE_SWIPE_LEFT,  GestureAction.NONE),
            swipeRight = action(GESTURE_SWIPE_RIGHT, GestureAction.NONE),
            doubleTap  = action(GESTURE_DOUBLE_TAP,  GestureAction.SCRATCH_PAD),
        )
    }

    suspend fun setGestureAction(type: GestureType, action: GestureAction) {
        dataStore.edit { prefs ->
            val key = when (type) {
                GestureType.SWIPE_UP    -> GESTURE_SWIPE_UP
                GestureType.SWIPE_DOWN  -> GESTURE_SWIPE_DOWN
                GestureType.SWIPE_LEFT  -> GESTURE_SWIPE_LEFT
                GestureType.SWIPE_RIGHT -> GESTURE_SWIPE_RIGHT
                GestureType.DOUBLE_TAP  -> GESTURE_DOUBLE_TAP
            }
            prefs[key] = action.name
        }
    }

    // ── Step 8: focus profiles ────────────────────────────────────────────────

    val activeProfile: Flow<FocusProfile> = dataStore.data.map { prefs ->
        runCatching { FocusProfile.valueOf(prefs[PROFILE_ACTIVE] ?: "") }
            .getOrDefault(FocusProfile.NONE)
    }

    // All 4 configurable profiles read in one map pass over the DataStore snapshot.
    val allProfileConfigs: Flow<Map<FocusProfile, ProfileConfig>> = dataStore.data.map { prefs ->
        FocusProfile.entries.filter { it != FocusProfile.NONE }.associateWith { p ->
            ProfileConfig(
                blockList       = prefs[profileBlockListKey(p)]       ?: emptySet(),
                scheduleEnabled = prefs[profileScheduleEnabledKey(p)] ?: false,
                startTime       = prefs[profileStartTimeKey(p)]       ?: "09:00",
                endTime         = prefs[profileEndTimeKey(p)]         ?: "17:00",
            )
        }
    }

    suspend fun setActiveProfile(profile: FocusProfile) {
        dataStore.edit { it[PROFILE_ACTIVE] = profile.name }
    }

    suspend fun toggleBlockedApp(profile: FocusProfile, packageName: String) {
        dataStore.edit { prefs ->
            val key = profileBlockListKey(profile)
            val set = (prefs[key] ?: emptySet()).toMutableSet()
            if (packageName in set) set.remove(packageName) else set.add(packageName)
            prefs[key] = set
        }
    }

    suspend fun setProfileScheduleEnabled(profile: FocusProfile, enabled: Boolean) {
        dataStore.edit { it[profileScheduleEnabledKey(profile)] = enabled }
    }

    suspend fun setProfileStartTime(profile: FocusProfile, time: String) {
        dataStore.edit { it[profileStartTimeKey(profile)] = time }
    }

    suspend fun setProfileEndTime(profile: FocusProfile, time: String) {
        dataStore.edit { it[profileEndTimeKey(profile)] = time }
    }

    // ── Step 9: usage restrictions ────────────────────────────────────────────

    // Encoded as "packageName:minutes" — 0 minutes = unlimited.
    val appDailyLimits: Flow<Map<String, Int>> = dataStore.data.map { prefs ->
        prefs[APP_DAILY_LIMITS]?.associate { entry ->
            val colon = entry.indexOf(':')
            if (colon < 0) return@associate (entry to 0)
            entry.substring(0, colon) to (entry.substring(colon + 1).toIntOrNull() ?: 0)
        } ?: emptyMap()
    }

    // Encoded as "packageName:HH:mm-HH:mm".
    val appTimeWindows: Flow<Map<String, Pair<String, String>>> = dataStore.data.map { prefs ->
        prefs[APP_TIME_WINDOWS]?.associate { entry ->
            val colon = entry.indexOf(':')
            if (colon < 0) return@associate (entry to ("00:00" to "23:59"))
            val pkg   = entry.substring(0, colon)
            val value = entry.substring(colon + 1)          // "HH:mm-HH:mm"
            val dash  = value.indexOf('-')
            val start = if (dash < 0) "00:00" else value.substring(0, dash)
            val end   = if (dash < 0) "23:59" else value.substring(dash + 1)
            pkg to (start to end)
        } ?: emptyMap()
    }

    val frictionMessage: Flow<String> = dataStore.data.map { prefs ->
        prefs[FRICTION_MESSAGE] ?: "Take a breath. Do you really need this right now?"
    }

    val reportEnabled: Flow<Boolean>  = dataStore.data.map { it[REPORT_ENABLED]  ?: false }
    val reportTime: Flow<String>      = dataStore.data.map { it[REPORT_TIME]     ?: "21:00" }
    val screenTimeGoalMinutes: Flow<Int> = dataStore.data.map { it[SCREEN_TIME_GOAL_MINUTES] ?: 0 }
    val streakCount: Flow<Int>        = dataStore.data.map { it[STREAK_COUNT]     ?: 0 }
    val streakLastDate: Flow<String>  = dataStore.data.map { it[STREAK_LAST_DATE] ?: "" }

    suspend fun setDailyLimit(packageName: String, minutes: Int) {
        dataStore.edit { prefs ->
            val set = (prefs[APP_DAILY_LIMITS] ?: emptySet()).toMutableSet()
            set.removeAll { it.startsWith("$packageName:") }
            if (minutes > 0) set.add("$packageName:$minutes")
            prefs[APP_DAILY_LIMITS] = set
        }
    }

    suspend fun setTimeWindow(packageName: String, start: String?, end: String?) {
        dataStore.edit { prefs ->
            val set = (prefs[APP_TIME_WINDOWS] ?: emptySet()).toMutableSet()
            set.removeAll { it.startsWith("$packageName:") }
            if (start != null && end != null) set.add("$packageName:$start-$end")
            prefs[APP_TIME_WINDOWS] = set
        }
    }

    suspend fun setFrictionMessage(msg: String) {
        dataStore.edit { it[FRICTION_MESSAGE] = msg.ifBlank { "Take a breath. Do you really need this right now?" } }
    }

    suspend fun setReportEnabled(enabled: Boolean) { dataStore.edit { it[REPORT_ENABLED] = enabled } }
    suspend fun setReportTime(time: String)         { dataStore.edit { it[REPORT_TIME]    = time } }
    suspend fun setScreenTimeGoal(minutes: Int)     { dataStore.edit { it[SCREEN_TIME_GOAL_MINUTES] = minutes } }

    suspend fun updateStreak(count: Int, date: String) {
        dataStore.edit { prefs ->
            prefs[STREAK_COUNT]     = count
            prefs[STREAK_LAST_DATE] = date
        }
    }

}
