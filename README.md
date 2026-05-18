# Minimalist Launcher

[![Android CI](https://github.com/Rishikesh-kumar-7258/MinimalistApp/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Rishikesh-kumar-7258/MinimalistApp/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg)
![Min SDK](https://img.shields.io/badge/min%20SDK-26-brightgreen.svg)

A text-only Android home screen replacement. No wallpaper. No icons. No distractions.

Everything you need to navigate your phone — apps, contacts, settings — accessible as plain text, fast, and without visual noise.

---

## Philosophy

Modern launchers are built around icons, widgets, animations, and notifications competing for your attention. Minimalist Launcher removes all of that. Every element on screen is a word or a number. You read it and act. Nothing else happens.

The design constraint is intentional: if something can't be expressed as text, it doesn't belong on the home screen.

---

## What's built so far

### Step 1 — Launcher Registration
The app registers as an Android home screen replacement. Pressing Home opens this app. The back button is swallowed on the home screen, as expected of any launcher.

### Step 2 — App Drawer
All installed apps listed as plain text in a `LazyColumn`. Alphabetical and frequency-based sorting. Long-pressing any app opens a bottom sheet: pin, hide, group, or restrict. Hidden apps are stored in DataStore and filtered at query time. Icons are never loaded or rendered.

### Step 3 — Unified Search
A single search bar at the top of the app drawer filters three sources simultaneously as you type:

- **Apps** — by label, debounced 150 ms
- **Contacts** — via `ContactsContract`, permission requested lazily on first use
- **Settings** — a static map of 17 common settings actions (Wi-Fi, Bluetooth, Display, etc.)

Pull down anywhere on the app list to re-focus the search bar. Back button clears search. Search auto-clears on launch or home press.

### Step 4 — Home Screen & Pinned Shortcuts
The home screen shows:

- **Clock** — large text, tappable to toggle 12 h / 24 h format
- **Date** — below the clock in a lighter weight
- **Pinned shortcuts** — up to 5 apps or contacts, stored in DataStore; long-press to remove; pin from the app drawer via long-press → "pin"

Swiping left from the home screen opens the app drawer. Swiping up launches Google Search. Pressing Home from anywhere returns to the home screen and clears any active search.

---

## Navigation

| Gesture | Action |
|---------|--------|
| Swipe left | Open app drawer |
| Swipe right | Return to home screen |
| Swipe up (home screen) | Google Search |
| Pull down (app list) | Focus search bar |
| Tap app | Launch |
| Long-press app | Pin · Hide · Group · Restrict |
| Tap clock | Toggle 12 h / 24 h |
| Long-press pinned item | Remove shortcut |
| Back (search active) | Clear search |
| Back (drawer, no search) | Return home |

---

## Architecture

Three-module Gradle project:

```
MinimalistLauncher/
├── app/          Activity, Application class, theme
├── core/         Data layer — repositories, models
└── feature/      UI layer — ViewModel, screen, composables
```

### Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose (BOM 2024.06) |
| State | `StateFlow` + `combine` |
| Storage | DataStore Preferences |
| Async | Kotlin Coroutines |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 35 |

### Data flow

```
PackageManager ──► AppRepository ──► AppDrawerViewModel ──► AppDrawerScreen
DataStore      ──► PreferencesRepository ──►      │
ContactsContract ► ContactsRepository ──►         │
                                                   ▼
                                            HorizontalPager
                                           /               \
                                    HomeScreenPage    AppDrawerPage
```

The `uiState` StateFlow is built in two layers:

1. **`rawUiState`** — 5-flow `combine` on `Dispatchers.Default`: app list + preferences + selection state + raw search query + debounced search results
2. **`uiState`** — 2-flow `combine` layering clock + pins on top of `rawUiState`

This split ensures the 1-second clock tick never re-runs app filtering or sorting.

---

## Key technical decisions

**No icons, ever.** `loadIcon()` and `loadDrawable()` are never called. `queryIntentActivities` is used for app discovery, not `getInstalledPackages`. This eliminates an entire class of memory and threading problems.

**Duplicate package deduplication.** `queryIntentActivities` returns one result per launcher *Activity*, not per package. On OEM devices (e.g. Realme/OPPO) a single package can expose two launcher activities, producing duplicate keys in the `LazyColumn`. The fix: `.distinctBy { it.packageName }` in `AppRepository`.

**`TextFieldValue` over `String` in BasicTextField.** Using a plain `String` as the `BasicTextField` value resets the cursor to position 0 on every recomposition. The second typed character then inserts before the first. `TextFieldValue` is held in local Compose state; only the text string is forwarded to the ViewModel.

**Debounced search, split by cost.** App and settings filtering runs immediately (in-memory, negligible). Contact queries hit `ContentProvider`, so they are debounced 150 ms via a shared `StateFlow`. Both resolve together so results appear in one batch.

**Clock tick without re-filtering.** The clock emits every second. Combining it directly with the app-list state would re-run filtering/sorting every second. Instead, `rawUiState` builds the sorted app list independently; a second `combine` layer adds the clock output via `base.copy(...)`. Only the time strings are updated each tick.

**Contacts permission requested lazily.** `READ_CONTACTS` is declared in the manifest but never pre-requested. A `LaunchedEffect` inside the search page fires once when the query becomes non-empty and checks `checkSelfPermission`. If not granted, the system dialog appears. After approval, `contactsVersion` increments, which forces `flatMapLatest` to re-run the contact query with the current search text.

**`BackHandler` priority via LIFO.** `MainActivity` registers an always-enabled `OnBackPressedCallback` to swallow back presses (required for a home screen). Compose's `BackHandler` registers *after* that callback and therefore has higher priority (LIFO). When the search bar is active or the drawer is open, `BackHandler` intercepts the press instead.

---

## Roadmap

| Step | Feature |
|------|---------|
| 5 | Theming — Light / Dark / AMOLED, custom colors, font size, text alignment |
| 6 | Text widgets — weather (OpenWeatherMap) and next calendar event |
| 7 | Configurable gestures — remap swipe up/down/left/right/double-tap |
| 8 | Focus profiles — Work / Personal / Sleep with time-based scheduling |
| 9 | Usage tracking — per-app limits, time windows, friction screen, daily reports |
| 10 | Scratch pad, app lock (biometric/PIN), backup & restore, zero-telemetry audit |

---

## Permissions

All permissions are declared upfront and requested lazily — only when the feature that needs them is first used.

| Permission | Used for | When requested |
|-----------|---------|----------------|
| `QUERY_ALL_PACKAGES` | App discovery | On launch |
| `READ_CONTACTS` | Contact search | First search |
| `READ_CALENDAR` | Calendar widget | Step 6 |
| `PACKAGE_USAGE_STATS` | Usage tracking | Step 9 |
| `SCHEDULE_EXACT_ALARM` | Profile scheduling | Step 8 |
| `USE_BIOMETRIC` | App lock | Step 10 |
| `INTERNET` | Weather API | Step 6 |

---

## Building

```bash
# Clone and open in Android Studio, or build from the command line:
cd MinimalistLauncher
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug
```

Requires Android SDK with API 35 build tools. No external accounts or API keys needed for Steps 1–4.

---

## Contributing

Contributions are welcome when they preserve the launcher philosophy: text-only, distraction-free, and local-first.

Start with [CONTRIBUTING.md](CONTRIBUTING.md), use the issue templates for bugs and feature requests, and follow the [Code of Conduct](CODE_OF_CONDUCT.md). Before opening a pull request, run:

```bash
cd MinimalistLauncher
./gradlew check
```

For security issues, please follow [SECURITY.md](SECURITY.md) instead of opening a public issue.

---

## Project structure

```
core/
  data/
    AppRepository.kt          — app discovery, launch, dialer, settings, Google search
    ContactsRepository.kt     — contact search with permission guard
    PreferencesRepository.kt  — DataStore: sort order, hidden apps, launch counts,
                                clock format, 5 pinned slots
    SettingsActions.kt        — static map of 17 settings intents
  model/
    AppInfo.kt                — package name + label
    ClockFormat.kt            — HOUR_12 / HOUR_24
    PinnedItem.kt             — App(packageName, label) | Contact(name, number)
    SearchResult.kt           — App | Contact | Setting
    SortOrder.kt              — ALPHABETICAL | FREQUENCY

feature/appdrawer/
  AppDrawerUiState.kt         — single source of truth for all UI state
  AppDrawerViewModel.kt       — combines 7 upstream flows into one StateFlow
  AppDrawerScreen.kt          — HorizontalPager + all composables

app/
  LauncherApplication.kt      — lazy repository singletons + DataStore delegate
  MainActivity.kt             — single activity, back-press handling, onNewIntent
  ui/theme/                   — Material3 color scheme, typography
```

---

## License

MIT
