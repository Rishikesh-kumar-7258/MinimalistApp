package com.minimalist.launcher.feature.settings

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.core.model.AppFontFamily
import com.minimalist.launcher.core.model.ClockFormat
import com.minimalist.launcher.core.model.FontSize
import com.minimalist.launcher.core.model.GestureAction
import com.minimalist.launcher.core.model.GestureType
import com.minimalist.launcher.core.model.SortOrder
import com.minimalist.launcher.core.model.TextAlignment
import com.minimalist.launcher.core.model.ThemeMode

// ─────────────────────────────────────────────────────────────────────────────
// Color presets — 12 background/text pairs with safe contrast ratios
// ─────────────────────────────────────────────────────────────────────────────

private data class ColorPreset(val name: String, val bg: String, val text: String)

private val COLOR_PRESETS = listOf(
    ColorPreset("midnight", "#000000", "#FFFFFF"),
    ColorPreset("chalk",    "#FFFFFF", "#000000"),
    ColorPreset("slate",    "#1A1A2E", "#E0E0E0"),
    ColorPreset("paper",    "#F5F0E8", "#2C2C2C"),
    ColorPreset("carbon",   "#2D2D2D", "#E8E8E8"),
    ColorPreset("ocean",    "#003153", "#E8F4F8"),
    ColorPreset("forest",   "#1B2A1B", "#B8D4B8"),
    ColorPreset("lilac",    "#1C1B22", "#D4C5E8"),
    ColorPreset("github",   "#0D1117", "#C9D1D9"),
    ColorPreset("cream",    "#FFFBF0", "#3D3635"),
    ColorPreset("sepia",    "#2B1D0E", "#E8D5B7"),
    ColorPreset("matrix",   "#0A0A0A", "#00C853"),
)

private fun String.toColor(): Color? = runCatching {
    Color(android.graphics.Color.parseColor(this))
}.getOrNull()

private fun String.isValidHex(): Boolean =
    matches(Regex("^#[0-9A-Fa-f]{6}$"))

// ─────────────────────────────────────────────────────────────────────────────
// Settings screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenFocus: () -> Unit = {},
    onOpenUsage: () -> Unit = {},
    onOpenRestrictions: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appearance = uiState.appearance

    BackHandler { onBack() }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 32.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Spacer(Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = "←",
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable { onBack() }.padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            Text(
                text  = "settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(32.dp))

        // ── APPEARANCE ────────────────────────────────────────────────────────
        SectionHeader("appearance")

        // Theme mode
        SettingRow(label = "theme") {
            OptionGroup(
                options   = listOf("light", "dark", "amoled"),
                selected  = when (appearance.themeMode) {
                    ThemeMode.LIGHT  -> "light"
                    ThemeMode.DARK   -> "dark"
                    ThemeMode.AMOLED -> "amoled"
                },
                onSelect  = { opt ->
                    viewModel.setThemeMode(
                        when (opt) {
                            "light"  -> ThemeMode.LIGHT
                            "amoled" -> ThemeMode.AMOLED
                            else     -> ThemeMode.DARK
                        }
                    )
                },
            )
        }

        // Font family
        SettingRow(label = "font") {
            OptionGroup(
                options  = listOf("mono", "sans", "serif"),
                selected = when (appearance.fontFamily) {
                    AppFontFamily.MONOSPACE  -> "mono"
                    AppFontFamily.SANS_SERIF -> "sans"
                    AppFontFamily.SERIF      -> "serif"
                },
                onSelect = { opt ->
                    viewModel.setFontFamily(
                        when (opt) {
                            "sans"  -> AppFontFamily.SANS_SERIF
                            "serif" -> AppFontFamily.SERIF
                            else    -> AppFontFamily.MONOSPACE
                        }
                    )
                },
            )
        }

        // Font size
        SettingRow(label = "size") {
            OptionGroup(
                options  = listOf("small", "medium", "large"),
                selected = when (appearance.fontSize) {
                    FontSize.SMALL  -> "small"
                    FontSize.MEDIUM -> "medium"
                    FontSize.LARGE  -> "large"
                },
                onSelect = { opt ->
                    viewModel.setFontSize(
                        when (opt) {
                            "small" -> FontSize.SMALL
                            "large" -> FontSize.LARGE
                            else    -> FontSize.MEDIUM
                        }
                    )
                },
            )
        }

        // Text alignment
        SettingRow(label = "align") {
            OptionGroup(
                options  = listOf("left", "center", "right"),
                selected = when (appearance.textAlignment) {
                    TextAlignment.LEFT   -> "left"
                    TextAlignment.CENTER -> "center"
                    TextAlignment.RIGHT  -> "right"
                },
                onSelect = { opt ->
                    viewModel.setTextAlignment(
                        when (opt) {
                            "center" -> TextAlignment.CENTER
                            "right"  -> TextAlignment.RIGHT
                            else     -> TextAlignment.LEFT
                        }
                    )
                },
            )
        }

        Spacer(Modifier.height(20.dp))

        // Color palette
        Text(
            text  = "colors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(12.dp))
        ColorPaletteGrid(
            currentBg   = appearance.customBgColor,
            currentText = appearance.customTextColor,
            onSelect    = { preset -> viewModel.setPresetPalette(preset.bg, preset.text) },
        )

        Spacer(Modifier.height(12.dp))
        CustomHexRow(
            label        = "bg",
            currentHex   = appearance.customBgColor,
            onHexChange  = { hex ->
                viewModel.setPresetPalette(hex, appearance.customTextColor ?: "#FFFFFF")
            },
        )
        Spacer(Modifier.height(8.dp))
        CustomHexRow(
            label        = "text",
            currentHex   = appearance.customTextColor,
            onHexChange  = { hex ->
                viewModel.setPresetPalette(appearance.customBgColor ?: "#000000", hex)
            },
        )
        if (appearance.customBgColor != null || appearance.customTextColor != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick  = { viewModel.clearCustomColors() },
                modifier = Modifier.padding(start = 0.dp),
            ) {
                Text(
                    text  = "reset colors",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        Spacer(Modifier.height(24.dp))

        // ── BEHAVIOR ──────────────────────────────────────────────────────────
        SectionHeader("behavior")

        SettingRow(label = "sort") {
            OptionGroup(
                options  = listOf("a-z", "freq"),
                selected = if (uiState.sortOrder == SortOrder.ALPHABETICAL) "a-z" else "freq",
                onSelect = { opt ->
                    viewModel.setSortOrder(
                        if (opt == "a-z") SortOrder.ALPHABETICAL else SortOrder.FREQUENCY
                    )
                },
            )
        }

        SettingRow(label = "clock") {
            OptionGroup(
                options  = listOf("12h", "24h"),
                selected = if (uiState.clockFormat == ClockFormat.HOUR_12) "12h" else "24h",
                onSelect = { opt ->
                    viewModel.setClockFormat(
                        if (opt == "12h") ClockFormat.HOUR_12 else ClockFormat.HOUR_24
                    )
                },
            )
        }

        // Focus profiles link
        NavRow(label = "focus",        onClick = onOpenFocus)
        // Usage & restrictions links
        NavRow(label = "usage",        onClick = onOpenUsage)
        NavRow(label = "restrictions", onClick = onOpenRestrictions)

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        Spacer(Modifier.height(24.dp))

        // ── GESTURES ──────────────────────────────────────────────────────────
        SectionHeader("gestures")

        val gestures = uiState.gestureSettings
        listOf(
            "swipe up"    to GestureType.SWIPE_UP,
            "swipe down"  to GestureType.SWIPE_DOWN,
            "swipe left"  to GestureType.SWIPE_LEFT,
            "swipe right" to GestureType.SWIPE_RIGHT,
            "double tap"  to GestureType.DOUBLE_TAP,
        ).forEach { (label, type) ->
            val current = when (type) {
                GestureType.SWIPE_UP    -> gestures.swipeUp
                GestureType.SWIPE_DOWN  -> gestures.swipeDown
                GestureType.SWIPE_LEFT  -> gestures.swipeLeft
                GestureType.SWIPE_RIGHT -> gestures.swipeRight
                GestureType.DOUBLE_TAP  -> gestures.doubleTap
            }
            GestureRow(
                label    = label,
                current  = current,
                onSelect = { action -> viewModel.setGestureAction(type, action) },
            )
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        Spacer(Modifier.height(24.dp))

        // ── WIDGETS ───────────────────────────────────────────────────────────
        SectionHeader("widgets")

        WidgetToggleRow(
            label   = "weather",
            enabled = uiState.weatherEnabled,
            onToggle = viewModel::setWeatherEnabled,
        )

        if (uiState.weatherEnabled) {
            Spacer(Modifier.height(8.dp))
            WidgetTextInputRow(
                label       = "api key",
                value       = uiState.weatherApiKey,
                placeholder = "openweathermap key",
                onValueChange = viewModel::setWeatherApiKey,
                isPassword  = true,
            )
            Spacer(Modifier.height(8.dp))
            WidgetTextInputRow(
                label       = "city",
                value       = uiState.weatherCity,
                placeholder = "e.g. London",
                onValueChange = viewModel::setWeatherCity,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick  = { viewModel.fetchWeatherNow() },
                modifier = Modifier.padding(start = 0.dp),
            ) {
                Text(
                    text  = "fetch now",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        WidgetToggleRow(
            label   = "calendar",
            enabled = uiState.calendarEnabled,
            onToggle = viewModel::setCalendarEnabled,
        )

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        Spacer(Modifier.height(24.dp))

        // ── PERMISSIONS ───────────────────────────────────────────────────────
        SectionHeader("permissions")

        val contactsGranted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val requestContacts = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* status updates on recomposition */ }

        PermissionRow(
            label   = "contacts",
            granted = contactsGranted,
            onGrant = { requestContacts.launch(Manifest.permission.READ_CONTACTS) },
        )

        val usageGranted = remember {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            if (appOps == null) {
                false
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName,
                ) == AppOpsManager.MODE_ALLOWED
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName,
                ) == AppOpsManager.MODE_ALLOWED
            }
        }

        PermissionRow(
            label   = "usage stats",
            granted = usageGranted,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        )

        val calendarGranted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        val requestCalendar = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* recomposition picks up the new status */ }

        PermissionRow(
            label   = "calendar",
            granted = calendarGranted,
            onGrant = { requestCalendar.launch(Manifest.permission.READ_CALENDAR) },
        )

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        Spacer(Modifier.height(24.dp))

        // ── DATA ──────────────────────────────────────────────────────────────
        SectionHeader("data")

        val backupLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> uri?.let { viewModel.writeBackup(context, it) } }

        val restoreLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let { viewModel.readAndApplyRestore(context, it) } }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text  = "backup →",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable { backupLauncher.launch("minimalist_backup.json") }
                    .padding(4.dp),
            )
            Text(
                text  = "restore →",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable { restoreLauncher.launch(arrayOf("application/json")) }
                    .padding(4.dp),
            )
        }

        if (uiState.backupMessage.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text  = uiState.backupMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            )
        }

        Spacer(Modifier.height(64.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color palette grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColorPaletteGrid(
    currentBg: String?,
    currentText: String?,
    onSelect: (ColorPreset) -> Unit,
) {
    val chunks = COLOR_PRESETS.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunks.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { preset ->
                    val isSelected = currentBg == preset.bg && currentText == preset.text
                    val bgColor = preset.bg.toColor() ?: Color.Black
                    val textColor = preset.text.toColor() ?: Color.White
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(bgColor)
                            .then(
                                if (isSelected) Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    shape = RoundedCornerShape(4.dp),
                                ) else Modifier
                            )
                            .clickable { onSelect(preset) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = "Aa",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                        )
                    }
                }
                // fill trailing empty slots so the last row aligns
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom hex input
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomHexRow(label: String, currentHex: String?, onHexChange: (String) -> Unit) {
    var text by remember(currentHex) { mutableStateOf(currentHex ?: "") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        // Color preview swatch
        val previewColor = currentHex?.toColor()
        if (previewColor != null) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(previewColor)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        RoundedCornerShape(2.dp),
                    )
            )
            Spacer(Modifier.width(8.dp))
        }
        BasicTextField(
            value = text,
            onValueChange = { new ->
                text = new
                val candidate = if (new.startsWith("#")) new else "#$new"
                if (candidate.isValidHex()) onHexChange(candidate.uppercase())
            },
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            singleLine  = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType   = KeyboardType.Ascii,
            ),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            text  = "#RRGGBB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation row (taps to open a sub-screen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text     = "configure →",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier
                .clickable { onClick() }
                .padding(4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (granted) {
            Text(
                text  = "granted",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            )
        } else {
            Text(
                text     = "grant",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onGrant() }.padding(4.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(64.dp),
        )
        content()
    }
}

@Composable
private fun OptionGroup(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { opt ->
            val isActive = opt == selected
            Text(
                text  = opt,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive)
                    MaterialTheme.colorScheme.onBackground
                else
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier
                    .clickable { onSelect(opt) }
                    .padding(4.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gesture row (Step 7)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GestureRow(label: String, current: GestureAction, onSelect: (GestureAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GestureAction.entries.forEach { action ->
                val tag = when (action) {
                    GestureAction.NONE             -> "—"
                    GestureAction.APP_DRAWER       -> "drawer"
                    GestureAction.SEARCH           -> "search"
                    GestureAction.DIALER           -> "dialer"
                    GestureAction.SCRATCH_PAD      -> "pad"
                    GestureAction.RECENT_APPS      -> "recents"
                    GestureAction.PROFILE_SWITCHER -> "focus"
                }
                Text(
                    text  = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (action == current)
                        MaterialTheme.colorScheme.onBackground
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.clickable { onSelect(action) }.padding(4.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Widget helpers (Step 6)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WidgetToggleRow(label: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("on" to true, "off" to false).forEach { (label2, value) ->
                Text(
                    text  = label2,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled == value)
                        MaterialTheme.colorScheme.onBackground
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.clickable { onToggle(value) }.padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun WidgetTextInputRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    var text by remember(value) { mutableStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.width(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text,
            onValueChange = { new ->
                text = new
                onValueChange(new)
            },
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            singleLine  = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            ),
            visualTransformation = if (isPassword && text.isNotEmpty())
                androidx.compose.ui.text.input.PasswordVisualTransformation('•')
            else
                androidx.compose.ui.text.input.VisualTransformation.None,
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            text  = placeholder,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}
