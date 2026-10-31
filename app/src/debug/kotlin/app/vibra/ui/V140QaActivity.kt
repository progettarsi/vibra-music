/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vibra.ui.blocks.*
import app.vibra.ui.onboarding.*
import app.vibra.ui.motion.VibraTextButton
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme

/** Debug-only production-component previews. Sign-in callbacks are simulated, never real authentication. */
class V140QaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "home"
        val count = intent.getIntExtra("count", 2)
        setContent {
            ArchiveTuneTheme(darkTheme = true) {
                if (mode == "onboarding") {
                    val vm = remember { VibraOnboardingViewModel(object : OnboardingAccount {
                        override suspend fun youTubeProfile() = "Preview" to null
                        override suspend fun youTubeArtists() = emptyList<OnboardingArtist>()
                        override suspend fun spotifyArtists() = emptyList<OnboardingArtist>()
                        override fun importSpotifyPlaylists() {}
                        override suspend fun follow(artists: List<OnboardingArtist>) {}
                    }) }
                    VibraOnboarding(vm, vm::onYouTubeSignedIn, vm::onSpotifySignedIn, {})
                } else {
                    var taps by remember { mutableIntStateOf(0) }
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp).statusBarsPadding()) {
                        Text("Production layout preview", color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(20.dp))
                        ForYouBlock(null, "For You", "Picked from what you listen to",
                            List((count - 1).coerceIn(0, 2)) { index -> MoodEntry(if (index == 0) "Discovery Mix" else "My Mix", false) { taps++ } },
                            { taps++ }, Modifier.fillMaxWidth())
                        Text("Actions: $taps", color = MaterialTheme.colorScheme.onBackground)
                        VibraTextButton(onClick = { taps = 0 }) { Text("Reset") }
                    }
                }
            }
        }
    }
}
