# vibra

**Music that moves like liquid.**

vibra is a music app for Android with a fluid, expressive interface: a floating dock whose
search bar grows out of the search button, a player that swells out of the mini-player,
covers that bleed into the whole screen, and spring physics, gooey surfaces and haptics
everywhere. It plays YouTube Music and your local files.

vibra is built on top of [ArchiveTune](https://github.com/rukamori/ArchiveTune): its music
engine runs underneath, while the interface is all new, designed in Figma on Material 3
Expressive.

## What matters here

vibra is built around **how it feels to use**, not around a list of features. Every screen,
transition and touch is designed and tuned by hand: if something moves, it moves on a spring;
if something changes, it changes in place instead of blinking; if you touch something, it
answers. A feature only ships when it is as smooth and coherent as the rest of the app, so
vibra deliberately does fewer things, and does them well.

## Highlights

**An interface that flows**
- The dock holds your profile, the mini-player and search. They merge and split like drops
  of liquid, and search grows out of its own button.
- The full player opens out of the mini-player and goes back into it, following your
  finger. You can grab it and throw it at any point.
- Every page opens from the thing you tapped and goes back the way it came. Predictive back
  follows your thumb.
- All movement runs on springs. Shapes carry meaning (spiky for energy, soft for calm, a
  star for favourites). Vibrations mark taps, toggles and thresholds.

**The player**
- A full-width cover that fades into a backdrop made from its own colours.
- Music videos play in place of the cover, muted and in sync with the song. The backdrop
  follows the video frame by frame.
- Swipe the cover to change track. The seek bar ripples while the music plays. The sleep
  timer shows the time left.
- Synced lyrics fill word by word and follow the song. If you scroll away, they glide back
  to the line being sung.

**Home, library and profile**
- Pages are built from blocks, and each block is sized by how much it matters to you.
- The home feed doesn't end: when YouTube runs out of suggestions, it brings back songs you
  loved and haven't played in a while.
- Your library and profile follow your listening habits. The profile includes listening
  stats, and you can pick a main colour that recolours the whole app.

**Under the hood**
- YouTube Music streaming, search, account and library.
- Local files, downloads, synced lyrics from several providers, and listening history.
- Equalizer, loudness normalization, crossfade, Cast and optional Last.fm scrobbling.

## Download

Get the latest APK from [Releases](../../releases/latest). It needs Android 8.0 or newer.
Open the APK on your phone and allow installs from that source when Android asks. Every
release is signed with the same key, so a new version installs over the old one.

## Build from source

You need JDK 21 and the Android SDK (platform 37). Then:

```bash
./gradlew :app:assembleGmsMobileUniversalDebug
adb install -r app/build/outputs/apk/gmsMobileUniversal/debug/app-gms-mobile-universal-debug.apk
```

The debug build installs as **vibra debug**, next to the release.
[`docs/SETUP.md`](docs/SETUP.md) covers build variants, release signing and the optional
integrations. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) explains how the pieces fit,
and [`docs/FIGMA_IMPLEMENTATION_MAP.md`](docs/FIGMA_IMPLEMENTATION_MAP.md) maps the design
to the code.

## How the code is organised

| Where | What |
|---|---|
| `app/src/main/kotlin/app/vibra/` | vibra's interface: shell and dock, player, lyrics, blocks, motion, haptics, theme |
| `app/src/main/kotlin/moe/rukamori/archivetune/` | The music engine: playback service, database, YouTube Music, settings |
| `core/`, `lyrics/`, `lastfm/`, `morideobfuscator/`, … | Engine libraries |

## Licence and credits

vibra is free software under the **GNU General Public License v3.0** (see
[`LICENSE`](LICENSE)). It contains code © Rukamori and the ArchiveTune contributors. Their
copyright notices are kept in every engine file, and their original documents are in
[`docs/upstream/`](docs/upstream). The bundled fonts (Roboto, Google Sans Flex) are under
the SIL Open Font License 1.1. Third-party licences are listed in the app under
*Settings → About*.

vibra is not affiliated with or endorsed by ArchiveTune, YouTube or Google. Please don't
send vibra's changes to the ArchiveTune repositories.

## A note on how this was made

It's vibecoded. I don't care. It plays my music and the dock wobbles nicely. 🌊
