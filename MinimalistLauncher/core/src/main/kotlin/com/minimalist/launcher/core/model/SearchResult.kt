package com.minimalist.launcher.core.model

sealed class SearchResult {
    data class App(val info: AppInfo) : SearchResult()
    data class Contact(val name: String, val number: String) : SearchResult()
    data class Setting(val label: String, val action: String) : SearchResult()
}
