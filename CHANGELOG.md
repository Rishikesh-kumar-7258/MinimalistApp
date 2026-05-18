# Changelog

All notable changes to Minimalist Launcher are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned
- Step 11 — App lock (biometric/PIN), backup & restore, zero-telemetry audit

---

## [0.10.0] — 2026-05-19

### Added
- Step 10: Scratch pad — plain-text note accessible from home screen, auto-saved
- App lock — lock specific apps behind PIN or biometric authentication
- Backup & restore — export and import all settings to/from a local JSON file
- Zero-telemetry audit — verified no analytics or crash data leaves the device

---

## [0.9.0]

### Added
- Step 9: Usage tracking — per-app daily limits, time windows (restrict to specific hours), friction screen, daily usage report

---

## [0.8.0]

### Added
- Step 8: Focus profiles — Work / Personal / Sleep / Custom profiles; time-based automatic switching via `SCHEDULE_EXACT_ALARM`

---

## [0.7.0]

### Added
- Step 7: Configurable gestures — remap swipe up / down / left / right / double-tap on home screen

---

## [0.6.0]

### Added
- Step 6: Text widgets — current weather via OpenWeatherMap (user-supplied API key) and next calendar event; both rendered as plain text with no icons

---

## [0.5.0]

### Added
- Step 5: Theming — Light / Dark / AMOLED modes, custom background and text colors, font size, text alignment

---

## [0.4.0]

### Added
- Step 4: Home screen with large clock (tappable to toggle 12 h / 24 h), date line, and up to 5 pinned shortcuts
- Horizontal pager navigation: home screen ↔ app drawer
- Swipe up on home screen opens Google Search

---

## [0.3.0]

### Added
- Step 3: Unified search bar — filters apps, contacts, and settings simultaneously
- Contacts search via `ContactsContract`; `READ_CONTACTS` requested lazily on first search
- 17 common settings actions (Wi-Fi, Bluetooth, Display, etc.)
- Debounced contact queries (150 ms) to avoid hammering `ContentProvider`

---

## [0.2.0]

### Added
- Step 2: App drawer — `LazyColumn` of all installed apps, alphabetical and frequency-based sort
- Long-press context menu: pin, hide, group, restrict
- Hidden apps stored in DataStore; filtered at query time

### Fixed
- Crash on OEM devices (Realme/OPPO) caused by duplicate launcher activities for the same package — resolved with `.distinctBy { it.packageName }` in `AppRepository`
- Cursor reset to position 0 on every keystroke — resolved by using `TextFieldValue` instead of `String` in `BasicTextField`

---

## [0.1.0]

### Added
- Step 1: Launcher registration — app registers as Android home screen replacement
- Back button swallowed on home screen via `OnBackPressedCallback`
- Three-module Gradle project: `app`, `core`, `feature`
