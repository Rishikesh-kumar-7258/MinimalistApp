# Minimalist Launcher — Architecture & Bug Analysis

## 1. Application Overview

A minimal Android home-screen replacement built with Jetpack Compose. The app shows an
alphabetical (or frequency-sorted) list of installed launcher apps. Long-pressing an app opens
a bottom-sheet with actions (hide, pin, etc.). It registers itself as a HOME-category activity
so Android can offer it as the default launcher.

---

## 2. Module Structure

```
MinimalistLauncher/
├── app/          — Application entry point (MainActivity, LauncherApplication, theme)
├── core/         — Data layer (AppRepository, PreferencesRepository, models)
└── feature/      — UI layer (AppDrawerScreen, AppDrawerViewModel, AppDrawerUiState)
```

---

## 3. Components & Data Flow

```
[Android OS]
    │  queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)
    ▼
[AppRepository]          ← core module
    │  getInstalledApps() → List<AppInfo>
    │  launch(packageName)
    ▼
[AppDrawerViewModel]     ← feature module
    │  combines: allApps + hiddenApps + sortOrder + launchCounts + selectedApp
    │  emits: AppDrawerUiState (via StateFlow)
    ▼
[AppDrawerScreen]        ← feature module (Compose UI)
    │  SortToggleRow  — a-z / freq toggle
    │  AppList        — LazyColumn of app labels
    │  AppOptionsSheet — bottom sheet on long-press
    ▼
[PreferencesRepository]  ← core module (DataStore)
    │  hiddenApps, sortOrder, launchCounts
```

### LauncherApplication
- Custom `Application` subclass.
- Creates `AppRepository` and `PreferencesRepository` via lazy delegates.
- Provides a single `dataStore` instance using `preferencesDataStore`.

### MainActivity
- Single activity; `launchMode="singleTask"` so pressing Home always returns to the
  existing instance instead of stacking.
- Registers as `CATEGORY_HOME` so Android offers it as a home-screen replacement.
- Creates `AppDrawerViewModel` via `viewModels {}` factory.
- Calls `promptSetDefaultLauncherIfNeeded()` to ask the user to set it as default.

### AppRepository (`core`)
- `getInstalledApps()` — queries `PackageManager` for all activities that respond to
  `ACTION_MAIN + CATEGORY_LAUNCHER`, maps them to `AppInfo(packageName, label)`.
- `launch(packageName)` — gets the launch intent and starts the activity.

### PreferencesRepository (`core`)
- Wraps `DataStore<Preferences>`.
- Persists: `hiddenApps` (StringSet), `sortOrder` (String), `launchCounts` (StringSet
  encoded as `"packageName:count"` pairs).

### AppDrawerViewModel (`feature`)
- Loads apps on `Dispatchers.IO` at init time.
- `combine`s the app list with preferences to produce `AppDrawerUiState`.
- Exposes a `StateFlow<AppDrawerUiState>` consumed by the Compose UI.

### AppDrawerUiState (`feature`)
```kotlin
data class AppDrawerUiState(
    val apps: List<AppInfo>,      // visible + sorted
    val sortOrder: SortOrder,
    val isLoading: Boolean,
    val selectedApp: AppInfo?,    // drives bottom-sheet
    val error: Throwable?,        // load failure
)
```

### AppDrawerScreen (`feature`)
- Observes `uiState` via `collectAsStateWithLifecycle()`.
- Renders: sort toggle → loading/empty/list state → optional bottom-sheet.
- `LazyColumn` uses `app.packageName` as the item key.

---

## 4. Problems Found

### BUG-1 ✦ CRASH — Duplicate package names in app list  *(root cause of "keeps stopping")*

**File:** `core/src/main/kotlin/…/core/data/AppRepository.kt`

**What happens:**  
`queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER, 0)` returns one `ResolveInfo`
**per launcher Activity**, not per package. Many OEM apps (e.g. `com.heytap.market` on
OPPO/Realme devices) expose two or more launcher activities inside the same package.
`getInstalledApps()` maps each `ResolveInfo` directly to `AppInfo`, so the same package
name can appear two or more times in the returned list.

`LazyColumn` (in `AppDrawerScreen`) uses `{ it.packageName }` as the compose key.
When it encounters the same key twice it throws:

```
java.lang.IllegalArgumentException: Key "com.heytap.market" was already used.
```

This is the fatal exception that crashes the app on every launch.

**Fix:** Deduplicate by `packageName` inside `getInstalledApps()` using `distinctBy`.

---

### BUG-2 — Blocking Binder call on the main thread (ANR risk)

**File:** `app/src/main/kotlin/…/MainActivity.kt`

**What happens:**  
The original `promptSetDefaultLauncherIfNeeded()` called `roleManager.isRoleHeld()` and
`createRequestRoleIntent()` synchronously on the main thread. Both are Binder IPC calls
to `system_server`. On a loaded device these can take tens of milliseconds and trigger
an ANR watchdog.

**Fix:** Move the Binder calls inside `withContext(Dispatchers.IO)` inside a
`lifecycleScope.launch {}` block.

---

### BUG-3 — NullPointerException on roleManager

**File:** `app/src/main/kotlin/…/MainActivity.kt`

**What happens:**  
`getSystemService(RoleManager::class.java)` is declared `@Nullable` in AOSP. Calling
`.isRoleHeld()` on a null reference throws `NullPointerException`. While uncommon on
standard AOSP devices, it can happen on certain custom ROMs.

**Fix:** Null-check `roleManager` before calling any method on it.

---

### BUG-4 — Uncaught exception crashes the app via viewModelScope

**File:** `feature/src/main/kotlin/…/AppDrawerViewModel.kt`

**What happens:**  
`viewModelScope.launch(Dispatchers.IO) { ... }` had no `catch` block. Any unhandled
`Throwable` thrown by `appRepository.getInstalledApps()` propagates through the
`SupervisorJob` and reaches the thread's default uncaught exception handler, which
terminates the process.

**Fix:** Wrap the body in `try { … } catch (e: Throwable) { … }` and store the error
in `loadError` state so the UI can react gracefully.

---

### BUG-5 — launchCounts parsing crash on malformed data

**File:** `core/src/main/kotlin/…/PreferencesRepository.kt`

**What happens:**  
The original parser used `.associate { entry.substring(0, entry.indexOf(':')) … }`.
If an entry has no colon, `indexOf(':')` returns `-1`, and `substring(0, -1)` throws
`StringIndexOutOfBoundsException` on the DataStore IO coroutine, which again surfaces
as an uncaught exception.

**Fix:** Replace `.associate` with `.mapNotNull` and guard with `if (colon >= 0)`.

---

### BUG-6 — Error state captured but never shown in the UI

**File:** `feature/src/main/kotlin/…/AppDrawerScreen.kt`

**What happens:**  
`AppDrawerUiState.error` is populated when app loading fails, but `AppDrawerScreen`
has no branch for it. The user sees "no apps found" with no indication something went
wrong and no way to retry.

**Fix:** Add an error branch in the `when` block and a retry callback through the
ViewModel.

---

### BUG-7 — Activity recreated on configuration changes (rotation, font size, etc.)

**File:** `app/src/main/AndroidManifest.xml`

**What happens:**  
Without `android:configChanges`, every screen rotation destroys and recreates
`MainActivity`. This re-triggers `promptSetDefaultLauncherIfNeeded()` which pops the
"Set as default launcher" dialog on every rotation — terrible UX for a home screen.

**Fix:** Declare `android:configChanges` to handle these changes in-process without
recreating the Activity.

---

## 5. Solutions Applied

| # | Severity | File | Change |
|---|----------|------|--------|
| BUG-1 | **CRASH** | `AppRepository.kt` | Add `.distinctBy { it.packageName }` after `mapNotNull` |
| BUG-2 | HIGH | `MainActivity.kt` | Move Binder calls into `withContext(Dispatchers.IO)` |
| BUG-3 | HIGH | `MainActivity.kt` | Null-check `roleManager` |
| BUG-4 | HIGH | `AppDrawerViewModel.kt` | Add `catch (e: Throwable)` in init coroutine |
| BUG-5 | MEDIUM | `PreferencesRepository.kt` | Replace `.associate` with guarded `.mapNotNull` |
| BUG-6 | LOW | `AppDrawerScreen.kt` | Add error branch + retry in UI |
| BUG-7 | LOW | `AndroidManifest.xml` | Add `android:configChanges` |
