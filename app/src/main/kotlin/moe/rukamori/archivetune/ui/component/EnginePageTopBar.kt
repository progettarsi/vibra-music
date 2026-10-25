/*
 * Vibra (2026) - GPL-3.0 License.
 * Derived from ArchiveTune c Rukamori (github.com/rukamori).
 */
package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.vibra.ui.components.VibraTopBar
import app.vibra.ui.theme.VibraDimens
import moe.rukamori.archivetune.constants.AppBarHeight

/** Shared fixed header for inherited pages; preserves their toolbar actions and navigation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnginePageTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth()) {
            VibraTopBar(title = "", onBack = if (navigationIcon == null) onBack else null)
            if (navigationIcon != null) {
                Box(
                    Modifier.align(Alignment.TopStart).statusBarsPadding().height(AppBarHeight).padding(start = VibraDimens.Gutter - 8.dp),
                    contentAlignment = Alignment.Center,
                ) { navigationIcon() }
            }
            Row(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().height(AppBarHeight).padding(end = VibraDimens.Gutter),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        Text(
            title,
                        modifier = Modifier.clipToBounds().layout { measurable, constraints ->
                val child = measurable.measure(constraints)
                val state = scrollBehavior?.state
                if (state != null) state.heightOffsetLimit = -child.height.toFloat()
                val visibleHeight = (child.height + (state?.heightOffset ?: 0f)).roundToInt().coerceAtLeast(0)
                layout(child.width, visibleHeight) { child.placeRelative(0, 0) }
            }.padding(horizontal = VibraDimens.Gutter).padding(bottom = 16.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle != null) {
            Box(Modifier.padding(horizontal = VibraDimens.Gutter).padding(bottom = 16.dp)) { subtitle() }
        }
    }
}

/** Opaque page root: navigation transitions cannot expose content from the outgoing page. */
@Composable
fun EnginePageSurface(content: @Composable () -> Unit) {
    // A Surface propagates minimum constraints: sibling toolbar/content roots would both
    // become screen-sized. This Box paints the page without imposing those minima on children.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}
