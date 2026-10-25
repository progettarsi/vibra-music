# Figma → Android implementation map

Source: Figma file **Vibra** (`roWjwjEcqcRmusPbqDLu54`, page "Page 1"), inspected through
the Figma MCP integration (`get_metadata`, `get_design_context`, `get_variable_defs`,
`get_screenshot`). Artboards are 402 × 874, which map 1:1 to dp on 400–412 dp phones.

## Design tokens

| Figma | Value | Android |
|---|---|---|
| `Schemes/Background` | `#141218` | `VibraPalette.Background` → `colorScheme.background/surface` |
| `Schemes/Surface Container` | `#211F26` | `VibraPalette.SurfaceContainer` → `colorScheme.surfaceContainer` |
| `Schemes/Primary` | `#D0BCFF` | `colorScheme.primary` |
| `Schemes/On Primary` | `#381E72` | `colorScheme.onPrimary` |
| `Schemes/On Surface` / `On Background` | `#E6E0E9` | `colorScheme.onSurface/onBackground` |
| `Schemes/On Surface Variant` | `#CAC4D0` | `colorScheme.onSurfaceVariant` |
| `Schemes/Secondary` | `#CCC2DC` | `colorScheme.secondary` |
| `Schemes/Surface Variant` | `#49454F` | `colorScheme.surfaceVariant` |
| "palette" frame (268:574) | 4 swatches above | same tokens |
| `Static/*/Font` | Roboto, emphasized weight Medium | `VibraRoboto` (bundled variable font), `vibraTypography()` |
| Player title "APNEA" | Google Sans Flex Bold 45/52 | `VibraTextStyles.PlayerTitle` (bundled variable font, ROND 40) |
| Card radius | 32 | `VibraShapes.Card` |
| Song row radius / thumb radius | 24 / 14 | `VibraShapes.Row` / `VibraShapes.RowThumbnail` |
| Page gutter / card gap | 20 / 10 | `VibraDimens.Gutter` / `VibraDimens.CardGap` |
| Dock | 362 × 55.46, 20 above bottom, shadow 0 3 13.75 #00000040, backdrop blur 7.5 | `VibraDock` + `GooSurface` shadow; `VibraDimens.DockHeight/DockBottomMargin` |
| Shape Set: 12-sided cookie | play buttons | `MaterialShapes.Cookie12Sided` (`VibraPlayButton`) |
| Shape Set (playlist cover) | soft pentagon | `MaterialShapes.Pentagon` (`MediaDetailHero`) |
| Shape Set (profile picture) | flower | `MaterialShapes.Flower` (`VibraProfileScreen`) |
| Vibra Icon (95:535) | wave glyph on #141218 | `res/drawable/ic_vibra_foreground.xml` (adaptive + themed icon) |
| Home logo (63:354) | wave stroke | `res/drawable/ic_vibra_logo.xml` |

## Screens

| Figma frame | Node | Android implementation | Data source | Fidelity |
|---|---|---|---|---|
| main-homepage | 1:2 | `app/vibra/ui/home/VibraHomeScreen.kt` | `HomeViewModel` (quick picks, YT home feed, chips, playlists, keep-listening) | Near-exact layout; artwork is real content, so colours differ from the mock images |
| ↳ ForYou | 22:221 | `ForYouCard` — two cookie mood buttons (feed chips; fallback Moods & genres / Charts) + "For You" Sunny blob (plays personalised quick picks) | chips, quick picks | Approximate: mood cookies use gradients because chips carry no artwork |
| ↳ songsCard | 73:1088 | `SongsCard` — 4 `VibraRow(background = None)` | quick picks | Exact geometry |
| ↳ coverCard + play overlay | 55:438 / 55:581 | `CoverCard` | first keep-listening item | Exact |
| ↳ playlistCard | 26:1428 | `PlaylistCard` — SoftBurst art, title, "by You", Play pill, ⋯ | first local/account playlist (fallback: Library) | Exact |
| ↳ albumCollectionCard | 26:335 | `AlbumCollectionCard` | first feed section with ≥3 albums | Exact layout |
| ↳ (not in Figma) further feed shelves | — | `MosaicBlock` (blocks/) | engine sections, similar recommendations | Derived from card/coverCard |
| dock | 31:2028 / 76:1235 | `app/vibra/ui/dock/VibraDock.kt` | `NowPlaying` | Exact layout + liquid behaviour (see below) |
| playlistScreen | 31:1981 | engine `LocalPlaylistScreen` / `OnlinePlaylistScreen` / `AutoPlaylistScreen`… via re-skinned `MediaDetailHero` + `ListItem`→`VibraRow` | engine ViewModels | Near-exact header and rows; extra engine actions (download, sync, edit) appear as additional round buttons |
| albumScreen | 31:2288 | engine `AlbumScreen` via re-skinned `MediaDetailHero` + `VibraRow` | `AlbumViewModel` | Near-exact |
| playerScreen | 55:592 | `app/vibra/ui/player/VibraPlayer.kt` (`VibraPlayerSheet`) | `PlayerConnection` | Near-exact; adds the queueOpen action row |
| queueOpen | 74:416 | `VibraQueuePanel` + controls slide-up | `PlayerConnection.queueWindows` | Near-exact; adds header (✕ / Up next / shuffle) |
| profile | 75:508 | `app/vibra/ui/profile/VibraProfileScreen.kt` | account (HomeViewModel), `mostPlayedArtists` (7 days), playlists | Near-exact; adds Library shortcuts card |
| Components: Canzone (4 variants) | 31:2290 | `VibraRow` (`RowBackground.Card/None`) | — | Exact |
| Components: coverCard variants | 55:368 | `CoverBlock` / `MosaicBlock` | — | Exact |
| Components: favourites card | 116:557 | `FavouritesCard` | weekly top artist | Exact |
| Components: playlistsCard | 116:835 | `PlaylistsCard` | playlists | Approximate (title added for clarity) |

## Interactions specified by the user brief (no Figma prototype links exist)

| Interaction | Implementation |
|---|---|
| Search bar "spawns" from the search button, player shrinks to make room | `VibraDock`: `LiquidScope` springs the search slot from 56 dp to `weight(1f)` and the mini-player from `weight(1f)` to a 56 dp cookie; the profile pill collapses to 0 and its gap closes with a spring; `GooSurface` renders the pills as merging liquid; suggestions rise from the dock (`OnlineSearchScreen`). |
| Dock adapts to usage | No media → profile and search sit at the edges and the player pill grows between them when playback starts; on the profile screen the leading pill becomes Settings; the dock fades away while the player is open. |
| Mini-player ↔ full player | `VibraPlayerSheet` grows from the mini-player bounds (rect + corner-radius lerp) driven by `LiquidSheetState` (tap, drag up, drag down, predictive back). |
| Queue | `swap_vert` or the "From:" pill slides the controls up and the queue panel in (second `LiquidSheetState`). |
| Lyrics | "lyrics" action shows the engine `LyricsScreen` over the player (fade + spring scale). |

## Screens with no Figma design (derived)

Settings, Library, Artist, Search results/suggestions, Stats, History, Charts, Moods &
genres, Login, Music recognition, Year in Music, podcasts, Spotify import and all
dialogs/menus keep the engine layouts. They inherit the Vibra palette, typography,
shapes, the re-skinned rows (`VibraRow`) and hero (`MediaDetailHero`), so they read as the
same product. Library gains a `VibraTopBar` (Figma `arrow_back` style).

## Assets

| Asset | Status |
|---|---|
| Vibra icon + logo vectors | Exported from Figma and converted to Android vector drawables |
| Material icons (`swap_vert`, `schedule`) | Added as vector drawables (Apache-2.0 Material icons) |
| Photographs/cover art in the mockups (album art, portraits, 3D avatar) | Intentionally **not** shipped: they are placeholder content; real artwork comes from the user's music and account |
