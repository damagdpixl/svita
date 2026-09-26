# Svita — Stack

Single-language product: **Kotlin**. Python exists only in the private research workspace as a parity-test oracle — it is never shipped.

| Layer | Chosen | Why |
|---|---|---|
| Platform | Android, Kotlin + Jetpack Compose (KMP-ready libraries) | native ML runtimes, best OSS/F-Droid story, free path to iOS via Compose Multiplatform later |
| UI design system | Material 3 base + custom «Editorial Collage» design language | a distinctive editorial-collage look: cream/charcoal palette, serif headlines, monospace controls, sticker accents, manifesto-tile empty states |
| Data | SQLDelight (SQLite) | KMP-ready, SQL-first, migration tests |
| Serialization | kotlinx.serialization | KMP-ready, stable schemas for export/import |
| Images | Coil 3 | Compose-native |
| Dress-me engine | pure Kotlin module (`:core:engine`, no Android deps) | testable, portable; validated against a private reference implementation (909-check matrix, −10…+35 °C) |
| ML (v0.2) | ONNX Runtime Mobile (NNAPI delegate) | background removal (U2-Net int8) + attribute tagging |
| Weather | Open-Meteo + bundled climate norms | no API key, works offline |
| DI | manual constructor injection | small surface; revisit if it grows |
| Build/CI | Gradle KTS + version catalog; GitHub Actions (Temurin 17) | standard, reproducible |
| Tests | JUnit/kotlin-test + Robolectric + Compose testing | layered: parity, migrations, repositories, UI, offline smoke |

Fonts: OFL-licensed with Cyrillic support (serif for headlines, monospace for controls).
