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
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.PinnedItem
import com.minimalist.launcher.core.model.SearchResult
import com.minimalist.launcher.core.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AppDrawerViewModel(
    private val appRepository: AppRepository,
    private val preferencesRepository: PreferencesRepository,
    private val contactsRepository: ContactsRepository,
) : ViewModel() {

    // ── App list (Step 2) ────────────────────────────────────────────────────

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

    // ── Search (Step 3) ──────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")

    @Suppress("OPT_IN_USAGE")
    private val debouncedQuery = _searchQuery
        .debounce(150)
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    private val contactsVersion = MutableStateFlow(0)

    fun onContactsPermissionGranted() { contactsVersion.value++ }

    @Suppress("OPT_IN_USAGE")
    private val contactResults = combine(debouncedQuery, contactsVersion) { q, _ -> q }
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList())
            else flow<List<SearchResult.Contact>> {
                emit(contactsRepository.search(q))
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val searchState = combine(debouncedQuery, contactResults) { q, contacts ->
        q to contacts
    }

    // ── Selection state (app sheet + pinned-slot sheet) ──────────────────────

    private val selectedApp       = MutableStateFlow<AppInfo?>(null)
    private val _editingPinnedSlot = MutableStateFlow<Int?>(null)

    private val selectionState = combine(selectedApp, _editingPinnedSlot) { app, slot ->
        app to slot
    }

    // ── Home screen (Step 4) ─────────────────────────────────────────────────

    // Clock: re-emits every second; flatMapLatest restarts the inner loop when
    // the clock format preference changes so the format takes effect immediately.
    @Suppress("OPT_IN_USAGE")
    private val clockState = preferencesRepository.clockFormat.flatMapLatest { format ->
        flow {
            while (true) {
                val now      = LocalDateTime.now()
                val use24h   = format == ClockFormat.HOUR_24
                val timeFmt  = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a")
                val dateFmt  = DateTimeFormatter.ofPattern("EEEE, d MMMM")
                emit(Triple(now.format(timeFmt), now.format(dateFmt), use24h))
                delay(1_000)
            }
        }.flowOn(Dispatchers.Default)
    }

    // Pinned items cached as a StateFlow so pinApp() can read the current value
    // without suspending.
    private val pinnedItemsFlow = preferencesRepository.pinnedItems()
        .stateIn(viewModelScope, SharingStarted.Lazily, List(5) { null })

    // Clock + pins combined into a single home-state emission. Kept separate
    // from the app-drawer state so the 1-second clock tick does NOT re-trigger
    // app list filtering/sorting.
    private val homeState = combine(clockState, pinnedItemsFlow) { clock, pins ->
        clock to pins   // Pair<Triple<String,String,Boolean>, List<PinnedItem?>>
    }

    // ── Combined UI state ────────────────────────────────────────────────────

    // Inner combine (5 flows) handles app list + search. selectedApp and
    // editingPinnedSlot share the selectionState slot to keep the count at 5.
    private val rawUiState = combine(
        appState, prefState, selectionState, _searchQuery, searchState,
    ) { (apps, loading, error), (hidden, sort, counts), (selected, editSlot), rawQuery, (debouncedQ, contacts) ->

        val visible = apps.filter { it.packageName !in hidden }
        val sorted  = when (sort) {
            SortOrder.ALPHABETICAL -> visible.sortedBy { it.label.lowercase() }
            SortOrder.FREQUENCY    -> visible.sortedByDescending { counts[it.packageName] ?: 0 }
        }

        if (debouncedQ.isBlank()) {
            AppDrawerUiState(
                apps              = sorted,
                sortOrder         = sort,
                isLoading         = loading,
                selectedApp       = selected,
                editingPinnedSlot = editSlot,
                error             = error,
                searchQuery       = rawQuery,
                searchResults     = emptyList(),
            )
        } else {
            val appResults: List<SearchResult> = visible
                .filter { it.label.contains(debouncedQ, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
                .map { SearchResult.App(it) }

            val settingResults: List<SearchResult> = SettingsActions.search(debouncedQ)

            AppDrawerUiState(
                apps              = emptyList(),
                sortOrder         = sort,
                isLoading         = loading,
                selectedApp       = selected,
                editingPinnedSlot = editSlot,
                error             = error,
                searchQuery       = rawQuery,
                searchResults     = appResults + contacts + settingResults,
            )
        }
    }.flowOn(Dispatchers.Default)

    // Outer combine layers home state on top. The clock tick produces a new
    // homeState emission every second; combine uses the cached rawUiState value
    // so app filtering is NOT re-run on every tick.
    val uiState = combine(rawUiState, homeState) { base, (clock, pins) ->
        val (time, date, use24h) = clock
        base.copy(
            currentTime  = time,
            currentDate  = date,
            use24h       = use24h,
            pinnedItems  = pins,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.Lazily,
        initialValue = AppDrawerUiState(),
    )

    // ── Search actions ───────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun clearSearch() { _searchQuery.value = "" }

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

    // ── App drawer actions ───────────────────────────────────────────────────

    fun onAppClick(app: AppInfo) {
        viewModelScope.launch {
            clearSearch()
            try { preferencesRepository.recordLaunch(app.packageName) }
            catch (e: Exception) { Log.e("AppDrawerVM", "recordLaunch failed", e) }
            appRepository.launch(app.packageName)
        }
    }

    fun onAppLongPress(app: AppInfo) { selectedApp.value = app }

    fun hideApp(app: AppInfo) {
        viewModelScope.launch {
            preferencesRepository.setHidden(app.packageName, true)
            selectedApp.value = null
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { preferencesRepository.setSortOrder(order) }
    }

    fun dismissBottomSheet() { selectedApp.value = null }

    // ── Pinned shortcuts actions (Step 4) ────────────────────────────────────

    fun onPinnedItemClick(item: PinnedItem) {
        when (item) {
            is PinnedItem.App     -> appRepository.launch(item.packageName)
            is PinnedItem.Contact -> appRepository.launchContact(item.number)
        }
    }

    fun onPinnedItemLongPress(slot: Int) {
        _editingPinnedSlot.value = slot
    }

    fun dismissPinnedEditor() {
        _editingPinnedSlot.value = null
    }

    fun removePinnedItem(slot: Int) {
        viewModelScope.launch {
            preferencesRepository.setPinnedItem(slot, null)
            _editingPinnedSlot.value = null
        }
    }

    // Pins the app into the first empty slot. Called from the app options sheet.
    fun pinApp(app: AppInfo) {
        viewModelScope.launch {
            val emptySlot = pinnedItemsFlow.value.indexOfFirst { it == null }
            if (emptySlot >= 0) {
                preferencesRepository.setPinnedItem(
                    emptySlot,
                    PinnedItem.App(app.packageName, app.label),
                )
            }
            selectedApp.value = null   // dismiss the app options sheet
        }
    }

    // ── Clock format action (Step 4) ─────────────────────────────────────────

    fun toggleClockFormat() {
        viewModelScope.launch {
            val next = if (uiState.value.use24h) ClockFormat.HOUR_12 else ClockFormat.HOUR_24
            preferencesRepository.setClockFormat(next)
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private val _navigateToHome = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToHome: SharedFlow<Unit> = _navigateToHome.asSharedFlow()

    // Called from onNewIntent (Home button pressed) — clear search AND go home.
    fun goHome() {
        clearSearch()
        _navigateToHome.tryEmit(Unit)
    }

    fun launchGoogleSearch() = appRepository.launchGoogleSearch()

    // ── Factory ─────────────────────────────────────────────────────────────

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
