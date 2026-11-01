/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import app.vibra.ui.components.VibraBottomSheet
import app.vibra.ui.theme.VibraDimens

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(isVisible: Boolean = false, content: @Composable ColumnScope.() -> Unit = {}) {
  var isVisible by mutableStateOf(isVisible)
  var content by mutableStateOf(content)
  internal var dialogContent by mutableStateOf<(@Composable () -> Unit)?>(null)
    private set

  @OptIn(ExperimentalMaterial3Api::class)
  fun show(content: @Composable ColumnScope.() -> Unit) {
    dialogContent = null
    isVisible = true
    this.content = content
  }

  @OptIn(ExperimentalMaterial3Api::class)
  fun dismiss() {
    isVisible = false
  }

  fun showDialog(content: @Composable () -> Unit) {
    isVisible = false
    dialogContent = content
  }

  fun dismissDialog() {
    dialogContent = null
  }
}

// Vibra: the native modal owns its position and wraps the bounded scrollable menu.
// A parent page's Box alignment must never be passed into this separate window.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(modifier: Modifier = Modifier, state: MenuState) {
  val focusManager = LocalFocusManager.current

  state.dialogContent?.invoke()

  if (state.isVisible) {
    VibraBottomSheet(
      onDismissRequest = {
        focusManager.clearFocus()
        state.isVisible = false
      },
      modifier = modifier,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = VibraDimens.Gutter)) {
        state.content(this)
      }
    }
  }
}
