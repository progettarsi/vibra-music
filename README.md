# Vibra

An Android music player with a fluid, "liquid" Material 3 Expressive interface designed
in Figma — a floating dock whose search bar grows out of the search button, a player that
swells out of the mini-player, and spring physics, gooey surfaces and haptics throughout.

Vibra is an **independent fork** of [ArchiveTune](https://github.com/rukamori/ArchiveTune)
(GPL-3.0) and keeps its music engine: YouTube Music streaming and library, local files,
downloads, synced lyrics, statistics, normalization, equalizer and more. It is not
affiliated with or endorsed by the ArchiveTune project.

## Quick start

```bash
./gradlew :app:assembleGmsMobileUniversalDebug
adb install -r app/build/outputs/apk/gmsMobileUniversal/debug/app-gms-mobile-universal-debug.apk
```

Requirements (JDK 21, Android SDK 37) and optional integrations: [`docs/SETUP.md`](docs/SETUP.md).

## Documentation

| Document | Contents |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Engine vs. presentation layer, the liquid motion engine, haptics |
| [`docs/FIGMA_IMPLEMENTATION_MAP.md`](docs/FIGMA_IMPLEMENTATION_MAP.md) | Every Figma frame/token → code |
| [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md) | Feature-by-feature status and verification |
| [`docs/QA_REPORT.md`](docs/QA_REPORT.md) | Test environment, executed journeys, defects, gaps |
| [`docs/FINAL_REPORT.md`](docs/FINAL_REPORT.md) | Final audit and readiness verdict |
| [`docs/upstream/`](docs/upstream) | ArchiveTune's original README, privacy policy and contribution policy (kept for attribution) |

## Licence and attribution

Vibra is licensed under the **GNU General Public License v3.0** (see [`LICENSE`](LICENSE)).
It contains code © Rukamori and the ArchiveTune contributors; their copyright notices are
preserved in every engine source file. Bundled fonts (Roboto, Google Sans Flex) are under
the SIL Open Font License 1.1 (`app/src/main/assets/licenses`). Third-party library
licences are listed in the app under *Settings → About → Licenses*.

Vibra is an independent project: please do not send Vibra's changes to the ArchiveTune repositories.
