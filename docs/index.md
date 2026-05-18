# Minimalist Launcher

A personal Android launcher built around one rule: if it can't be expressed as text, it doesn't belong on your home screen.

No icons. No wallpaper. No notification dots. Every app, contact, and setting is a word you read and tap. What's left is a phone that responds to intent rather than habit.

Built for personal use — not a product, not a platform. Focus profiles, per-app time limits, gesture-mapped navigation, and a friction screen are built in because the phone is the distraction, and the launcher is the first line of defense.

---

## Core Experience

- Text-only interface — no app icons, ever
- No wallpaper — solid background only
- Unified search bar — searches apps, contacts, and settings in one place
- App drawer — scrollable, alphabetical or frequency-sorted text list
- Customizable color scheme, font size, and text alignment

---

## Home Screen

- Large clock — tap to toggle 12h / 24h format
- Date displayed below the clock
- Up to 5 pinned shortcuts — apps or contacts, shown as plain text
- Swipe left to open app drawer, swipe up for Google Search

---

## Focus & Digital Wellbeing

- **Focus profiles** — Work, Personal, Sleep, and Custom modes that restrict or allow specific apps
- **Profile scheduling** — automatically switch profiles on a time schedule
- **Per-app time windows** — allow an app only within a defined daily time slot (e.g. Reddit 8pm–9pm)
- **Friction screen** — a brief mandatory pause before opening designated apps, replacing hard blocks
- **Daily usage report** — plain text summary of screen time per app
- **Weekly trends** — 7-day history with comparison to the prior week
- **Usage streaks** — track consecutive days under a screen time goal

---

## Gestures & Navigation

| Gesture | Action |
|---|---|
| Swipe left | Open app drawer |
| Swipe right | Return to home screen |
| Swipe up (home screen) | Google Search |
| Pull down (app list) | Focus search bar |
| Tap app | Launch |
| Long-press app | Pin · Hide · Group · Restrict |
| Tap clock | Toggle 12h / 24h |
| Long-press pinned item | Remove shortcut |

All swipe gestures are configurable — remap any direction or double-tap to any action.

---

## Organization

- **App groups** — label apps into categories (Work, Social, Tools) as collapsible text sections
- **Hidden apps** — hide apps from the drawer without uninstalling
- **Frequency sorting** — optionally sort by most-used

---

## Customization

- Light, Dark, and AMOLED black themes
- Font selection from a curated set of clean system fonts
- Font size — small / medium / large
- Text alignment — left, center, or right

---

## Utilities

- **Scratch pad** — single tap to open a plain-text note from the home screen, saves automatically
- **App lock** — lock specific apps behind PIN or biometric authentication
- **Emergency bypass** — essential apps (dialer, messages, maps) are never restricted

---

## Privacy

- Zero telemetry — no analytics, no crash reporting sent off-device
- All data stays on device
- Weather uses a user-configured API key stored locally
- Backup and restore to a local file — no cloud sync required

---

## Built With

| | |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose |
| Storage | DataStore Preferences |
| Min Android | 8.0 (API 26) |

---

[View source on GitHub](https://github.com/Rishikesh-kumar-7258/MinimalistApp)
