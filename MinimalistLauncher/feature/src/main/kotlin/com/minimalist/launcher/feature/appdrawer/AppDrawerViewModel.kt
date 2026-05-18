package com.minimalist.launcher.feature.appdrawer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.core.data.AppRepository
import com.minimalist.launcher.core.data.CalendarRepository
import com.minimalist.launcher.core.data.ContactsRepository
import com.minimalist.launcher.core.data.EmergencyBypass
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.data.SettingsActions
import com.minimalist.launcher.core.data.UsageRepository
import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.FocusProfile
import com.minimalist.launcher.core.model.FrictionReason
import com.minimalist.launcher.core.model.PinnedItem
import com.minimalist.launcher.core.model.SearchResult
import com.minimalist.launcher.core.model.SortOrder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class AppDrawerViewModel(
    private val appRepository: AppRepository,
    private val preferencesRepository: PreferencesRepository,
    private val contactsRepository: ContactsRepository,
    private val calendarRepository: CalendarRepository,
    private val usageRepository: UsageRepository,
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

    // Includes profile block-list so all filtering (hidden + blocked) is co-located.
    private data class PrefState(
        val hidden: Set<String>,
        val sort: SortOrder,
        val counts: Map<String, Int>,
        val activeProfile: FocusProfile,
        val blockList: Set<String>,
    )

    private val prefState = combine(
        preferencesRepository.hiddenApps,
        preferencesRepository.sortOrder,
        preferencesRepository.launchCounts,
        preferencesRepository.activeProfile,
        preferencesRepository.allProfileConfigs,
    ) { hidden, sort, counts, active, configs ->
        val blockList = if (active == FocusProfile.NONE) emptySet()
                        else configs[active]?.blockList ?: emptySet()
        PrefState(hidden, sort, counts, active, blockList)
    }

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

    private val selectedApp        = MutableStateFlow<AppInfo?>(null)
    private val _editingPinnedSlot = MutableStateFlow<Int?>(null)

    private val selectionState = combine(selectedApp, _editingPinnedSlot) { app, slot ->
        app to slot
    }

    // ── Home screen (Step 4) ─────────────────────────────────────────────────

    @Suppress("OPT_IN_USAGE")
    private val clockState = preferencesRepository.clockFormat.flatMapLatest { format ->
        flow {
            while (true) {
                val now    = LocalDateTime.now()
                val use24h = format == ClockFormat.HOUR_24
                val timeFmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a")
                val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM")
                emit(Triple(now.format(timeFmt), now.format(dateFmt), use24h))
                delay(1_000)
            }
        }.flowOn(Dispatchers.Default)
    }

    private val pinnedItemsFlow = preferencesRepository.pinnedItems()
        .stateIn(viewModelScope, SharingStarted.Lazily, List(5) { null })

    private val homeState = combine(clockState, pinnedItemsFlow) { clock, pins ->
        clock to pins
    }

    // ── Widgets (Step 6) ─────────────────────────────────────────────────────

    private val weatherLine = combine(
        preferencesRepository.weatherEnabled,
        preferencesRepository.weatherCache,
    ) { enabled, cache ->
        if (enabled && !cache.isNullOrBlank()) cache else null
    }

    @Suppress("OPT_IN_USAGE")
    private val calendarLine = preferencesRepository.calendarEnabled.flatMapLatest { enabled ->
        if (!enabled) flowOf<String?>(null)
        else flow<String?> {
            while (true) {
                emit(withContext(Dispatchers.IO) { calendarRepository.nextEvent() })
                delay(60_000)
            }
        }
    }

    private val widgetState = combine(weatherLine, calendarLine) { w, c -> w to c }

    // ── Gestures (Step 7) ────────────────────────────────────────────────────

    private val gestureSettings = preferencesRepository.gestureSettings
        .stateIn(viewModelScope, SharingStarted.Lazily, com.minimalist.launcher.core.model.GestureSettings())

    // ── Friction state (Step 9) ──────────────────────────────────────────────

    private val _frictionApp    = MutableStateFlow<AppInfo?>(null)
    private val _frictionReason = MutableStateFlow<FrictionReason?>(null)
    private val _frictionMsg    = MutableStateFlow("Take a breath. Do you really need this right now?")

    private data class FrictionState(
        val app: AppInfo?,
        val reason: FrictionReason?,
        val msg: String,
    )

    private val frictionState = combine(_frictionApp, _frictionReason, _frictionMsg) { app, reason, msg ->
        FrictionState(app, reason, msg)
    }

    // ── Scratch pad + app lock state (Step 10) ───────────────────────────────

    private val _showScratchPad   = MutableStateFlow(false)
    private val _pendingLockedApp = MutableStateFlow<AppInfo?>(null)

    private data class UtilityState(
        val showScratchPad: Boolean,
        val scratchPadContent: String,
        val pendingLockedApp: AppInfo?,
    )

    private val utilityState = combine(
        _showScratchPad,
        preferencesRepository.scratchPadContent,
        _pendingLockedApp,
    ) { show, content, locked -> UtilityState(show, content, locked) }

    // ── Combined UI state ────────────────────────────────────────────────────

    private val rawUiState = combine(
        appState, prefState, selectionState, _searchQuery, searchState,
    ) { (apps, loading, error), prefs, (selected, editSlot), rawQuery, (debouncedQ, contacts) ->

        val visible = apps.filter { app ->
            app.packageName !in prefs.hidden &&
            (EmergencyBypass.isEmergency(app.packageName) || app.packageName !in prefs.blockList)
        }
        val sorted = when (prefs.sort) {
            SortOrder.ALPHABETICAL -> visible.sortedBy { it.label.lowercase() }
            SortOrder.FREQUENCY    -> visible.sortedByDescending { prefs.counts[it.packageName] ?: 0 }
        }

        if (debouncedQ.isBlank()) {
            AppDrawerUiState(
                apps              = sorted,
                sortOrder         = prefs.sort,
                isLoading         = loading,
                selectedApp       = selected,
                editingPinnedSlot = editSlot,
                error             = error,
                searchQuery       = rawQuery,
                searchResults     = emptyList(),
                activeProfile     = prefs.activeProfile,
            )
        } else {
            val appResults: List<SearchResult> = visible
                .filter { it.label.contains(debouncedQ, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
                .map { SearchResult.App(it) }

            val settingResults: List<SearchResult> = SettingsActions.search(debouncedQ)

            AppDrawerUiState(
                apps              = emptyList(),
                sortOrder         = prefs.sort,
                isLoading         = loading,
                selectedApp       = selected,
                editingPinnedSlot = editSlot,
                error             = error,
                searchQuery       = rawQuery,
                searchResults     = appResults + contacts + settingResults,
                activeProfile     = prefs.activeProfile,
            )
        }
    }.flowOn(Dispatchers.Default)

    val uiState = combine(rawUiState, homeState, widgetState, gestureSettings) { base, (clock, pins), (weather, calendar), gestures ->
        val (time, date, use24h) = clock
        base.copy(
            currentTime     = time,
            currentDate     = date,
            use24h          = use24h,
            pinnedItems     = pins,
            weatherLine     = weather,
            calendarLine    = calendar,
            gestureSettings = gestures,
        )
    }
    .combine(frictionState) { base, fr ->
        base.copy(
            frictionApp     = fr.app,
            frictionReason  = fr.reason,
            frictionMessage = fr.msg,
        )
    }
    .combine(utilityState) { base, util ->
        base.copy(
            showScratchPad   = util.showScratchPad,
            scratchPadContent = util.scratchPadContent,
            pendingLockedApp  = util.pendingLockedApp,
        )
    }
    .stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.Lazily,
        initialValue = AppDrawerUiState(),
    )

    // ── Friction enforcement (Step 9) ─────────────────────────────────────────

    private fun checkAndLaunch(app: AppInfo, onClearSearch: Boolean = true, proceed: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // App lock check (Step 10)
            val locked = preferencesRepository.lockedApps.first()
            if (app.packageName in locked) {
                withContext(Dispatchers.Main) { _pendingLockedApp.value = app }
                return@launch
            }

            val limits  = preferencesRepository.appDailyLimits.first()
            val windows = preferencesRepository.appTimeWindows.first()
            val msg     = preferencesRepository.frictionMessage.first()

            // Daily limit check
            val limitMin = limits[app.packageName] ?: 0
            if (limitMin > 0) {
                val usageMs = usageRepository.queryTodayRawMs()[app.packageName] ?: 0L
                if (usageMs / 60_000 >= limitMin) {
                    withContext(Dispatchers.Main) {
                        _frictionMsg.value    = msg
                        _frictionApp.value    = app
                        _frictionReason.value = FrictionReason.DAILY_LIMIT
                    }
                    return@launch
                }
            }

            // Time window check
            val window = windows[app.packageName]
            if (window != null) {
                val now   = LocalTime.now()
                val fmt   = DateTimeFormatter.ofPattern("HH:mm")
                val start = runCatching { LocalTime.parse(window.first,  fmt) }.getOrDefault(LocalTime.MIDNIGHT)
                val end   = runCatching { LocalTime.parse(window.second, fmt) }.getOrDefault(LocalTime.MAX)
                val inWindow = if (start <= end) now >= start && now <= end
                               else now >= start || now <= end
                if (!inWindow) {
                    withContext(Dispatchers.Main) {
                        _frictionMsg.value    = msg
                        _frictionApp.value    = app
                        _frictionReason.value = FrictionReason.TIME_WINDOW
                    }
                    return@launch
                }
            }

            withContext(Dispatchers.Main) {
                if (onClearSearch) clearSearch()
                proceed()
            }
        }
    }

    fun clearFriction() {
        _frictionApp.value    = null
        _frictionReason.value = null
    }

    fun proceedAfterFriction() {
        val app = _frictionApp.value ?: return
        clearFriction()
        clearSearch()
        viewModelScope.launch {
            try { preferencesRepository.recordLaunch(app.packageName) }
            catch (e: Exception) { Log.e("AppDrawerVM", "recordLaunch failed", e) }
            appRepository.launch(app.packageName)
        }
    }

    // ── App lock actions (Step 10) ────────────────────────────────────────────

    fun cancelLock() { _pendingLockedApp.value = null }

    fun launchAfterBiometric() {
        val app = _pendingLockedApp.value ?: return
        _pendingLockedApp.value = null
        clearSearch()
        viewModelScope.launch {
            try { preferencesRepository.recordLaunch(app.packageName) }
            catch (e: Exception) { Log.e("AppDrawerVM", "recordLaunch failed", e) }
            appRepository.launch(app.packageName)
        }
    }

    // ── Scratch pad (Step 10) ─────────────────────────────────────────────────

    fun openScratchPad()  { _showScratchPad.value = true }
    fun closeScratchPad() { _showScratchPad.value = false }

    fun setScratchPadContent(content: String) {
        viewModelScope.launch { preferencesRepository.setScratchPadContent(content) }
    }

    // ── Search actions ───────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun clearSearch() { _searchQuery.value = "" }

    fun onSearchResultClick(result: SearchResult) {
        when (result) {
            is SearchResult.App -> {
                checkAndLaunch(result.info) {
                    viewModelScope.launch {
                        try { preferencesRepository.recordLaunch(result.info.packageName) }
                        catch (e: Exception) { Log.e("AppDrawerVM", "recordLaunch failed", e) }
                        appRepository.launch(result.info.packageName)
                    }
                }
            }
            is SearchResult.Contact -> {
                clearSearch()
                appRepository.launchContact(result.number)
            }
            is SearchResult.Setting -> {
                clearSearch()
                appRepository.launchSetting(result.action)
            }
        }
    }

    // ── App drawer actions ───────────────────────────────────────────────────

    fun onAppClick(app: AppInfo) {
        checkAndLaunch(app) {
            viewModelScope.launch {
                try { preferencesRepository.recordLaunch(app.packageName) }
                catch (e: Exception) { Log.e("AppDrawerVM", "recordLaunch failed", e) }
                appRepository.launch(app.packageName)
            }
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

    fun onPinnedItemLongPress(slot: Int) { _editingPinnedSlot.value = slot }

    fun dismissPinnedEditor() { _editingPinnedSlot.value = null }

    fun removePinnedItem(slot: Int) {
        viewModelScope.launch {
            preferencesRepository.setPinnedItem(slot, null)
            _editingPinnedSlot.value = null
        }
    }

    fun pinApp(app: AppInfo) {
        viewModelScope.launch {
            val emptySlot = pinnedItemsFlow.value.indexOfFirst { it == null }
            if (emptySlot >= 0) {
                preferencesRepository.setPinnedItem(
                    emptySlot,
                    PinnedItem.App(app.packageName, app.label),
                )
            }
            selectedApp.value = null
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

    fun goHome() {
        clearSearch()
        _navigateToHome.tryEmit(Unit)
    }

    fun launchGoogleSearch() = appRepository.launchGoogleSearch()

    fun launchDialer() = appRepository.launchDialer()

    // ── Focus profiles (Step 8) ──────────────────────────────────────────────

    fun switchProfile(profile: FocusProfile) = viewModelScope.launch {
        preferencesRepository.setActiveProfile(profile)
    }

    // ── Factory ─────────────────────────────────────────────────────────────

    class Factory(
        private val appRepository: AppRepository,
        private val preferencesRepository: PreferencesRepository,
        private val contactsRepository: ContactsRepository,
        private val calendarRepository: CalendarRepository,
        private val usageRepository: UsageRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppDrawerViewModel(
                appRepository, preferencesRepository, contactsRepository,
                calendarRepository, usageRepository,
            ) as T
    }
}
