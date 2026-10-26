# Vibra — Architecture

Vibra is an independent Android music client built on top of
[ArchiveTune](https://github.com/rukamori/ArchiveTune) (commit `cdf8d6c`, 2026-10-20): its
**music engine is kept intact** and its **presentation layer is replaced** by a new UI
implemented from the Vibra Figma file.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ app.vibra.ui.*            NEW — Vibra presentation layer (this project)        │
│   shell/   VibraAppShell: NavHost + liquid dock + search overlay + player      │
│   dock/    VibraDock (profile · mini-player · search), liquid search           │
│   player/  VibraPlayerSheet, VibraQueuePanel, NowPlaying adapter              │
│   blocks/  relevance-sized widget blocks + packer (home, profile, library)     │
│   home/    VibraHomeScreen (Figma main-homepage, built from blocks)            │
│   library/ VibraLibraryScreen + habit-ordered library blocks                   │
│   profile/ VibraProfileScreen, edit mode (shape/colour), VibraStatsScreen      │
│   components/ VibraRow, VibraCard, VibraArtwork, buttons, seek bar, top bar    │
│   motion/  Liquid physics: springs, LiquidScope, GooSurface, LiquidSheet, …    │
│   haptics/ VibraHaptics (semantic haptic vocabulary)                           │
│   theme/   VibraColors / VibraType / VibraDimens+VibraShapes (design tokens)   │
│   navigation/ VibraItemActions (single open/play/menu policy for all items)    │
├──────────────────────────────────────────────────────────────────────────────┤
│ moe.rukamori.archivetune.* ENGINE — kept from ArchiveTune                      │
│   playback/  MusicService (Media3 MediaLibraryService), PlayerConnection,      │
│              queues, SleepTimer, downloads (ExoDownloadService), caching       │
│   db/        Room MusicDatabase (songs, albums, artists, playlists, events)    │
│   viewmodels/ Hilt ViewModels (home, search, library, album, playlists …)      │
│   ui/screens, ui/menu, ui/component — engine screens, re-skinned via shared    │
│              components (MediaDetailHero, ListItem) and the Vibra theme        │
│   core/ lyrics/ lastfm/ canvas/ shazamkit/ spotifycore/ — library modules      │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Principles

1. **One source of truth for playback.** The Media3 session in `MusicService` owns the
   player. The UI reads it through `PlayerConnection` (bound in `MainActivity`), converted
   to an immutable `NowPlaying` snapshot (`app/vibra/ui/player/NowPlaying.kt`). No Vibra
   component keeps its own copy of playback state; position is polled separately
   (`PlaybackPositionState`) so only the progress bar recomposes every 250 ms.
2. **Reuse, don't clone.** Vibra calls the engine's ViewModels, DAOs, queues, menus and
   dialogs directly. Where logic was duplicated or trapped inside a legacy composable it
   was *extracted* and shared rather than copied:
   - `ui/player/QueueReorder.kt` — drag-to-reorder semantics (legacy queue + Vibra queue).
   - `playback/SongDownloads.kt` — single-song download/remove/share (player menu + Vibra).
   - `PlayerMenu(onCollapsePlayer = …)` — decoupled from the legacy bottom-sheet state.
   - `VibraItemActions` — one open/play/menu policy for every song/album/artist/playlist.
   - `DeviceMusicVolumeController.kt` — moved out of the deleted legacy player.

   The replaced legacy UI (old home, bottom-sheet player, mini-player, legacy queue,
   floating navigation bar, TV rail; ~5,980 lines) was deleted rather than left as dead
   code. `PlayerComponents.kt`/`Thumbnail.kt` remain because lyrics and settings
   previews still use parts of them.
3. **Re-skin shared components instead of rewriting screens.** `MediaDetailHero`
   (album, playlists, artist, podcasts…) and the base `ListItem` (≈40 list screens) keep
   their APIs but render the Figma design, so every inherited screen adopts the design
   without behavioural changes.
4. **Design tokens are centralised** (`app/vibra/ui/theme`). The default theme is the
   exact Figma scheme (`VibraDarkColorScheme`); album-art/custom palettes remain opt-in.

## The app frame

`MainActivity` keeps all of its lifecycle work (service binding, intents/deep links,
backup restore, onboarding, update checks) and, after onboarding,
renders:

```
CompositionLocalProvider(engine locals)
  └─ VibraAppShell
       ├─ NavHost  (navigationBuilder() engine routes + Vibra routes)
       ├─ search overlay (OnlineSearchScreen, shown while the dock is in Search mode)
       ├─ VibraDock (floating, above IME + navigation bar)
       └─ VibraPlayerSheet (grows out of the dock's mini-player)
  ├─ BottomSheetMenu / BottomSheetPage (engine menus and pages)
  └─ NetworkStatusBanner
```

Routes replaced/added: `home` → `VibraHomeScreen`; `vibra_profile` → `VibraProfileScreen`;
`library` gets a `VibraTopBar` (it relied on ArchiveTune's global app bar). Every other
engine route is unchanged.

## Liquid motion engine (`app/vibra/ui/motion`)

| Piece | What it does | Used by |
|---|---|---|
| `Liquid.spatial/effects(speed)` | Typed access to the M3 Expressive spring tokens from `MaterialTheme.motionScheme` (expressive scheme, or an instant scheme when the user disables animations). | everywhere |
| `LiquidScope { Modifier.liquid() }` | Lookahead-based layout morphing: tagged children spring to new bounds whenever the layout changes (sizes, spacing, slots appearing). | dock |
| `GooSurface` / `gooBlob()` | Metaball renderer: pills are drawn into one layer, blurred and alpha-thresholded (RenderEffect, API 31+) so neighbours melt together while moving; graceful fallback below API 31. Drop shadow via blur mask. | dock |
| `LiquidSheetState` | 0‥1 progress driven by drags *and* springs with velocity hand-off; predictive-back preview. | player, queue |
| `LiquidMorphShape` / `rememberLiquidMorphShape` | Spring-animated `Morph` between Material shapes, with rotation. | play button |
| `liquidPress()` | Elastic press "squish". | buttons, dock |

Reduced motion: one switch. Vibra's "disable animations" setting swaps the motion scheme
for an instant one (all `Liquid.*` springs snap), the cookie play button stops rotating,
and Android's system animator scale is honoured by Compose itself.

## Haptics (`app/vibra/ui/haptics/VibraHaptics.kt`)

Semantic calls (`press`, `toggle(on)`, `tick`, `threshold`, `gestureEnd`, `longPress`,
`confirm`, `reject`) mapped to Compose `HapticFeedbackType` primitives; gated by the
existing "Haptic feedback" preference (`MainActivity` wraps `LocalHapticFeedback`).

| Moment | Haptic |
|---|---|
| Dock/primary button tap | `press` |
| Play/pause, like, shuffle, open search | `toggle(on)` |
| Scrubbing the seek bar (every 10 %), reordering past a slot, cycling repeat | `tick` |
| Mini-player swipe crosses the skip point; pull-to-refresh | `threshold` |
| Skip committed, seek released, queue item dropped | `gestureEnd` |
| Grabbing the seek bar / a queue item, opening a context menu | `longPress` |
| Added to playlist, download started, sleep timer set | `confirm` |
| Playback error, nothing to play | `reject` |

## Project identity

* applicationId `vibra.music.com` (debug: `vibra.music.com.debug`), versionName `1.0.0`.
* Kotlin namespace stays `moe.rukamori.archivetune` for the engine (renaming ~640 files
  would be risky churn with no user benefit); new code lives in `app.vibra.*`.
* Upstream-hosted services are disconnected: updater, gatekeeper/data server, Music
  Together relay. Discord integration and the always-on-display mode were removed.
* Upstream signing keystore, CI workflows, store metadata, icon pack and Apple's
  SF Pro font are not shipped. See `docs/SETUP.md` and `docs/FINAL_REPORT.md`.
