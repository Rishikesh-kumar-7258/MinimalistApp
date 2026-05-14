package com.minimalist.launcher.feature.appdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.core.data.AppRepository
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDrawerViewModel(
    private val appRepository: AppRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val selectedApp = MutableStateFlow<AppInfo?>(null)
    private val isLoading = MutableStateFlow(true)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                allApps.value = appRepository.getInstalledApps()
            } finally {
                isLoading.value = false
            }
        }
    }

    private val appState = combine(allApps, isLoading) { apps, loading -> Pair(apps, loading) }

    private val prefState = combine(
        preferencesRepository.hiddenApps,
        preferencesRepository.sortOrder,
        preferencesRepository.launchCounts
    ) { hidden, sort, counts -> Triple(hidden, sort, counts) }

    val uiState = combine(appState, prefState, selectedApp) { (apps, loading), (hidden, sort, counts), selected ->
        val visible = apps.filter { it.packageName !in hidden }
        val sorted = when (sort) {
            SortOrder.ALPHABETICAL -> visible.sortedBy { it.label.lowercase() }
            SortOrder.FREQUENCY -> visible.sortedByDescending { counts[it.packageName] ?: 0 }
        }
        AppDrawerUiState(
            apps = sorted,
            sortOrder = sort,
            isLoading = loading,
            selectedApp = selected
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppDrawerUiState()
    )

    fun onAppClick(app: AppInfo) {
        viewModelScope.launch {
            preferencesRepository.recordLaunch(app.packageName)
            appRepository.launch(app.packageName)
        }
    }

    fun onAppLongPress(app: AppInfo) {
        selectedApp.value = app
    }

    fun hideApp(app: AppInfo) {
        viewModelScope.launch {
            preferencesRepository.setHidden(app.packageName, true)
            selectedApp.value = null
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { preferencesRepository.setSortOrder(order) }
    }

    fun dismissBottomSheet() {
        selectedApp.value = null
    }

    class Factory(
        private val appRepository: AppRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppDrawerViewModel(appRepository, preferencesRepository) as T
    }
}
