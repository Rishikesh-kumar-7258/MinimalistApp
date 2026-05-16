package com.minimalist.launcher.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.model.AppFontFamily
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.FontSize
import com.minimalist.launcher.core.model.GestureAction
import com.minimalist.launcher.core.model.GestureType
import com.minimalist.launcher.core.model.SortOrder
import com.minimalist.launcher.core.model.TextAlignment
import com.minimalist.launcher.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val prefs: PreferencesRepository) : ViewModel() {

    // Batching the 4 widget prefs into one combine keeps the outer combine at 4 flows
    // (Kotlin's combine supports up to 5 but clarity matters more than saving a slot).
    private data class WidgetPrefs(
        val weatherEnabled: Boolean,
        val calendarEnabled: Boolean,
        val weatherApiKey: String,
        val weatherCity: String,
    )

    private val widgetPrefs = combine(
        prefs.weatherEnabled,
        prefs.calendarEnabled,
        prefs.weatherApiKey,
        prefs.weatherCity,
    ) { we, ce, key, city -> WidgetPrefs(we, ce, key, city) }

    val uiState = combine(
        prefs.appearanceSettings,
        prefs.sortOrder,
        prefs.clockFormat,
        widgetPrefs,
        prefs.gestureSettings,
    ) { appearance, sort, clock, widget, gestures ->
        SettingsUiState(
            appearance       = appearance,
            sortOrder        = sort,
            clockFormat      = clock,
            weatherEnabled   = widget.weatherEnabled,
            calendarEnabled  = widget.calendarEnabled,
            weatherApiKey    = widget.weatherApiKey,
            weatherCity      = widget.weatherCity,
            gestureSettings  = gestures,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, SettingsUiState())

    // ── Event: request immediate weather fetch ────────────────────────────────
    // MainActivity listens to this and enqueues a OneTimeWorkRequest for WeatherWorker.

    private val _fetchWeatherNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fetchWeatherNow: SharedFlow<Unit> = _fetchWeatherNow.asSharedFlow()

    // ── Appearance (Step 5) ───────────────────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setFontSize(size: FontSize) = viewModelScope.launch { prefs.setFontSize(size) }
    fun setFontFamily(family: AppFontFamily) = viewModelScope.launch { prefs.setFontFamily(family) }
    fun setTextAlignment(alignment: TextAlignment) = viewModelScope.launch { prefs.setTextAlignment(alignment) }
    fun setPresetPalette(bg: String, text: String) = viewModelScope.launch { prefs.setCustomColors(bg, text) }
    fun clearCustomColors() = viewModelScope.launch { prefs.setCustomColors(null, null) }

    // ── Behavior (Step 5) ─────────────────────────────────────────────────────

    fun setSortOrder(order: SortOrder) = viewModelScope.launch { prefs.setSortOrder(order) }
    fun setClockFormat(format: ClockFormat) = viewModelScope.launch { prefs.setClockFormat(format) }

    // ── Widgets (Step 6) ─────────────────────────────────────────────────────

    fun setWeatherEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setWeatherEnabled(enabled)
        if (enabled) triggerFetchIfReady()
    }

    fun setCalendarEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setCalendarEnabled(enabled)
    }

    fun setWeatherApiKey(key: String) = viewModelScope.launch {
        prefs.setWeatherApiKey(key)
    }

    fun setWeatherCity(city: String) = viewModelScope.launch {
        prefs.setWeatherCity(city)
    }

    // Called explicitly from the UI when the user taps "fetch now".
    fun fetchWeatherNow() = viewModelScope.launch {
        triggerFetchIfReady()
    }

    // ── Gestures (Step 7) ─────────────────────────────────────────────────────

    fun setGestureAction(type: GestureType, action: GestureAction) = viewModelScope.launch {
        prefs.setGestureAction(type, action)
    }

    private suspend fun triggerFetchIfReady() {
        val key  = prefs.weatherApiKey.first()
        val city = prefs.weatherCity.first()
        if (key.isNotBlank() && city.isNotBlank()) {
            _fetchWeatherNow.tryEmit(Unit)
        }
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    class Factory(private val prefs: PreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(prefs) as T
    }
}
