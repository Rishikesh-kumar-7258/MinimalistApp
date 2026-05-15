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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.SearchResult
import com.minimalist.launcher.core.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(viewModel: AppDrawerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    val listState    = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Pull-down anywhere on the list to focus the search bar.
    val nestedScrollConnection = remember(focusRequester) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // available.y > 0 = unconsumed downward drag (list already at top)
                if (available.y > 8f) focusRequester.requestFocus()
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection),
    ) {
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = viewModel::onSearchQueryChange,
            onClear = {
                viewModel.clearSearch()
                keyboardController?.hide()
            },
            focusRequester = focusRequester,
        )

        // Sort toggle only visible when not searching.
        if (uiState.searchQuery.isEmpty()) {
            SortToggleRow(
                current = uiState.sortOrder,
                onToggle = { viewModel.setSortOrder(it) },
            )
        }

        when {
            uiState.isLoading -> CenteredHint("loading...")

            uiState.error != null -> ErrorState(onRetry = viewModel::retryLoadApps)

            uiState.searchQuery.isNotEmpty() -> SearchResultsList(
                modifier   = Modifier.weight(1f),
                results    = uiState.searchResults,
                listState  = listState,
                onResultClick = { result ->
                    keyboardController?.hide()
                    viewModel.onSearchResultClick(result)
                },
                onAppLongPress = { app ->
                    viewModel.onAppLongPress(app)
                },
            )

            uiState.apps.isEmpty() -> CenteredHint("no apps found")

            else -> AppList(
                modifier      = Modifier.weight(1f),
                apps          = uiState.apps,
                listState     = listState,
                onAppClick    = viewModel::onAppClick,
                onAppLongPress = viewModel::onAppLongPress,
            )
        }
    }

    if (uiState.selectedApp != null) {
        AppOptionsSheet(
            app      = uiState.selectedApp!!,
            onHide   = { viewModel.hideApp(it) },
            onDismiss = viewModel::dismissBottomSheet,
        )
    }
}

// ── Search bar ───────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect    = false,
                keyboardType   = KeyboardType.Text,
                imeAction      = ImeAction.Search,
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text  = "search apps, contacts, settings…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (query.isNotEmpty()) {
            Text(
                text  = "×",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

// ── Search results list ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsList(
    modifier: Modifier,
    results: List<SearchResult>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onResultClick: (SearchResult) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
) {
    if (results.isEmpty()) {
        CenteredHint("no results")
        return
    }

    LazyColumn(modifier = modifier, state = listState) {
        items(results, key = { result ->
            when (result) {
                is SearchResult.App     -> "app_${result.info.packageName}"
                is SearchResult.Contact -> "contact_${result.number}"
                is SearchResult.Setting -> "setting_${result.label}"
            }
        }) { result ->
            SearchResultItem(
                result        = result,
                onClick       = { onResultClick(result) },
                onLongPress   = { if (result is SearchResult.App) onAppLongPress(result.info) },
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val (label, hint) = when (result) {
        is SearchResult.App     -> result.info.label to null
        is SearchResult.Contact -> result.name        to "call"
        is SearchResult.Setting -> result.label       to "settings"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 32.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(
                text  = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
            )
        }
    }
}

// ── Normal app list ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(
    modifier: Modifier,
    apps: List<AppInfo>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onAppClick: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
) {
    LazyColumn(modifier = modifier, state = listState) {
        items(apps, key = { it.packageName }) { app ->
            Text(
                text  = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick     = { onAppClick(app) },
                        onLongClick = { onAppLongPress(app) },
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp),
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun CenteredHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "failed to load apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(
                    text  = "retry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SortToggleRow(current: SortOrder, onToggle: (SortOrder) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        SortLabel("a-z",  current == SortOrder.ALPHABETICAL) { onToggle(SortOrder.ALPHABETICAL) }
        Text(
            text  = "  ·  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        )
        SortLabel("freq", current == SortOrder.FREQUENCY)    { onToggle(SortOrder.FREQUENCY) }
    }
}

@Composable
private fun SortLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (active)
            MaterialTheme.colorScheme.onBackground
        else
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
    )
}

// ── Bottom sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppOptionsSheet(
    app: AppInfo,
    onHide: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text  = app.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))
            SheetAction("pin")      { /* Step 4 */ }
            SheetAction("hide")     { onHide(app) }
            SheetAction("group")    { /* future */ }
            SheetAction("restrict") { /* Step 9 */ }
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}
