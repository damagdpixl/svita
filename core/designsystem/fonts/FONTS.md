# Fonts

Bundled in `src/main/res/font/` (renamed to lowercase resource names):

| File | Family | Source | License |
| --- | --- | --- | --- |
| `playfair_display_variable.ttf` | Playfair Display (variable, wght axis) | [google/fonts, OFL/playfairdisplay](https://github.com/google/fonts/tree/main/ofl/playfairdisplay) | SIL Open Font License 1.1 — see `OFL-PlayfairDisplay.txt` |
| `jetbrains_mono_regular.ttf` | JetBrains Mono Regular | [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono/tree/master/fonts/ttf) | SIL Open Font License 1.1 — see `OFL-JetBrainsMono.txt` |
| `jetbrains_mono_medium.ttf` | JetBrains Mono Medium | same | same |
| `jetbrains_mono_bold.ttf` | JetBrains Mono Bold | same | same |

Both licenses are OFL 1.1 and are committed in this folder (deliberately NOT under
`res/` so they ship as repo documentation, not Android resources).

Usage in the «Editorial Collage» design system:
- Playfair Display — headlines (serif voice).
- JetBrains Mono — controls, labels, buttons (uppercase mono voice).
