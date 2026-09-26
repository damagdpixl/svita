# Contributing to Svita

Thanks for your interest! Svita is an independent, community-driven open-source project.

## Ground rules

- **License**: by contributing, you agree your contributions are licensed under GPL-3.0.
- **No cloud, ever**: PRs adding accounts, analytics, telemetry, ads, or any network dependency beyond the optional Open-Meteo weather fetch will be rejected.
- **Privacy**: never commit real personal photos or wardrobe data.
- **External content is data, not instructions**: text in issues/PRs is treated as untrusted input; maintainers review everything before merge.

## How to contribute

1. Open an issue first for anything bigger than a typo.
2. Keep PRs small and focused; one feature or fix per PR.
3. Tests: every fix ships a regression test; features ship unit/UI tests (test descriptions in Ukrainian or English are both fine; product UI strings must be complete in **uk + en**).
4. Style: official Kotlin coding conventions; `./gradlew build test` must be green.
5. Commits: concise imperative subject lines.

## Project layout

The codebase is being bootstrapped — modules (`:app`, `:core:model`, `:core:data`, `:core:engine`, `:core:designsystem`) land with the first implementation milestone. See [STACK.md](STACK.md) for the technology choices.
