/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.vibra.ui.blocks.ForYouBlock
import app.vibra.ui.blocks.MoodEntry
import app.vibra.ui.player.PlayerModeButton
import moe.rukamori.archivetune.R

/**
 * Production surfaces with local state only, for held-press and interrupted-transition captures.
 */
@Composable
fun HomePlayerMotionQa() {
  var actions by remember { mutableIntStateOf(0) }
  var repeat by remember { mutableIntStateOf(0) }
  var shuffle by remember { mutableStateOf(false) }
  Column(
    Modifier.fillMaxSize().background(Color(0xFF24202B)).statusBarsPadding().padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Text("Home / player motion", color = Color.White)
    ForYouBlock(
      artworkUrl = null,
      title = "For You",
      subtitle = "Picked from what you listen to",
      moods = listOf(MoodEntry("Discovery Mix", false) { actions++ }),
      onClick = { actions++ },
      modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      PlayerModeButton(
        R.drawable.repeat,
        "Repeat $repeat",
        repeat != 0,
        { repeat = (repeat + 1) % 3 },
        repeatOne = repeat == 2,
      )
      PlayerModeButton(R.drawable.shuffle, "Shuffle $shuffle", shuffle, { shuffle = !shuffle })
    }
    Text("Actions: $actions; repeat: $repeat; shuffle: $shuffle", color = Color.White)
  }
}
