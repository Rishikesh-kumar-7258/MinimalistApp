package com.minimalist.launcher.feature.usage

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
import com.minimalist.launcher.core.model.AppUsageStat

@Composable
fun UsageScreen(viewModel: UsageViewModel, onBack: () -> Unit) {
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
                    text  = "usage",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Streak ────────────────────────────────────────────────────────────
        if (uiState.screenTimeGoalMinutes > 0) {
            item {
                Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                    Text(
                        text  = "streak",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "${uiState.streakCount} day${if (uiState.streakCount != 1) "s" else ""}  under ${uiState.screenTimeGoalMinutes}m goal",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // ── Today ─────────────────────────────────────────────────────────────
        item {
            Text(
                text     = "today",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        if (uiState.isLoading) {
            item {
                Text(
                    text     = "loading…",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
        } else if (uiState.todayStats.isEmpty()) {
            item {
                Text(
                    text     = "no data — grant usage stats permission in settings",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
        } else {
            items(uiState.todayStats.take(10)) { stat ->
                UsageStatRow(stat)
            }
        }

        // ── This week vs last week ────────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text     = "this week vs last week",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        val weekMap: Map<String, Long> = uiState.thisWeekStats.associate { it.packageName to it.usageMs }
        val lastMap: Map<String, Long> = uiState.lastWeekStats.associate { it.packageName to it.usageMs }
        val weekApps = (uiState.thisWeekStats + uiState.lastWeekStats)
            .distinctBy { it.packageName }
            .filter { (weekMap[it.packageName] ?: 0L) > 0L || (lastMap[it.packageName] ?: 0L) > 0L }
            .sortedByDescending { weekMap[it.packageName] ?: 0L }
            .take(10)

        if (weekApps.isEmpty()) {
            item {
                Text(
                    text     = "no weekly data available",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
        } else {
            items(weekApps) { stat ->
                WeeklyTrendRow(
                    label    = stat.appLabel,
                    thisWeek = weekMap[stat.packageName] ?: 0L,
                    lastWeek = lastMap[stat.packageName] ?: 0L,
                )
            }
        }

        // ── Report settings ───────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text     = "daily report",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = "notification",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("on" to true, "off" to false).forEach { (label, v) ->
                        Text(
                            text  = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.reportEnabled == v)
                                MaterialTheme.colorScheme.onBackground
                            else
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier
                                .clickable { viewModel.setReportEnabled(v) }
                                .padding(4.dp),
                        )
                    }
                }
            }
        }

        if (uiState.reportEnabled) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text     = "at",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TimeInput(
                        value    = uiState.reportTime,
                        onChange = viewModel::setReportTime,
                    )
                }
            }
        }

        // ── Screen time goal ──────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = "daily goal (min)",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                var goalText by remember(uiState.screenTimeGoalMinutes) {
                    mutableStateOf(if (uiState.screenTimeGoalMinutes > 0) uiState.screenTimeGoalMinutes.toString() else "")
                }
                BasicTextField(
                    value = goalText,
                    onValueChange = { v ->
                        goalText = v.filter { it.isDigit() }.take(4)
                        goalText.toIntOrNull()?.let { viewModel.setScreenTimeGoal(it) }
                    },
                    textStyle = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    singleLine  = true,
                    decorationBox = { inner ->
                        if (goalText.isEmpty()) {
                            Text(
                                text  = "0 = off",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            )
                        }
                        inner()
                    },
                )
            }
        }

        // ── Friction message ──────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text     = "friction message",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(8.dp))
            var msg by remember(uiState.frictionMessage) { mutableStateOf(uiState.frictionMessage) }
            BasicTextField(
                value = msg,
                onValueChange = { msg = it; viewModel.setFrictionMessage(it) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun UsageStatRow(stat: AppUsageStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = stat.appLabel,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = stat.usageMs.toTimeStr(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun WeeklyTrendRow(label: String, thisWeek: Long, lastWeek: Long) {
    val delta = thisWeek - lastWeek
    val deltaStr = when {
        delta > 0 -> "+${delta.toTimeStr()}"
        delta < 0 -> "-${(-delta).toTimeStr()}"
        else      -> "="
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = "${thisWeek.toTimeStr()}  $deltaStr",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun TimeInput(value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = { new ->
            text = new
            if (new.matches(Regex("^\\d{1,2}:\\d{2}$"))) onChange(new)
        },
        textStyle = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
        singleLine  = true,
    )
}

private fun Long.toTimeStr(): String {
    val m = this / 60_000
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}
