package com.minimalist.launcher.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.core.data.AppRepository
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.model.FocusProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FocusViewModel(
    private val prefs: PreferencesRepository,
    private val appRepository: AppRepository,
) : ViewModel() {

    private val _expandedProfile = MutableStateFlow<FocusProfile?>(null)
    private val _isLoadingApps   = MutableStateFlow(true)
    private val _allApps         = MutableStateFlow(emptyList<com.minimalist.launcher.core.model.AppInfo>())

    val uiState = combine(
        prefs.activeProfile,
        prefs.allProfileConfigs,
        _expandedProfile,
        _allApps,
        _isLoadingApps,
    ) { active, configs, expanded, apps, loading ->
        FocusUiState(
            activeProfile   = active,
            configs         = configs,
            allApps         = apps,
            expandedProfile = expanded,
            isLoadingApps   = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, FocusUiState())

    // Emitted when any schedule config changes so MainActivity can re-run AlarmScheduler.
    private val _scheduleChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scheduleChanged: SharedFlow<Unit> = _scheduleChanged.asSharedFlow()

    init {
        loadApps()
    }

    private fun loadApps() = viewModelScope.launch(Dispatchers.IO) {
        _isLoadingApps.value = true
        _allApps.value = runCatching { appRepository.getInstalledApps() }.getOrDefault(emptyList())
        _isLoadingApps.value = false
    }

    // ── Profile switching ─────────────────────────────────────────────────────

    fun setActiveProfile(profile: FocusProfile) = viewModelScope.launch {
        prefs.setActiveProfile(profile)
    }

    // ── Expand/collapse ───────────────────────────────────────────────────────

    fun toggleExpanded(profile: FocusProfile) {
        _expandedProfile.update { if (it == profile) null else profile }
    }

    // ── Block-list ────────────────────────────────────────────────────────────

    fun toggleBlockedApp(profile: FocusProfile, packageName: String) = viewModelScope.launch {
        prefs.toggleBlockedApp(profile, packageName)
    }

    // ── Schedule ─────────────────────────────────────────────────────────────

    fun setScheduleEnabled(profile: FocusProfile, enabled: Boolean) = viewModelScope.launch {
        prefs.setProfileScheduleEnabled(profile, enabled)
        _scheduleChanged.tryEmit(Unit)
    }

    fun setStartTime(profile: FocusProfile, time: String) = viewModelScope.launch {
        prefs.setProfileStartTime(profile, time)
        _scheduleChanged.tryEmit(Unit)
    }

    fun setEndTime(profile: FocusProfile, time: String) = viewModelScope.launch {
        prefs.setProfileEndTime(profile, time)
        _scheduleChanged.tryEmit(Unit)
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    class Factory(
        private val prefs: PreferencesRepository,
        private val appRepository: AppRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FocusViewModel(prefs, appRepository) as T
    }
}
