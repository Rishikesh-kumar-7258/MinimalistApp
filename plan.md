# Minimalist Launcher — Implementation Plan

## Stack
- **Platform:** Android (Kotlin)
- **Min SDK:** API 26 (Android 8.0) — covers ~95% of active devices
- **UI:** Jetpack Compose (text-only, no icon rendering needed)
- **Storage:** Room (usage data) + DataStore (settings/preferences)
- **Build:** Gradle with version catalogs

---

## Step 1 — Project Scaffold & Launcher Registration
**Features covered:** core shell

Register the app as a home screen replacement. This is the foundation everything else sits on.

- Create a new Android project with an empty Compose activity
- Declare `android.intent.category.HOME` and `android.intent.category.DEFAULT` in `AndroidManifest.xml` so the OS offers it as a launcher
- Handle the "set as default" prompt gracefully on first launch
- Set up the Gradle module structure: `app`, `core`, `feature` modules for clean separation
- Add all required permissions upfront: `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `READ_CONTACTS`, `BIND_ACCESSIBILITY_SERVICE` (for usage tracking)

**Exit criteria:** tapping Home opens this app; back button does nothing on the home screen.

---

## Step 2 — App Drawer & Text-Only Rendering
**Features covered:** #2 (no icons), #6 (app drawer), #13 (hidden apps)

Build the core app list — the backbone of the launcher.

- Query `PackageManager` for all launchable apps; strip icons entirely
- Render apps as plain text using a `LazyColumn` in Compose
- Implement alphabetical sorting and frequency-based sorting (toggle in settings)
- Add a hidden apps list: hidden apps are stored in DataStore and filtered out at query time
- Long-press on any app name opens a bottom sheet with actions: Pin, Hide, Group, Restrict

**Exit criteria:** all installed apps visible as text; tapping launches them; long-press menu works.

---

## Step 3 — Unified Search Bar
**Features covered:** #3 (search), #10 (pull-down search)

Search is the primary navigation method — make it fast and frictionless.

- Place a single text input at the top of the home screen
- Filter the app list in real time as the user types (no button press needed)
- Extend search to contacts via `ContactsContract` (name → open dialer with number)
- Extend search to settings via a static map of common settings intents (Wi-Fi, Bluetooth, Display, etc.)
- Implement pull-down gesture anywhere on the home screen to focus the search bar
- Auto-clear search on home press or app launch

**Exit criteria:** typing filters apps + contacts + settings simultaneously; pull-down opens keyboard.

---

## Step 4 — Home Screen Layout & Pinned Shortcuts
**Features covered:** #1 (no wallpaper), #8 (pinnable shortcuts), #9 (text widgets — clock/date)

Build the home screen canvas that users see every time they unlock their phone.

- Enforce a solid background color (no wallpaper API calls); background color is user-configured
- Display clock and date as plain text at the top using `DateTimeFormatter`; support 12h/24h toggle
- Implement pinned shortcuts: up to 5 slots, each storing a package name or contact; rendered as plain text labels
- Long-press a pinned slot to replace or remove it
- Store all home screen layout state in DataStore

**Exit criteria:** home screen shows clock, date, and pinned items as text only; no images rendered.

---

## Step 5 — Theming & Customization
**Features covered:** #4 (minimalist design), #5 (color scheme), #24–#28 (font, size, alignment, themes)

Let users make the interface feel like their own without breaking the minimal aesthetic.

- Build a Settings screen with sections: Appearance, Behavior, Permissions
- Color picker for background, text, and accent — constrain to hex input + a curated palette of 12 safe combinations to prevent unreadable choices
- Font selector: ship 3 bundled fonts (system default, a clean serif, a clean sans-serif) — no internet required
- Font size toggle: Small / Medium / Large (maps to Compose `TextStyle` sp values)
- Text alignment: Left / Center / Right — applied globally to all home screen text
- Theme mode: Light / Dark / AMOLED (AMOLED forces pure `#000000` background for OLED power saving)

**Exit criteria:** all appearance changes apply immediately without restart; AMOLED mode renders pure black background.

---

## Step 6 — Text Widgets (Weather & Calendar)
**Features covered:** #9 (weather, calendar event widgets)

Pure-text information at a glance — no cards, no icons, no images.

- **Weather:** prompt user to enter an OpenWeatherMap API key (stored in encrypted DataStore); fetch on a `WorkManager` periodic task every 30 min; display as a single text line: `18°C  Partly cloudy`
- **Calendar:** read the next upcoming event from `CalendarContract`; display as a single text line: `3pm — Team sync`; requires `READ_CALENDAR` permission
- Both widgets are individually toggleable in Settings
- If no API key is set, the weather line is hidden rather than showing an error

**Exit criteria:** weather and calendar lines appear on home screen as plain text; update without user action.

---

## Step 7 — Gestures & Navigation
**Features covered:** #21 (configurable swipe gestures), #22 (pull-down search), #23 (long-press menu)

Replace tap-heavy navigation with fluid gestures — the clearest UX differentiator.

- Implement a `GestureDetector` over the home screen that captures: swipe up, swipe down, swipe left, swipe right, double-tap
- Each gesture maps to a configurable action: Open App Drawer, Open Search, Open Dialer, Open Scratch Pad, Open Recent Apps, or a specific installed app
- Defaults: swipe up → App Drawer, swipe down → Search, double-tap → Scratch Pad
- Store gesture-to-action mappings in DataStore
- Pull-down search (from Step 3) becomes the default for swipe down — consistent with expectation

**Exit criteria:** all 5 gestures trigger correct actions; user can remap each in Settings.

---

## Step 8 — Focus Profiles & Scheduling
**Features covered:** #14 (focus profiles), #15 (profile scheduling), #31 (emergency bypass)

The layer that makes this a digital wellbeing tool, not just a launcher.

- Define 4 profiles: Work, Personal, Sleep, Custom — each stores an allow-list or block-list of apps
- Profile switcher accessible via a long-press on the home screen or a swipe gesture action
- Schedule each profile with start/end times using `AlarmManager` with `SCHEDULE_EXACT_ALARM` permission; profiles auto-activate silently
- Emergency bypass: a hardcoded whitelist (Phone, Messages, Maps) that is never blocked by any profile — stored separately from profile config and not user-editable for safety
- Active profile name shown as a subtle text line at the bottom of the home screen

**Exit criteria:** switching profiles changes which apps are accessible; scheduled switches happen automatically; emergency apps always open.

---

## Step 9 — Usage Tracking, Restrictions & Friction Screens
**Features covered:** #7 (usage tracking + restriction), #16 (per-app time windows), #17 (friction screen), #18–#20 (daily report, weekly trends, streaks)

The most technically complex step — requires Accessibility Service or UsageStatsManager.

- Use `UsageStatsManager` (`PACKAGE_USAGE_STATS` permission) to query foreground time per app; store daily snapshots in Room
- Per-app daily limits: when a limit is reached, intercept the next launch and show the friction screen (a full-screen plain text message with a 5-second countdown before allowing entry or a "go back" button)
- Per-app time windows: store allowed time ranges per app; check current time on every launch; block outside the window with the same friction screen
- Friction screen copy is user-editable (default: "Take a breath. Do you really need this right now?")
- Daily report: a summary screen showing time per app for today, triggered at a user-set time via notification
- Weekly trends: compare this week vs. last week per app using stored Room data
- Usage streaks: track consecutive days the user stays under their total screen time goal; store streak count in DataStore

**Exit criteria:** daily limits enforced; time windows block apps outside their slot; friction screen appears; daily report notification fires.

---

## Step 10 — Utilities, Privacy & Release Polish
**Features covered:** #29 (scratch pad), #30 (app lock), #32–#34 (zero telemetry, no internet, backup/restore)

Harden the app, close the remaining feature gaps, and prepare for release.

- **Scratch pad:** a plain `TextField` stored in DataStore (single note, auto-saved on every keystroke); opened via gesture or pinned shortcut; no formatting, no sync
- **App lock:** use `BiometricPrompt` API to gate specific apps; fallback to a 4-digit PIN stored as a bcrypt hash in encrypted DataStore; lock is checked before the app launches (intercepted at the launcher level)
- **Zero telemetry:** audit all dependencies for network calls; add a `NetworkSecurityConfig` that blocks all cleartext and restricts the app's own network access to the weather API domain only
- **Backup & restore:** serialize all DataStore preferences + Room usage history to a single JSON file; let user pick a save location via `Storage Access Framework`; import reverses the process
- **Final QA pass:** test on a physical OLED device for AMOLED mode; verify all permissions are requested lazily (only when the feature is first used); confirm the app passes Android's battery optimization checks; write a short `README` with setup instructions for the weather API key

**Exit criteria:** app passes review checklist; backup file round-trips correctly; no network calls observed in a proxy trace except to the weather API.
