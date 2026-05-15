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
    private val selectedApp  = MutableStateFlow<AppInfo?>(null)
    private val _searchQuery = MutableStateFlow("")

    // Single shared debounced flow — both contact queries and app filtering
    // use this so all results appear together 150 ms after the user stops typing.
    @Suppress("OPT_IN_USAGE")
    private val debouncedQuery = _searchQuery
        .debounce(150)
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    // Incremented when READ_CONTACTS is granted at runtime so the contacts
    // query re-runs even though debouncedQuery hasn't changed.
    // (StateFlow won't re-emit the same String, so a plain nudge is needed.)
    private val contactsVersion = MutableStateFlow(0)

    fun onContactsPermissionGranted() {
        contactsVersion.value++
    }

    @Suppress("OPT_IN_USAGE")
    private val contactResults = combine(debouncedQuery, contactsVersion) { q, _ -> q }
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else flow<List<SearchResult.Contact>> {
                emit(contactsRepository.search(q))
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Pairs the debounced query with debounced contact results.
    private val searchState = combine(debouncedQuery, contactResults) { q, contacts ->
        Pair(q, contacts)
    }

    // ── Combined UI state ────────────────────────────────────────────────────
    // Five flows: appState, prefState, selectedApp, raw query (for display),
    // searchState (debounced, for filtering).
    val uiState = combine(appState, prefState, selectedApp, _searchQuery, searchState) {
        (apps, loading, error), (hidden, sort, counts), selected, rawQuery, (debouncedQ, contacts) ->

        // Always compute the sorted full list so it can be shown in both
        // normal mode and the debounce window (while the user is still typing).
        val visible = apps.filter { it.packageName !in hidden }
        val sorted = when (sort) {
            SortOrder.ALPHABETICAL -> visible.sortedBy { it.label.lowercase() }
            SortOrder.FREQUENCY    -> visible.sortedByDescending { counts[it.packageName] ?: 0 }
        }

        if (debouncedQ.isBlank()) {
            // Normal mode OR inside the 150 ms debounce window.
            // Either way show the full sorted app list so there is no jarring
            // "no results" flash while the user is mid-keystroke.
            AppDrawerUiState(
                apps = sorted,
                sortOrder = sort,
                isLoading = loading,
                selectedApp = selected,
                error = error,
                searchQuery = rawQuery,   // raw — keeps the search bar text current
                searchResults = emptyList(),
            )
        } else {
            // Debounce has settled — filter apps, contacts, settings together.
            val appResults: List<SearchResult> = visible
                .filter { it.label.contains(debouncedQ, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
                .map { SearchResult.App(it) }

            val settingResults: List<SearchResult> = SettingsActions.search(debouncedQ)

            AppDrawerUiState(
                apps = emptyList(),
                sortOrder = sort,
                isLoading = loading,
                selectedApp = selected,
                error = error,
                searchQuery = rawQuery,
                searchResults = appResults + contacts + settingResults,
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
