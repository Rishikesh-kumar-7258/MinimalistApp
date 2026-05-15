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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.minimalist.launcher.core.model.AppearanceSettings
import com.minimalist.launcher.feature.appdrawer.AppDrawerScreen
import com.minimalist.launcher.feature.appdrawer.AppDrawerViewModel
import com.minimalist.launcher.feature.settings.SettingsScreen
import com.minimalist.launcher.feature.settings.SettingsViewModel
import com.minimalist.launcher.ui.theme.MinimalistLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestDefaultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user made their choice; no action needed */ }

    private val viewModel: AppDrawerViewModel by viewModels {
        val app = application as LauncherApplication
        AppDrawerViewModel.Factory(app.appRepository, app.preferencesRepository, app.contactsRepository)
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
            // Observe appearance settings — recomposes when any setting changes.
            val appearance by produceState(initialValue = AppearanceSettings()) {
                preferencesRepository.appearanceSettings.collect { value = it }
            }

            var showSettings by remember { mutableStateOf(false) }

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

    // Called when the user presses Home while this IS the home screen (singleTask).
    // Clear the search so the drawer is clean next time.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.goHome()   // clear search + signal pager to return to home page
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
            // Run Binder calls on IO so the main thread stays free.
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
        // On API 26–28 the OS prompts naturally on the first Home press
    }
}
