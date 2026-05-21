package com.minimalist.launcher.feature.appdrawer

import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.FocusProfile
import com.minimalist.launcher.core.model.FrictionReason
import com.minimalist.launcher.core.model.GestureSettings
import com.minimalist.launcher.core.model.PinnedItem
import com.minimalist.launcher.core.model.SearchResult
import com.minimalist.launcher.core.model.SortOrder

data class AppDrawerUiState(
    // ── App drawer (Steps 2–3) ───────────────────────────────────────────────
    val apps: List<AppInfo> = emptyList(),
    val sortOrder: SortOrder = SortOrder.ALPHABETICAL,
    val isLoading: Boolean = true,
    val selectedApp: AppInfo? = null,
    val error: Throwable? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    // ── Home screen (Step 4) ─────────────────────────────────────────────────
    val currentTime: String = "",
    val currentDate: String = "",
    val use24h: Boolean = false,
    val pinnedItems: List<PinnedItem?> = emptyList(),
    val pinnedSlotCount: Int = 3,
    val editingPinnedSlot: Int? = null,
    // ── Widgets (Step 6) ─────────────────────────────────────────────────────
    val weatherLine: String? = null,
    val calendarLine: String? = null,
    // ── Gestures (Step 7) ────────────────────────────────────────────────────
    val gestureSettings: GestureSettings = GestureSettings(),
    // ── Focus profiles (Step 8) ──────────────────────────────────────────────
    val activeProfile: FocusProfile = FocusProfile.NONE,
    // ── Friction / restrictions (Step 9) ─────────────────────────────────────
    val frictionApp: AppInfo? = null,
    val frictionReason: FrictionReason? = null,
    val frictionMessage: String = "Take a breath. Do you really need this right now?",
    // ── Scratch pad + app lock (Step 10) ─────────────────────────────────────
    val showScratchPad: Boolean = false,
    val scratchPadContent: String = "",
    val pendingLockedApp: AppInfo? = null,
)
