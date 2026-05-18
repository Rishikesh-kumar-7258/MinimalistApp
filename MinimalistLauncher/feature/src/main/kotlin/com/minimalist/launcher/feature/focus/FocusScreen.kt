package com.minimalist.launcher.feature.focus

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
import com.minimalist.launcher.core.model.FocusProfile
import com.minimalist.launcher.core.model.ProfileConfig

@Composable
fun FocusScreen(viewModel: FocusViewModel, onBack: () -> Unit) {
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
                    text  = "focus profiles",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Active profile quick-switch ────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                Text(
                    text  = "active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FocusProfile.entries.forEach { profile ->
                        val label = if (profile == FocusProfile.NONE) "none"
                                    else profile.name.lowercase()
                        val isActive = profile == uiState.activeProfile
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isActive)
                                MaterialTheme.colorScheme.onBackground
                            else
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier
                                .clickable { viewModel.setActiveProfile(profile) }
                                .padding(4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                Spacer(Modifier.height(24.dp))
                Text(
                    text  = "configure",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Per-profile expandable sections ───────────────────────────────────
        FocusProfile.entries.filter { it != FocusProfile.NONE }.forEach { profile ->
            val config = uiState.configs[profile] ?: ProfileConfig()
            val isExpanded = uiState.expandedProfile == profile

            item(key = profile.name) {
                ProfileHeader(
                    profile    = profile,
                    isExpanded = isExpanded,
                    isActive   = profile == uiState.activeProfile,
                    onToggle   = { viewModel.toggleExpanded(profile) },
                )
            }

            if (isExpanded) {
                item(key = "${profile.name}_schedule") {
                    ScheduleSection(
                        config            = config,
                        onToggleSchedule  = { viewModel.setScheduleEnabled(profile, it) },
                        onStartTimeChange = { viewModel.setStartTime(profile, it) },
                        onEndTimeChange   = { viewModel.setEndTime(profile, it) },
                    )
                }

                if (uiState.isLoadingApps) {
                    item(key = "${profile.name}_loading") {
                        Text(
                            text  = "loading apps…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    item(key = "${profile.name}_apps_header") {
                        Text(
                            text  = "blocked apps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(start = 48.dp, end = 32.dp, top = 12.dp, bottom = 6.dp),
                        )
                    }
                    items(uiState.allApps, key = { "${profile.name}_${it.packageName}" }) { app ->
                        val isBlocked = app.packageName in config.blockList
                        AppBlockRow(
                            label     = app.label,
                            isBlocked = isBlocked,
                            onToggle  = { viewModel.toggleBlockedApp(profile, app.packageName) },
                        )
                    }
                }

                item(key = "${profile.name}_footer") { Spacer(Modifier.height(8.dp)) }
            }

            item(key = "${profile.name}_divider") {
                HorizontalDivider(
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile header row (expand / collapse)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    profile: FocusProfile,
    isExpanded: Boolean,
    isActive: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 32.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = profile.name.lowercase(),
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (isActive) {
            Text(
                text  = "active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Text(
            text  = if (isExpanded) "▲" else "▼",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Schedule section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScheduleSection(
    config: ProfileConfig,
    onToggleSchedule: (Boolean) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 48.dp, end = 32.dp, bottom = 8.dp)) {
        Text(
            text  = "schedule",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("on" to true, "off" to false).forEach { (label, value) ->
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (config.scheduleEnabled == value)
                        MaterialTheme.colorScheme.onBackground
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.clickable { onToggleSchedule(value) }.padding(4.dp),
                )
            }
        }
        if (config.scheduleEnabled) {
            Spacer(Modifier.height(8.dp))
            TimeInputRow("from", config.startTime, onStartTimeChange)
            Spacer(Modifier.height(4.dp))
            TimeInputRow("to",   config.endTime,   onEndTimeChange)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TimeInputRow(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.padding(end = 12.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = { new ->
                text = new
                // Accept only HH:mm format to keep DataStore clean
                if (new.matches(Regex("^\\d{1,2}:\\d{2}$"))) onChange(new)
            },
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            singleLine  = true,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// App block toggle row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppBlockRow(label: String, isBlocked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = if (isBlocked)
                MaterialTheme.colorScheme.onBackground
            else
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = if (isBlocked) "block" else "—",
            style = MaterialTheme.typography.labelSmall,
            color = if (isBlocked)
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
            modifier = Modifier.clickable { onToggle() }.padding(4.dp),
        )
    }
}
