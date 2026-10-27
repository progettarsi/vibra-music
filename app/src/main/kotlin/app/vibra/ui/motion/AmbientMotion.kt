/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import moe.rukamori.archivetune.LocalAnimationsDisabled

/**
 * Whether *ambient* motion may run: the continuous, purely decorative animations that tick
 * every frame without anyone touching the screen — the cover cookie spinning while music plays,
 * the drifting atmospheres, the profile wallpaper, the seek bar's wave, the onboarding's sway,
 * the wobble of queue rows.
 *
 * Responses to the user (presses, page transitions, the player opening) are not ambient and
 * keep running. The battery saver (PowerSaveController, Normal level) turns ambient motion
 * off; its Max level and the system's "remove animations" turn every animation off
 * (LocalAnimationsDisabled), which also stops ambient motion.
 */
val LocalAmbientMotion = compositionLocalOf { true }

/** True when ambient motion may run (see [LocalAmbientMotion]). */
val ambientMotionAllowed: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAmbientMotion.current && !LocalAnimationsDisabled.current
