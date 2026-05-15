package com.minimalist.launcher.core.model

sealed class PinnedItem {
    data class App(val packageName: String, val label: String) : PinnedItem()
    data class Contact(val name: String, val number: String) : PinnedItem()
}
