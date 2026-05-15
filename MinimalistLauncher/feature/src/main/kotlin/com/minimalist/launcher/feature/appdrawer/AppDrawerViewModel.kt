package com.minimalist.launcher.feature.appdrawer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.core.data.AppRepository
import com.minimalist.launcher.core.data.ContactsRepository
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.data.SettingsActions
import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.SearchResult
import com.minimalist.launcher.core.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDrawerViewModel(
    private val appRepository: AppRepository,
    private val preferencesRepository: PreferencesRepository,
    private val contactsRepository: ContactsRepository,
) : ViewModel() {

    // ── App list ────────────────────────────────────────────────────────────
    private val allApps   = MutableStateFlow<List<AppInfo>>(emptyList())
    private val isLoading = MutableStateFlow(true)
    private val loadError = MutableStateFlow<Throwable?>(null)

    init { loadApps() }

    private fun loadApps() {
        isLoading.value = true
        loadError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                allApps.value = appRepository.getInstalledApps()
            } catch (e: Throwable) {
                Log.e("AppDrawerVM", "Failed to load apps", e)
                loadError.value = e
            } finally {
                isLoading.value = false
            }
        }
    }

    fun retryLoadApps() = loadApps()

    private val appState = combine(allApps, isLoading, loadError) { apps, loading, error ->
        Triple(apps, loading, error)
    }

    private val prefState = combine(
        preferencesRepository.hiddenApps,
        preferencesRepository.sortOrder,
        preferencesRepository.launchCounts,
    ) { hidden, sort, counts -> Triple(hidden, sort, counts) }

    // ── Search ──────────────────────────────────────────────────────────────
    private val selectedApp   = MutableStateFlow<AppInfo?>(null)
    private val _searchQuery  = MutableStateFlow("")

    @Suppress("OPT_IN_USAGE") // flatMapLatest is stable in coroutines >= 1.6
    private val contactResults = _searchQuery
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else flow<List<SearchResult.Contact>> {
                emit(contactsRepository.search(q))
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val searchState = combine(_searchQuery, contactResults) { q, contacts ->
        Pair(q, contacts)
    }

    // ── Combined UI state ────────────────────────────────────────────────────
    val uiState = combine(appState, prefState, selectedApp, searchState) {
        (apps, loading, error), (hidden, sort, counts), selected, (query, contacts) ->

        if (query.isBlank()) {
            // Normal mode — same behaviour as before search was added.
            val visible = apps.filter { it.packageName !in hidden }
            val sorted = when (sort) {
                SortOrder.ALPHABETICAL -> visible.sortedBy { it.label.lowercase() }
                SortOrder.FREQUENCY    -> visible.sortedByDescending { counts[it.packageName] ?: 0 }
            }
            AppDrawerUiState(
                apps = sorted,
                sortOrder = sort,
                isLoading = loading,
                selectedApp = selected,
                error = error,
                searchQuery = query,
                searchResults = emptyList(),
            )
        } else {
            // Search mode — filter apps + merge contacts + settings.
            val appResults: List<SearchResult> = apps
                .filter { it.packageName !in hidden }
                .filter { it.label.contains(query, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
                .map { SearchResult.App(it) }

            val settingResults: List<SearchResult> = SettingsActions.search(query)
            val contactResults: List<SearchResult>  = contacts

            AppDrawerUiState(
                apps = emptyList(),
                sortOrder = sort,
                isLoading = loading,
                selectedApp = selected,
                error = error,
                searchQuery = query,
                searchResults = appResults + contactResults + settingResults,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = AppDrawerUiState(),
        )

    // ── Actions ──────────────────────────────────────────────────────────────
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun onSearchResultClick(result: SearchResult) {
        viewModelScope.launch {
            clearSearch()
            when (result) {
                is SearchResult.App -> {
                    try { preferencesRepository.recordLaunch(result.info.packageName) }
                    catch (e: Exception) { Log.e("AppDrawerVM", "recordLaunch failed", e) }
                    appRepository.launch(result.info.packageName)
                }
                is SearchResult.Contact -> appRepository.launchContact(result.number)
                is SearchResult.Setting -> appRepository.launchSetting(result.action)
            }
        }
    }

    fun onAppClick(app: AppInfo) {
        viewModelScope.launch {
            clearSearch()
            try { preferencesRepository.recordLaunch(app.packageName) }
            catch (e: Exception) { Log.e("AppDrawerVM", "recordLaunch failed", e) }
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
        private val preferencesRepository: PreferencesRepository,
        private val contactsRepository: ContactsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppDrawerViewModel(appRepository, preferencesRepository, contactsRepository) as T
    }
}
