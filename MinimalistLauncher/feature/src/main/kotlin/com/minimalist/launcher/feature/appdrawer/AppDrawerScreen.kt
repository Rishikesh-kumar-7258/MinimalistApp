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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.PinnedItem
import com.minimalist.launcher.core.model.SearchResult
import com.minimalist.launcher.core.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(viewModel: AppDrawerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val focusManager   = LocalFocusManager.current
    val listState      = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Track whether the search bar itself has input focus so BackHandler can
    // fire even when the user opened the keyboard without typing anything.
    var isSearchBarFocused by remember { mutableStateOf(false) }

    // ── Focus management ──────────────────────────────────────────────────────

    // 1. Back button: when the search bar is active (focused or has text) the
    //    back press should dismiss it, not be swallowed by the home-screen sink.
    //    BackHandler is added AFTER MainActivity's suppress callback (LIFO order),
    //    so it takes priority whenever it is enabled.
    BackHandler(enabled = isSearchBarFocused || uiState.searchQuery.isNotEmpty()) {
        viewModel.clearSearch()
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // 2. Scroll: when the user starts scrolling the app list while the keyboard
    //    is open, dismiss it so the list has full screen space.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // 3. External clear (e.g. Home press → onNewIntent → clearSearch()): the
    //    query becomes empty from outside the screen, so clean up focus here.
    //    Using isEmpty() as the key means this only fires on the empty↔non-empty
    //    transition, not on every individual keystroke.
    LaunchedEffect(uiState.searchQuery.isEmpty()) {
        if (uiState.searchQuery.isEmpty()) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // ── Runtime permission for contacts ──────────────────────────────────────
    // READ_CONTACTS is a dangerous permission — declaring it in the manifest is
    // not enough. We request it lazily the first time the user activates search.
    val requestContactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onContactsPermissionGranted()
    }

    // Fire once when the search bar transitions from empty → non-empty.
    // The Boolean key means the effect only re-launches if that state flips,
    // not on every individual keystroke.
    val isSearchActive = uiState.searchQuery.isNotEmpty()
    LaunchedEffect(isSearchActive) {
        if (isSearchActive &&
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

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
        // ── Step 4: home screen content ──────────────────────────────────────
        // Shown above the search bar; hidden while the user is actively typing.
        if (uiState.searchQuery.isEmpty()) {
            ClockSection(
                time           = uiState.currentTime,
                date           = uiState.currentDate,
                onToggleFormat = viewModel::toggleClockFormat,
            )
            val filledPins = uiState.pinnedItems.filterNotNull()
            if (filledPins.isNotEmpty()) {
                PinnedSection(
                    items          = uiState.pinnedItems,
                    onItemClick    = viewModel::onPinnedItemClick,
                    onItemLongPress = { slot -> viewModel.onPinnedItemLongPress(slot) },
                )
            }
        }
        // ── Step 3: search bar ───────────────────────────────────────────────
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = viewModel::onSearchQueryChange,
            onClear = {
                viewModel.clearSearch()
                focusManager.clearFocus()
                keyboardController?.hide()
            },
            focusRequester = focusRequester,
            onFocusChange = { isSearchBarFocused = it },
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

            // Debounce has settled and produced results — show filtered list.
            uiState.searchResults.isNotEmpty() -> SearchResultsList(
                modifier   = Modifier.weight(1f),
                results    = uiState.searchResults,
                listState  = listState,
                onResultClick = { result ->
                    keyboardController?.hide()
                    viewModel.onSearchResultClick(result)
                },
                onAppLongPress = { app -> viewModel.onAppLongPress(app) },
            )

            // Full app list is available — show it.
            // This covers: normal mode AND the 150 ms debounce window while typing,
            // so the user never sees a jarring "no results" flash mid-keystroke.
            uiState.apps.isNotEmpty() -> AppList(
                modifier       = Modifier.weight(1f),
                apps           = uiState.apps,
                listState      = listState,
                onAppClick     = viewModel::onAppClick,
                onAppLongPress = viewModel::onAppLongPress,
            )

            // Debounce settled, query was non-blank, but nothing matched.
            uiState.searchQuery.isNotEmpty() -> CenteredHint("no results")

            else -> CenteredHint("no apps found")
        }
    }

    if (uiState.selectedApp != null) {
        AppOptionsSheet(
            app       = uiState.selectedApp!!,
            canPin    = uiState.pinnedItems.any { it == null },
            onPin     = { viewModel.pinApp(it) },
            onHide    = { viewModel.hideApp(it) },
            onDismiss = viewModel::dismissBottomSheet,
        )
    }

    uiState.editingPinnedSlot?.let { slot ->
        PinnedItemSheet(
            item      = uiState.pinnedItems.getOrNull(slot),
            onRemove  = { viewModel.removePinnedItem(slot) },
            onDismiss = viewModel::dismissPinnedEditor,
        )
    }
}

// ── Search bar ───────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,          // from ViewModel — used only to detect external clears
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // FIX 3: Use TextFieldValue locally so the cursor position is preserved
    // across every recomposition. With plain String, Compose resets the cursor
    // to 0 on each state update, making the 2nd typed character appear before
    // the 1st. TextFieldValue carries the cursor/selection as part of the state.
    var fieldValue by remember { mutableStateOf(TextFieldValue(query)) }

    // Sync when the ViewModel clears the query from outside (e.g. after an app
    // launch). Only reset when the ViewModel explicitly empties the string; do
    // NOT reset while the user is still typing (rawQuery already matches fieldValue.text).
    LaunchedEffect(query) {
        if (query.isEmpty() && fieldValue.text.isNotEmpty()) {
            fieldValue = TextFieldValue("")
        }
    }

    // FIX 1: Wrap the search bar in its own surface so it is visually distinct
    // from the app list below. A subtle bottom divider draws the boundary.
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                // FIX 2: Symmetric vertical padding so text is never clipped.
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                // FIX 3: drive the field with TextFieldValue, not String.
                value = fieldValue,
                onValueChange = { newValue ->
                    fieldValue = newValue           // update cursor + text locally
                    onQueryChange(newValue.text)    // send text-only to ViewModel
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                // BasicTextField defaults to SolidColor(Color.Black), which is
                // invisible on dark backgrounds. Match the text colour instead.
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect    = false,
                    keyboardType   = KeyboardType.Text,
                    imeAction      = ImeAction.Search,
                ),
                decorationBox = { innerTextField ->
                    // FIX 2: fillMaxWidth + vertical padding inside decorationBox
                    // so the placeholder text has room and is never clipped.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (fieldValue.text.isEmpty()) {
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

            if (fieldValue.text.isNotEmpty()) {
                Text(
                    text  = "×",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    modifier = Modifier
                        .clickable(onClick = onClear)
                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }

        // FIX 1: Thin divider line that separates the search bar from the list.
        HorizontalDivider(
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            thickness = 1.dp,
        )
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

// ── Step 4 composables ────────────────────────────────────────────────────────

@Composable
private fun ClockSection(
    time: String,
    date: String,
    onToggleFormat: () -> Unit,
) {
    if (time.isEmpty()) return   // clock not yet initialised — skip for one frame

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleFormat)
            .padding(horizontal = 32.dp)
            .padding(top = 40.dp, bottom = 20.dp),
    ) {
        Text(
            text  = time,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = date,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedSection(
    items: List<PinnedItem?>,
    onItemClick: (PinnedItem) -> Unit,
    onItemLongPress: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 8.dp),
    ) {
        items.forEachIndexed { slot, item ->
            if (item == null) return@forEachIndexed
            val label = when (item) {
                is PinnedItem.App     -> item.label
                is PinnedItem.Contact -> item.name
            }
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick     = { onItemClick(item) },
                        onLongClick = { onItemLongPress(slot) },
                    )
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinnedItemSheet(
    item: PinnedItem?,
    onRemove: () -> Unit,
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
            val title = when (item) {
                is PinnedItem.App     -> item.label
                is PinnedItem.Contact -> item.name
                null                  -> "pinned shortcut"
            }
            Text(
                text  = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))
            SheetAction("remove") { onRemove() }
        }
    }
}

// ── Bottom sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppOptionsSheet(
    app: AppInfo,
    canPin: Boolean,
    onPin: (AppInfo) -> Unit,
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
            if (canPin) SheetAction("pin") { onPin(app) }
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
