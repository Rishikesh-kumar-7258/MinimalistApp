# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.x (latest) | Yes |

## Scope

Minimalist Launcher is a local-only Android home screen replacement. It has no backend, no cloud sync, and no telemetry. The attack surface is intentionally minimal:

- All data stays on-device (DataStore preferences, local backup files).
- No network requests are made by the launcher itself. The weather widget (Step 6+) contacts OpenWeatherMap using a user-supplied API key stored only in DataStore.
- No analytics or crash-reporting SDKs are included.

## Reporting a Vulnerability

If you find a security vulnerability, please **do not open a public GitHub issue**.

Instead, report it via GitHub's private vulnerability reporting:

1. Go to the repository on GitHub.
2. Click **Security** → **Advisories** → **Report a vulnerability**.
3. Describe the issue, steps to reproduce, and potential impact.

You can expect an acknowledgement within **72 hours** and a fix or mitigation plan within **14 days** for confirmed issues.

## Out of Scope

- Issues in Android OS itself or third-party libraries that are not exploitable through this app.
- Theoretical issues with no practical exploit path.
- Issues only reproducible on rooted devices performing privileged operations.
