/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.vibra.ui.blocks.ForYouBlock
import app.vibra.ui.blocks.MoodEntry
import app.vibra.ui.components.VibraCard
import app.vibra.ui.components.VibraFilledIconButton
import app.vibra.ui.components.VibraGlyphButton
import app.vibra.ui.components.VibraPrimaryButton
import app.vibra.ui.components.VibraRow
import app.vibra.ui.motion.LocalHasTapBounce
import app.vibra.ui.motion.VibraIconButton
import app.vibra.ui.motion.VibraTextButton
import app.vibra.ui.motion.combinedClickable
import app.vibra.ui.motion.vibraControlFeedback
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.AlbumPlayButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.IconButton as EngineIconButton

/**
 * Debug-only matrix of production touch surfaces: every action bumps a named counter, so a tap
 * can be checked for exactly one callback. `bounce=false` runs the same matrix with the shared
 * bounce suppressed. Nothing here talks to the network or the player.
 */
@Composable
fun FeedbackBoundsQa(bounce: Boolean) {
    val hits = remember { mutableStateMapOf<String, Int>() }
    fun hit(name: String) { hits[name] = (hits[name] ?: 0) + 1 }
    CompositionLocalProvider(LocalHasTapBounce provides !bounce) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "hits " + hits.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" },
                color = MaterialTheme.colorScheme.onBackground,
                // One line, so counting never moves the controls under the finger.
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("hits"),
            )
            // Pills and circles: Material with animated shapes, and the custom/engine circles.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                VibraPrimaryButton("Play", R.drawable.play, { hit("pill") })
                VibraPrimaryButton("Off", R.drawable.play, { hit("pillOff") }, enabled = false)
                VibraFilledIconButton(R.drawable.add, "Add", { hit("circle") })
                VibraFilledIconButton(R.drawable.add, "Add off", { hit("circleOff") }, enabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                VibraIconButton(onClick = { hit("m3icon") }, shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Icon(painterResource(R.drawable.more_vert), "M3 icon")
                }
                EngineIconButton(onClick = { hit("engine") }, onLongClick = { hit("engineLong") },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Icon(painterResource(R.drawable.more_vert), "Engine icon")
                }
                EngineIconButton(onClick = { hit("engineOff") }, onLongClick = {}, enabled = false) {
                    Icon(painterResource(R.drawable.more_vert), "Engine icon off")
                }
                VibraGlyphButton(R.drawable.skip_next, "Glyph", { hit("glyph") })
                VibraGlyphButton(R.drawable.skip_next, "Glyph off", { hit("glyphOff") }, enabled = false)
                VibraTextButton(onClick = { hit("text") }) { Text("Text") }
            }
            // Card with an independent inner action.
            VibraCard(Modifier.fillMaxWidth().height(96.dp), onClick = { hit("card") }, onLongClick = { hit("cardLong") }) {
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Card", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    EngineIconButton(onClick = { hit("cardInner") }, onLongClick = {}) {
                        Icon(painterResource(R.drawable.more_vert), "Card inner")
                    }
                }
            }
            // Shared row with a trailing menu, as on song lists.
            VibraRow(
                title = "Row with menu",
                thumbnail = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiary)) },
                interactionModifier = Modifier.combinedClickable(onLongClick = { hit("rowLong") }) { hit("row") },
                trailing = {
                    EngineIconButton(onClick = { hit("rowMenu") }, onLongClick = {}) {
                        Icon(painterResource(R.drawable.more_vert), "Row menu")
                    }
                },
                bounceOnTap = true,
            )
            // Inherited settings selection: the sheet rows are the shared selectable rows ("C" is disabled).
            var choice by remember { mutableStateOf("A") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ListPreference(
                        title = { Text("Selection list") },
                        selectedValue = choice,
                        values = listOf("A", "B", "C"),
                        valueText = { it },
                        isValueEnabled = { it != "C" },
                        onValueSelected = { choice = it; hit("select$it") },
                    )
                }
                Checkbox(checked = (hits["check"] ?: 0) % 2 == 1, onCheckedChange = { hit("check") }, modifier = Modifier.vibraControlFeedback())
            }
            // Block surfaces: For You with one mix (interactive surface) and an album play button.
            ForYouBlock(null, "For You", "Picked from what you listen to",
                listOf(MoodEntry("Discovery Mix", false) { hit("mix") }), { hit("forYou") }, Modifier.fillMaxWidth())
            Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A4A6A))) {
                AlbumPlayButton(visible = true, onClick = { hit("album") })
            }
            VibraTextButton(onClick = { hits.clear() }) { Text("Reset") }
            Spacer(Modifier.height(600.dp))
        }
    }
}
