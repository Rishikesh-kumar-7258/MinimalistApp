package com.minimalist.launcher

import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.minimalist.launcher.feature.appdrawer.AppDrawerScreen
import com.minimalist.launcher.feature.appdrawer.AppDrawerViewModel
import com.minimalist.launcher.ui.theme.MinimalistLauncherTheme

class MainActivity : ComponentActivity() {

    private val requestDefaultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user made their choice; no action needed */ }

    private val viewModel: AppDrawerViewModel by viewModels {
        val app = application as LauncherApplication
        AppDrawerViewModel.Factory(app.appRepository, app.preferencesRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        suppressBackButton()
        promptSetDefaultLauncherIfNeeded()

        setContent {
            MinimalistLauncherTheme {
                AppDrawerScreen(viewModel)
            }
        }
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
            val roleManager = getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                requestDefaultLauncher.launch(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                )
            }
        }
        // On API 26–28 the OS prompts the user naturally the first time Home is pressed
    }
}
