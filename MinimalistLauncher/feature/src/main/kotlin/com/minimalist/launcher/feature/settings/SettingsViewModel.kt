package com.minimalist.launcher.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.model.AppFontFamily
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.FontSize
import com.minimalist.launcher.core.model.SortOrder
import com.minimalist.launcher.core.model.TextAlignment
import com.minimalist.launcher.core.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val prefs: PreferencesRepository) : ViewModel() {

    val uiState = combine(
        prefs.appearanceSettings,
        prefs.sortOrder,
        prefs.clockFormat,
    ) { appearance, sort, clock ->
        SettingsUiState(appearance = appearance, sortOrder = sort, clockFormat = clock)
    }.stateIn(viewModelScope, SharingStarted.Lazily, SettingsUiState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setFontSize(size: FontSize) = viewModelScope.launch { prefs.setFontSize(size) }
    fun setFontFamily(family: AppFontFamily) = viewModelScope.launch { prefs.setFontFamily(family) }
    fun setTextAlignment(alignment: TextAlignment) = viewModelScope.launch { prefs.setTextAlignment(alignment) }
    fun setPresetPalette(bg: String, text: String) = viewModelScope.launch { prefs.setCustomColors(bg, text) }
    fun clearCustomColors() = viewModelScope.launch { prefs.setCustomColors(null, null) }
    fun setSortOrder(order: SortOrder) = viewModelScope.launch { prefs.setSortOrder(order) }
    fun setClockFormat(format: ClockFormat) = viewModelScope.launch { prefs.setClockFormat(format) }

    class Factory(private val prefs: PreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(prefs) as T
    }
}
