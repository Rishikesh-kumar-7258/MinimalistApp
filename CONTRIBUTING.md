# Contributing to Minimalist Launcher

Thank you for your interest in contributing. This document covers how to report bugs, suggest features, and submit code changes.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Reporting Bugs](#reporting-bugs)
- [Requesting Features](#requesting-features)
- [Development Setup](#development-setup)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Code Style](#code-style)
- [Commit Messages](#commit-messages)

---

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold it.

---

## Reporting Bugs

1. Search [existing issues](../../issues) to avoid duplicates.
2. Open a new issue using the **Bug report** template.
3. Include your Android version, device model (OEM launchers sometimes differ), and steps to reproduce.

---

## Requesting Features

1. Search [existing issues](../../issues) — someone may have asked already.
2. Open a new issue using the **Feature request** template.
3. Explain the use case, not just the desired behaviour. Features should align with the project's philosophy: **text-only, distraction-free, local-first**.

---

## Development Setup

### Requirements

- Android Studio Hedgehog or later
- Android SDK with API 35 build tools
- JDK 17 (bundled with Android Studio)
- A physical Android device or emulator running API 26+

### Build

```bash
git clone https://github.com/<your-username>/MinimalistLauncher.git
cd MinimalistLauncher/MinimalistLauncher
./gradlew assembleDebug
./gradlew installDebug   # installs on a connected device
```

### Project layout

```
MinimalistLauncher/
├── app/      — Activity, Application class, theme
├── core/     — Repositories, models, DataStore
└── feature/  — ViewModel, screens, composables
```

---

## Submitting a Pull Request

1. Fork the repo and create a branch from `main`:
   ```bash
   git checkout -b fix/your-description
   ```
2. Make your changes.
3. Run the full test suite and make sure the build is clean:
   ```bash
   ./gradlew check
   ```
4. Open a pull request against `main` using the provided template.
5. A maintainer will review within a few days. Be ready for feedback.

### What makes a good PR

- One logical change per PR — don't bundle unrelated fixes.
- If your change affects UI, include a screenshot or screen recording.
- Keep the diff focused. Avoid reformatting unrelated code in the same commit.

---

## Code Style

- **Kotlin** — follow the official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Compose** — one composable per file for screen-level components; group small helper composables in the same file as their parent.
- **No icons** — do not call `loadIcon()`, `loadDrawable()`, or render any `ImageView`/`Image` composable. The text-only constraint is a hard rule.
- **No analytics** — do not add any crash reporting, analytics, or telemetry libraries.
- Comments: only write one when the *why* is non-obvious. Skip comments that restate what the code already says.

---

## Commit Messages

Use the conventional commits format:

```
<type>(<scope>): <short description>

<optional body>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

Examples:
```
feat(search): debounce contact queries by 150 ms
fix(appdrawer): deduplicate OEM packages with duplicate launcher activities
docs: update README with Step 5 navigation gestures
```
