package com.minimalist.launcher

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import com.minimalist.launcher.core.data.AppRepository
import com.minimalist.launcher.core.data.ContactsRepository
import com.minimalist.launcher.core.data.PreferencesRepository

private val Application.dataStore by preferencesDataStore(name = "launcher_prefs")

class LauncherApplication : Application() {
    val appRepository by lazy { AppRepository(this) }
    val preferencesRepository by lazy { PreferencesRepository(dataStore) }
    val contactsRepository by lazy { ContactsRepository(this) }
}
