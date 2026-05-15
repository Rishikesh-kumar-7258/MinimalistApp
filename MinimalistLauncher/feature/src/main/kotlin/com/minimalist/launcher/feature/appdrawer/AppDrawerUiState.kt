package com.minimalist.launcher.feature.appdrawer

import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.SearchResult
import com.minimalist.launcher.core.model.SortOrder

data class AppDrawerUiState(
    val apps: List<AppInfo> = emptyList(),
    val sortOrder: SortOrder = SortOrder.ALPHABETICAL,
    val isLoading: Boolean = true,
    val selectedApp: AppInfo? = null,
    val error: Throwable? = null,
    // Search
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
)
