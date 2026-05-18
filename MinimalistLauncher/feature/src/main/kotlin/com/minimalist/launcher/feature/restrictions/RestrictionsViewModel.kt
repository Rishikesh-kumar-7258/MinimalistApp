package com.minimalist.launcher.feature.restrictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.core.data.AppRepository
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RestrictionsViewModel(
    private val appRepository: AppRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _apps      = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    val uiState = combine(
        _apps,
        _isLoading,
        prefs.appDailyLimits,
        prefs.appTimeWindows,
        prefs.lockedApps,
    ) { apps, loading, limits, windows, locked ->
        RestrictionsUiState(
            apps         = apps,
            isLoading    = loading,
            dailyLimits  = limits,
            timeWindows  = windows,
            lockedApps   = locked,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, RestrictionsUiState())

    init { loadApps() }

    private fun loadApps() = viewModelScope.launch(Dispatchers.IO) {
        _isLoading.value = true
        _apps.value = runCatching { appRepository.getInstalledApps() }.getOrDefault(emptyList())
        _isLoading.value = false
    }

    fun setDailyLimit(packageName: String, minutes: Int) = viewModelScope.launch {
        prefs.setDailyLimit(packageName, minutes)
    }

    fun clearDailyLimit(packageName: String) = viewModelScope.launch {
        prefs.setDailyLimit(packageName, 0)
    }

    fun setTimeWindow(packageName: String, start: String?, end: String?) = viewModelScope.launch {
        prefs.setTimeWindow(packageName, start, end)
    }

    fun toggleLock(packageName: String) = viewModelScope.launch {
        prefs.toggleLockedApp(packageName)
    }

    class Factory(
        private val appRepository: AppRepository,
        private val prefs: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RestrictionsViewModel(appRepository, prefs) as T
    }
}
