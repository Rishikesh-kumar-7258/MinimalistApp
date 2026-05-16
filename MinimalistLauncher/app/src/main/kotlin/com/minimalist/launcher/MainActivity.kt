package com.minimalist.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.minimalist.launcher.core.model.AppearanceSettings
import com.minimalist.launcher.feature.appdrawer.AppDrawerScreen
import com.minimalist.launcher.feature.appdrawer.AppDrawerViewModel
import com.minimalist.launcher.feature.settings.SettingsScreen
import com.minimalist.launcher.feature.settings.SettingsViewModel
import com.minimalist.launcher.ui.theme.MinimalistLauncherTheme
import com.minimalist.launcher.worker.WeatherWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestDefaultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user made their choice; no action needed */ }

    private val viewModel: AppDrawerViewModel by viewModels {
        val app = application as LauncherApplication
        AppDrawerViewModel.Factory(
            app.appRepository,
            app.preferencesRepository,
            app.contactsRepository,
            app.calendarRepository,
        )
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory((application as LauncherApplication).preferencesRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        suppressBackButton()

        val preferencesRepository = (application as LauncherApplication).preferencesRepository

        setContent {
            val appearance by produceState(initialValue = AppearanceSettings()) {
                preferencesRepository.appearanceSettings.collect { value = it }
            }

            var showSettings by remember { mutableStateOf(false) }

            // Listen for immediate weather-fetch requests from the Settings screen.
            LaunchedEffect(Unit) {
                settingsViewModel.fetchWeatherNow.collect {
                    val request = OneTimeWorkRequestBuilder<WeatherWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                    WorkManager.getInstance(applicationContext).enqueue(request)
                }
            }

            MinimalistLauncherTheme(appearance = appearance) {
                if (showSettings) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack    = { showSettings = false },
                    )
                } else {
                    AppDrawerScreen(
                        viewModel      = viewModel,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }

        promptSetDefaultLauncherIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.goHome()
    }

    private fun suppressBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Home screen swallows back presses — intentional per spec
            }
        })
    }

    private fun promptSetDefaultLauncherIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lifecycleScope.launch {
                val shouldPrompt = withContext(Dispatchers.IO) {
                    val roleManager = getSystemService(RoleManager::class.java)
                    roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
                if (shouldPrompt) {
                    val roleManager = getSystemService(RoleManager::class.java) ?: return@launch
                    requestDefaultLauncher.launch(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    )
                }
            }
        }
    }
}
