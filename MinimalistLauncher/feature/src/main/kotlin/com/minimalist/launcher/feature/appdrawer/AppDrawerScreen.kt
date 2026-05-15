package com.minimalist.launcher.feature.appdrawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(viewModel: AppDrawerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        SortToggleRow(
            current = uiState.sortOrder,
            onToggle = { viewModel.setSortOrder(it) }
        )
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
            uiState.error != null -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "failed to load apps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.retryLoadApps() }) {
                        Text(
                            text = "retry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            uiState.apps.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "no apps found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
            else -> AppList(
                modifier = Modifier.weight(1f),
                apps = uiState.apps,
                onAppClick = viewModel::onAppClick,
                onAppLongPress = viewModel::onAppLongPress
            )
        }
    }

    if (uiState.selectedApp != null) {
        AppOptionsSheet(
            app = uiState.selectedApp!!,
            onHide = { viewModel.hideApp(it) },
            onDismiss = viewModel::dismissBottomSheet
        )
    }
}

@Composable
private fun SortToggleRow(current: SortOrder, onToggle: (SortOrder) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))
        SortLabel(
            text = "a-z",
            active = current == SortOrder.ALPHABETICAL,
            onClick = { onToggle(SortOrder.ALPHABETICAL) }
        )
        Text(
            text = "  ·  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )
        SortLabel(
            text = "freq",
            active = current == SortOrder.FREQUENCY,
            onClick = { onToggle(SortOrder.FREQUENCY) }
        )
    }
}

@Composable
private fun SortLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (active)
            MaterialTheme.colorScheme.onBackground
        else
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(
    modifier: Modifier = Modifier,
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(apps, key = { it.packageName }) { app ->
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongPress(app) }
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp)
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppOptionsSheet(
    app: AppInfo,
    onHide: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))
            SheetAction(label = "pin") { /* Step 4 */ }
            SheetAction(label = "hide") { onHide(app) }
            SheetAction(label = "group") { /* future */ }
            SheetAction(label = "restrict") { /* Step 9 */ }
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
    }
}
