/*
 * Vibra (2026) — an independent music client built on the ArchiveTune engine.
 * GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori).
 */

package app.vibra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibra.ui.theme.VibraDimens
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight

/**
 * Minimal Vibra top bar (Figma "arrow_back", 37 dp glyph at x = 20) used by engine
 * screens that relied on ArchiveTune's global app bar (e.g. Library). It occupies exactly
 * the [AppBarHeight] those screens reserve below the status bar, so their layout is
 * unchanged.
 */
@Composable
fun VibraTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(AppBarHeight)
                .padding(horizontal = VibraDimens.Gutter - 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            VibraGlyphButton(
                icon = R.drawable.arrow_back,
                contentDescription = stringResource(R.string.vibra_back),
                onClick = onBack,
                iconSize = 30.dp,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .semantics { heading() },
        )
    }
}

/** Hosts an engine screen with a [VibraTopBar] drawn over its reserved app-bar area. */
@Composable
fun WithVibraTopBar(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    Box {
        content()
        VibraTopBar(title = title, onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
    }
}

/**
 * Soft fade at the top of screens whose back button floats over scrolling content
 * (profile, stats): content dissolves under the status bar and the button instead of
 * colliding with them.
 */
@Composable
fun TopScrim(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.background
    val status = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(status + 72.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to color,
                        0.6f to color.copy(alpha = 0.85f),
                        1f to color.copy(alpha = 0f),
                    ),
                ),
    )
}
