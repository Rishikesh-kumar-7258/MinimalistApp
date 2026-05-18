package com.minimalist.launcher.feature.restrictions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.core.model.AppInfo

@Composable
fun RestrictionsScreen(viewModel: RestrictionsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(48.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = "←",
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
                )
                Text(
                    text  = "restrictions",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text     = "set limits per app — daily limit in minutes, time window in HH:mm",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Column headers ────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = "app",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text  = "limit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text  = "from",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text  = "to",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text  = "lock",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                )
            }
        }

        if (uiState.isLoading) {
            item {
                Text(
                    text     = "loading apps…",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }
        } else {
            items(uiState.apps, key = { it.packageName }) { app ->
                AppRestrictionRow(
                    app         = app,
                    limitMin    = uiState.dailyLimits[app.packageName] ?: 0,
                    window      = uiState.timeWindows[app.packageName],
                    isLocked    = app.packageName in uiState.lockedApps,
                    onSetLimit  = { min -> viewModel.setDailyLimit(app.packageName, min) },
                    onSetWindow = { s, e -> viewModel.setTimeWindow(app.packageName, s, e) },
                    onToggleLock = { viewModel.toggleLock(app.packageName) },
                )
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
private fun AppRestrictionRow(
    app: AppInfo,
    limitMin: Int,
    window: Pair<String, String>?,
    isLocked: Boolean,
    onSetLimit: (Int) -> Unit,
    onSetWindow: (String?, String?) -> Unit,
    onToggleLock: () -> Unit,
) {
    var limitText by remember(limitMin) {
        mutableStateOf(if (limitMin > 0) limitMin.toString() else "")
    }
    var startText by remember(window) { mutableStateOf(window?.first ?: "") }
    var endText   by remember(window) { mutableStateOf(window?.second ?: "") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = app.label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )

        // Daily limit
        SmallInput(
            value       = limitText,
            placeholder = "—",
            width       = 36,
            isNumeric   = true,
            onValueChange = { v ->
                limitText = v.filter { it.isDigit() }.take(4)
                onSetLimit(limitText.toIntOrNull() ?: 0)
            },
        )

        // Time window start
        SmallInput(
            value       = startText,
            placeholder = "—",
            width       = 44,
            onValueChange = { v ->
                startText = v
                val e = endText.takeIf { it.matches(Regex("^\\d{1,2}:\\d{2}$")) }
                if (v.matches(Regex("^\\d{1,2}:\\d{2}$"))) onSetWindow(v, e ?: v)
                else if (v.isBlank()) onSetWindow(null, null)
            },
        )

        // Time window end
        SmallInput(
            value       = endText,
            placeholder = "—",
            width       = 44,
            onValueChange = { v ->
                endText = v
                val s = startText.takeIf { it.matches(Regex("^\\d{1,2}:\\d{2}$")) }
                if (v.matches(Regex("^\\d{1,2}:\\d{2}$"))) onSetWindow(s ?: v, v)
                else if (v.isBlank()) onSetWindow(null, null)
            },
        )

        // Lock toggle
        Text(
            text  = if (isLocked) "on" else "—",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLocked)
                MaterialTheme.colorScheme.onBackground
            else
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
            modifier = Modifier
                .clickable { onToggleLock() }
                .padding(4.dp),
        )
    }
}

@Composable
private fun SmallInput(
    value: String,
    placeholder: String,
    width: Int,
    isNumeric: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value         = value,
        onValueChange = onValueChange,
        textStyle     = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
        cursorBrush   = SolidColor(MaterialTheme.colorScheme.onBackground),
        singleLine    = true,
        modifier      = Modifier.padding(end = 8.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text  = placeholder,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                )
            }
            inner()
        },
    )
}
