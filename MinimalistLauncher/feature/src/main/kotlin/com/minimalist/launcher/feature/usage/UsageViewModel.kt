package com.minimalist.launcher.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.core.data.PreferencesRepository
import com.minimalist.launcher.core.data.UsageRepository
import com.minimalist.launcher.core.model.AppUsageStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class UsageViewModel(
    private val usageRepository: UsageRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _todayStats    = MutableStateFlow<List<AppUsageStat>>(emptyList())
    private val _thisWeekStats = MutableStateFlow<List<AppUsageStat>>(emptyList())
    private val _lastWeekStats = MutableStateFlow<List<AppUsageStat>>(emptyList())
    private val _isLoading     = MutableStateFlow(true)

    private data class PrefsBundle(
        val streak: Int,
        val streakDate: String,
        val goal: Int,
        val reportEnabled: Boolean,
        val reportTime: String,
    )

    private val prefsBundle = combine(
        prefs.streakCount,
        prefs.streakLastDate,
        prefs.screenTimeGoalMinutes,
        prefs.reportEnabled,
        prefs.reportTime,
    ) { streak, date, goal, report, time ->
        PrefsBundle(streak, date, goal, report, time)
    }

    val uiState = combine(
        combine(_todayStats, _thisWeekStats, _lastWeekStats, _isLoading) { t, tw, lw, l ->
            listOf<Any>(t, tw, lw, l)
        },
        prefsBundle,
        prefs.frictionMessage,
    ) { stats, p, fm ->
        @Suppress("UNCHECKED_CAST")
        UsageUiState(
            todayStats            = stats[0] as List<AppUsageStat>,
            thisWeekStats         = stats[1] as List<AppUsageStat>,
            lastWeekStats         = stats[2] as List<AppUsageStat>,
            isLoading             = stats[3] as Boolean,
            streakCount           = p.streak,
            streakLastDate        = p.streakDate,
            screenTimeGoalMinutes = p.goal,
            reportEnabled         = p.reportEnabled,
            reportTime            = p.reportTime,
            frictionMessage       = fm,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, UsageUiState())

    init { loadStats() }

    fun refresh() = loadStats()

    private fun loadStats() = viewModelScope.launch(Dispatchers.IO) {
        _isLoading.value = true
        _todayStats.value = runCatching { usageRepository.todayStats() }.getOrDefault(emptyList())

        val today     = LocalDate.now()
        val thisStart = today.minusDays(6).toString()
        val lastStart = today.minusDays(13).toString()
        val lastEnd   = today.minusDays(7).toString()

        _thisWeekStats.value = runCatching { usageRepository.weekStats(thisStart, today.toString()) }.getOrDefault(emptyList())
        _lastWeekStats.value = runCatching { usageRepository.weekStats(lastStart, lastEnd) }.getOrDefault(emptyList())
        _isLoading.value = false
    }

    fun setReportEnabled(enabled: Boolean)  = viewModelScope.launch { prefs.setReportEnabled(enabled) }
    fun setReportTime(time: String)          = viewModelScope.launch { prefs.setReportTime(time) }
    fun setScreenTimeGoal(minutes: Int)      = viewModelScope.launch { prefs.setScreenTimeGoal(minutes) }
    fun setFrictionMessage(msg: String)      = viewModelScope.launch { prefs.setFrictionMessage(msg) }

    class Factory(
        private val usageRepository: UsageRepository,
        private val prefs: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UsageViewModel(usageRepository, prefs) as T
    }
}
