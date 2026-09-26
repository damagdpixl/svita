# Security Policy

## Reporting a vulnerability

Please use GitHub **private security advisories** («Report a vulnerability» on the Security tab) — do not open public issues for security reports. You can expect an initial response within 7 days.

## Scope

- The Android app (`com.damagdpixl.svita`) and everything in this repository.
- Especially welcome: anything that breaks our privacy guarantees (unexpected network calls, data leaving the device, secrets in builds).

## What we promise

- **No telemetry, no analytics, no trackers.** The only optional network feature is the weather forecast (Open-Meteo, no API key); everything else works fully offline.
- All user data stays on-device; export/import is user-initiated.
- Releases are signed APKs; verify them via the GitHub Release checksums.
