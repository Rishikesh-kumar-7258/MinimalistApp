package com.minimalist.launcher.feature.appdrawer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import com.minimalist.launcher.core.model.AppInfo
import com.minimalist.launcher.core.model.FocusProfile
import com.minimalist.launcher.core.model.GestureAction
import com.minimalist.launcher.core.model.GestureSettings
import com.minimalist.launcher.core.model.PinnedItem
import com.minimalist.launcher.core.model.SearchResult
import com.minimalist.launcher.core.model.SortOrder
import com.minimalist.launcher.core.model.TextAlignment
import com.minimalist.launcher.feature.LocalAppearance
import com.minimalist.launcher.feature.friction.FrictionScreen
import com.minimalist.launcher.feature.scratchpad.ScratchPadScreen
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun TextAlignment.toTextAlign(): TextAlign = when (this) {
    TextAlignment.LEFT   -> TextAlign.Left
    TextAlignment.CENTER -> TextAlign.Center
    TextAlignment.RIGHT  -> TextAlign.Right
}

// ─────────────────────────────────────────────────────────────────────────────
// Top-level screen — HorizontalPager with Home (page 0) and App Drawer (page 1)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(viewModel: AppDrawerViewModel, onOpenSettings: () -> Unit = {}) {
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager       = LocalFocusManager.current
    val context            = LocalContext.current
    val scope              = rememberCoroutineScope()

    val pagerState     = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val listState      = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    var isSearchBarFocused   by remember { mutableStateOf(false) }
    // Signals the drawer page to auto-focus the search bar after navigation.
    var pendingFocusSearch   by remember { mutableStateOf(false) }
    var showProfileSwitcher  by remember { mutableStateOf(false) }

    // ── Focus helpers ─────────────────────────────────────────────────────────

    fun dismissSearch() {
        viewModel.clearSearch()
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun goHome() {
        dismissSearch()
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    // ── Gesture dispatcher ────────────────────────────────────────────────────

    fun onGesture(action: GestureAction) {
        when (action) {
            GestureAction.NONE        -> { /* no-op */ }
            GestureAction.APP_DRAWER  -> scope.launch { pagerState.animateScrollToPage(1) }
            GestureAction.SEARCH      -> {
                pendingFocusSearch = true
                scope.launch { pagerState.animateScrollToPage(1) }
            }
            GestureAction.DIALER           -> viewModel.launchDialer()
            GestureAction.SCRATCH_PAD      -> viewModel.openScratchPad()
            GestureAction.RECENT_APPS      -> { /* requires system overlay, not available as a launcher */ }
            GestureAction.PROFILE_SWITCHER -> showProfileSwitcher = true
        }
    }

    // ── Back handler ──────────────────────────────────────────────────────────
    // Priority (LIFO, added after MainActivity's suppress callback):
    //   1. If search is active → clear search & keyboard
    //   2. Else if on drawer page → navigate back to home
    //   3. Otherwise → swallowed by MainActivity's suppress callback (home screen)

    BackHandler(
        enabled = isSearchBarFocused
                || uiState.searchQuery.isNotEmpty()
                || pagerState.currentPage != 0
    ) {
        when {
            isSearchBarFocused || uiState.searchQuery.isNotEmpty() -> dismissSearch()
            else -> goHome()
        }
    }

    // ── Effects ───────────────────────────────────────────────────────────────

    // Navigate to home when ViewModel emits the signal (e.g. onNewIntent).
    LaunchedEffect(Unit) {
        viewModel.navigateToHome.collect { goHome() }
    }

    // Returning to home page → dismiss keyboard.
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // When we land on page 1 with a pending search focus, claim it.
    LaunchedEffect(pagerState.currentPage, pendingFocusSearch) {
        if (pagerState.currentPage == 1 && pendingFocusSearch) {
            pendingFocusSearch = false
            focusRequester.requestFocus()
        }
    }

    // List scrolling → dismiss keyboard.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // External clear (onNewIntent via goHome) → dismiss focus.
    LaunchedEffect(uiState.searchQuery.isEmpty()) {
        if (uiState.searchQuery.isEmpty()) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // Contact permission — requested the first time search becomes active.
    val requestContactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.onContactsPermissionGranted() }

    LaunchedEffect(uiState.searchQuery.isNotEmpty()) {
        if (uiState.searchQuery.isNotEmpty() &&
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> HomeScreenPage(
                    uiState              = uiState,
                    onToggleClock        = viewModel::toggleClockFormat,
                    onPinnedClick        = viewModel::onPinnedItemClick,
                    onPinnedLong         = viewModel::onPinnedItemLongPress,
                    onGesture            = ::onGesture,
                    onOpenSettings       = onOpenSettings,
                    onOpenProfileSwitcher = { showProfileSwitcher = true },
                )
                else -> AppDrawerPage(
                    uiState          = uiState,
                    listState        = listState,
                    focusRequester   = focusRequester,
                    isSearchFocused  = isSearchBarFocused,
                    onFocusChange    = { isSearchBarFocused = it },
                    onQueryChange    = viewModel::onSearchQueryChange,
                    onClear          = { dismissSearch() },
                    onSortOrder      = viewModel::setSortOrder,
                    onAppClick       = viewModel::onAppClick,
                    onAppLongPress   = viewModel::onAppLongPress,
                    onResultClick    = { result ->
                        keyboardController?.hide()
                        viewModel.onSearchResultClick(result)
                    },
                    onResultLongPress = { app -> viewModel.onAppLongPress(app) },
                    onRetry          = viewModel::retryLoadApps,
                )
            }
        }
    }

    // ── Sheets (shown above the pager) ────────────────────────────────────────

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

    if (showProfileSwitcher) {
        ProfileSwitcherSheet(
            current   = uiState.activeProfile,
            onSelect  = { profile ->
                viewModel.switchProfile(profile)
                showProfileSwitcher = false
            },
            onDismiss = { showProfileSwitcher = false },
        )
    }

    // ── Step 9: Friction screen overlay ──────────────────────────────────────

    if (uiState.frictionApp != null && uiState.frictionReason != null) {
        FrictionScreen(
            appLabel  = uiState.frictionApp!!.label,
            reason    = uiState.frictionReason!!,
            message   = uiState.frictionMessage,
            onProceed = viewModel::proceedAfterFriction,
            onGoBack  = viewModel::clearFriction,
        )
    }

    // ── Step 10: Scratch pad overlay ─────────────────────────────────────────

    if (uiState.showScratchPad) {
        ScratchPadScreen(
            content         = uiState.scratchPadContent,
            onContentChange = viewModel::setScratchPadContent,
            onClose         = viewModel::closeScratchPad,
        )
    }

    // ── Step 10: App lock — biometric prompt ─────────────────────────────────

    LaunchedEffect(uiState.pendingLockedApp) {
        val app = uiState.pendingLockedApp ?: return@LaunchedEffect
        val activity = context as? FragmentActivity ?: run {
            viewModel.cancelLock(); return@LaunchedEffect
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.launchAfterBiometric()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.cancelLock()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("App lock")
            .setSubtitle(app.label)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 0 — Home screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeScreenPage(
    uiState: AppDrawerUiState,
    onToggleClock: () -> Unit,
    onPinnedClick: (PinnedItem) -> Unit,
    onPinnedLong: (Int) -> Unit,
    onGesture: (GestureAction) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfileSwitcher: () -> Unit,
) {
    val gestures = uiState.gestureSettings

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Observe touches without consuming so HorizontalPager still handles
            // horizontal swipes for page navigation.
            .pointerInput(gestures) {
                val swipeThresholdPx  = 80.dp.toPx()
                val tapMaxMovePx      = 20f
                val doubleTapMs       = 400L
                val longPressMs       = 500L
                var lastTapTime       = -1L

                awaitPointerEventScope {
                    while (true) {
                        val down       = awaitPointerEvent(PointerEventPass.Initial)
                        val downChange = down.changes.firstOrNull() ?: continue
                        if (!downChange.pressed) continue   // skip non-down events

                        val startPos  = downChange.position
                        val startTime = System.currentTimeMillis()
                        var endPos    = startPos

                        // Track until finger lifts.
                        while (true) {
                            val event  = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            endPos = change.position
                            if (!change.pressed) break
                        }

                        val dx      = endPos.x - startPos.x
                        val dy      = endPos.y - startPos.y
                        val absDx   = abs(dx)
                        val absDy   = abs(dy)
                        val elapsed = System.currentTimeMillis() - startTime

                        when {
                            // Long press on background (held still >500 ms) → profile switcher
                            absDx < tapMaxMovePx && absDy < tapMaxMovePx && elapsed >= longPressMs -> {
                                lastTapTime = -1
                                onOpenProfileSwitcher()
                            }
                            // Tap — check for double-tap
                            absDx < tapMaxMovePx && absDy < tapMaxMovePx && elapsed < 300 -> {
                                val now = System.currentTimeMillis()
                                if (lastTapTime > 0 && now - lastTapTime < doubleTapMs) {
                                    onGesture(gestures.doubleTap)
                                    lastTapTime = -1
                                } else {
                                    lastTapTime = now
                                }
                            }
                            // Directional swipe
                            else -> {
                                lastTapTime = -1
                                when {
                                    absDy > absDx * 1.5f && dy < -swipeThresholdPx -> onGesture(gestures.swipeUp)
                                    absDy > absDx * 1.5f && dy >  swipeThresholdPx -> onGesture(gestures.swipeDown)
                                    absDx > absDy * 1.5f && dx < -swipeThresholdPx -> onGesture(gestures.swipeLeft)
                                    absDx > absDy * 1.5f && dx >  swipeThresholdPx -> onGesture(gestures.swipeRight)
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ClockSection(
                time           = uiState.currentTime,
                date           = uiState.currentDate,
                onToggleFormat = onToggleClock,
            )

            WidgetLine(text = uiState.weatherLine)
            WidgetLine(text = uiState.calendarLine)

            if (uiState.pinnedItems.any { it != null }) {
                PinnedSection(
                    items           = uiState.pinnedItems,
                    onItemClick     = onPinnedClick,
                    onItemLongPress = onPinnedLong,
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Bottom bar: profile indicator + settings link ─────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Active profile name — tap to switch
                Text(
                    text  = if (uiState.activeProfile == FocusProfile.NONE) "—"
                            else uiState.activeProfile.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(
                        alpha = if (uiState.activeProfile == FocusProfile.NONE) 0.15f else 0.55f,
                    ),
                    modifier = Modifier
                        .clickable { onOpenProfileSwitcher() }
                        .padding(4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text  = "settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                    modifier = Modifier
                        .clickable { onOpenSettings() }
                        .padding(8.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 1 — App drawer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppDrawerPage(
    uiState: AppDrawerUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusRequester: FocusRequester,
    isSearchFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onResultLongPress: (AppInfo) -> Unit,
    onRetry: () -> Unit,
) {
    // Pull-down at the top of the list re-focuses the search bar.
    val nestedScrollConnection = remember(focusRequester) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 8f) focusRequester.requestFocus()
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        SearchBar(
            query         = uiState.searchQuery,
            onQueryChange = onQueryChange,
            onClear       = onClear,
            focusRequester = focusRequester,
            onFocusChange  = onFocusChange,
        )

        if (uiState.searchQuery.isEmpty()) {
            SortToggleRow(current = uiState.sortOrder, onToggle = onSortOrder)
        }

        when {
            uiState.isLoading -> CenteredHint("loading...")

            uiState.error != null -> ErrorState(onRetry = onRetry)

            uiState.searchResults.isNotEmpty() -> SearchResultsList(
                modifier       = Modifier.weight(1f),
                results        = uiState.searchResults,
                listState      = listState,
                onResultClick  = onResultClick,
                onAppLongPress = onResultLongPress,
            )

            uiState.apps.isNotEmpty() -> AppList(
                modifier       = Modifier.weight(1f),
                apps           = uiState.apps,
                listState      = listState,
                onAppClick     = onAppClick,
                onAppLongPress = onAppLongPress,
            )

            uiState.searchQuery.isNotEmpty() -> CenteredHint("no results")

            else -> CenteredHint("no apps found")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4 — Home screen composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClockSection(
    time: String,
    date: String,
    onToggleFormat: () -> Unit,
) {
    if (time.isEmpty()) return
    val textAlign = LocalAppearance.current.textAlignment.toTextAlign()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleFormat)
            .padding(horizontal = 32.dp)
            .padding(top = 40.dp, bottom = 20.dp),
    ) {
        Text(
            text      = time,
            style     = MaterialTheme.typography.displayMedium,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = textAlign,
            modifier  = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text      = date,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = textAlign,
            modifier  = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 6 — Widget lines (weather + calendar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WidgetLine(text: String?) {
    if (text == null) return
    val textAlign = LocalAppearance.current.textAlignment.toTextAlign()
    Text(
        text      = text,
        style     = MaterialTheme.typography.bodyMedium,
        color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        textAlign = textAlign,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 6.dp),
    )
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
            AppTextItem(
                label       = label,
                onClick     = { onItemClick(item) },
                onLongClick = { onItemLongPress(slot) },
                verticalPad = 10.dp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search bar (Step 3)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(query)) }

    LaunchedEffect(query) {
        if (query.isEmpty() && fieldValue.text.isNotEmpty()) {
            fieldValue = TextFieldValue("")
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value         = fieldValue,
                onValueChange = { newValue ->
                    fieldValue = newValue
                    onQueryChange(newValue.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                singleLine    = true,
                textStyle     = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush   = SolidColor(MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect    = false,
                    keyboardType   = KeyboardType.Text,
                    imeAction      = ImeAction.Search,
                ),
                decorationBox = { innerTextField ->
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
        HorizontalDivider(
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            thickness = 1.dp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared press-feedback item (Steps 2, 3, 4)
// A scale-spring + ripple combination makes taps and long-presses clearly felt.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTextItem(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    verticalPad: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "item_scale",
    )

    val textAlign = LocalAppearance.current.textAlignment.toTextAlign()
    Text(
        text      = label,
        style     = MaterialTheme.typography.bodyLarge,
        color     = MaterialTheme.colorScheme.onBackground,
        textAlign = textAlign,
        modifier  = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = onClick,
                onLongClick       = onLongClick,
            )
            .padding(horizontal = 32.dp, vertical = verticalPad),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Search results list (Step 3)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchResultsList(
    modifier: Modifier,
    results: List<SearchResult>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onResultClick: (SearchResult) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
) {
    if (results.isEmpty()) { CenteredHint("no results"); return }

    LazyColumn(modifier = modifier, state = listState) {
        items(results, key = { result ->
            when (result) {
                is SearchResult.App     -> "app_${result.info.packageName}"
                is SearchResult.Contact -> "contact_${result.number}"
                is SearchResult.Setting -> "setting_${result.label}"
            }
        }) { result ->
            val (label, hint) = when (result) {
                is SearchResult.App     -> result.info.label to null
                is SearchResult.Contact -> result.name        to "call"
                is SearchResult.Setting -> result.label       to "settings"
            }
            SearchResultRow(
                label       = label,
                hint        = hint,
                onClick     = { onResultClick(result) },
                onLongClick = { if (result is SearchResult.App) onAppLongPress(result.info) },
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    label: String,
    hint: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "result_scale",
    )

    val textAlign = LocalAppearance.current.textAlignment.toTextAlign()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = onClick,
                onLongClick       = onLongClick,
            )
            .padding(horizontal = 32.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text      = label,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = textAlign,
            modifier  = Modifier.weight(1f),
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

// ─────────────────────────────────────────────────────────────────────────────
// Normal app list (Step 2)
// ─────────────────────────────────────────────────────────────────────────────

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
            AppTextItem(
                label       = app.label,
                onClick     = { onAppClick(app) },
                onLongClick = { onAppLongPress(app) },
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CenteredHint(text: String) {
    Box(
        modifier         = Modifier.fillMaxWidth().height(120.dp),
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
        modifier         = Modifier.fillMaxWidth().height(160.dp),
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
        color = if (active) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom sheets (Steps 2, 4)
// ─────────────────────────────────────────────────────────────────────────────

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
            if (canPin) SheetAction("pin")      { onPin(app) }
            SheetAction("hide")     { onHide(app) }
            SheetAction("group")    { /* future */ }
            SheetAction("restrict") { /* Step 9 */ }
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

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile switcher sheet (Step 8)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSwitcherSheet(
    current: FocusProfile,
    onSelect: (FocusProfile) -> Unit,
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
                .padding(bottom = 40.dp),
        ) {
            Text(
                text  = "focus profile",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))
            FocusProfile.entries.forEach { profile ->
                val label = if (profile == FocusProfile.NONE) "none" else profile.name.lowercase()
                val isActive = profile == current
                TextButton(
                    onClick  = { onSelect(profile) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = if (isActive) "$label ✓" else label,
                        style    = MaterialTheme.typography.bodyLarge,
                        color    = if (isActive)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
