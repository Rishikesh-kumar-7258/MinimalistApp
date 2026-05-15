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
import androidx.lifecycle.lifecycleScope
import com.minimalist.launcher.feature.appdrawer.AppDrawerScreen
import com.minimalist.launcher.feature.appdrawer.AppDrawerViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        suppressBackButton()

        // Draw first frame before any Binder calls — prevents ANR on slow system_server.
        setContent {
            MinimalistLauncherTheme {
                AppDrawerScreen(viewModel)
            }
        }

        promptSetDefaultLauncherIfNeeded()
    }

    // Called when the user presses Home while this IS the home screen (singleTask).
    // Clear the search so the drawer is clean next time.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.clearSearch()
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
