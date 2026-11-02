/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vibra.ui.theme.VibraShapes
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.NewAction

private val LocalMenuLayout = compositionLocalOf { MediaMenuLayout.Expanded }

/** The only owner of inter-card spacing and of scrolling, including the header. */
@Composable
fun MediaMenuSheet(
  layout: MediaMenuLayout,
  header: @Composable () -> Unit,
  actions: List<NewAction>,
  modifier: Modifier = Modifier,
  rows: @Composable ColumnScope.() -> Unit,
) {
  val compact = layout == MediaMenuLayout.Compact
  CompositionLocalProvider(LocalMenuLayout provides layout) {
    Column(
      modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
      verticalArrangement =
        Arrangement.spacedBy(if (compact) MenuTokens.CompactGap else MenuTokens.ExpandedGap),
    ) {
      header()
      if (actions.isNotEmpty()) MenuCard { MediaMenuActions(actions, layout) }
      MenuCard { Column(Modifier.padding(vertical = 12.dp), content = rows) }
    }
  }
}

@Composable
fun MenuCard(
  modifier: Modifier = Modifier,
  shape: Shape = VibraShapes.Card,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier.fillMaxWidth(),
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(content = content)
  }
}

/** Header geometry follows the selected expanded / compact reference, not list defaults. */
@Composable
fun MediaMenuHeader(
  title: String,
  subtitle: String,
  artworkUrl: String?,
  layout: MediaMenuLayout,
  onPlay: (() -> Unit)?,
  modifier: Modifier = Modifier,
  artworkShape: Shape = RoundedCornerShape(24.dp),
  compactArtwork: Dp = MenuTokens.CompactArtwork,
  toggle: @Composable () -> Unit = {},
) {
  val compact = layout == MediaMenuLayout.Compact
  MenuCard(modifier) {
    Row(
      Modifier.padding(
        horizontal = if (compact) 16.dp else 20.dp,
        vertical = if (compact) 10.dp else 20.dp,
      ),
      // Expanded: the text column spans the cover, title on its top edge and actions on its bottom.
      verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 18.dp),
    ) {
      val playLabel = stringResource(R.string.play) + " " + title
      VibraArtwork(
        url = artworkUrl,
        contentDescription = if (compact && onPlay != null) playLabel else null,
        shape =
          if (
            compact &&
              artworkShape != CircleShape &&
              artworkShape is androidx.compose.foundation.shape.CornerBasedShape
          )
            RoundedCornerShape(14.dp)
          else artworkShape,
        modifier =
          Modifier.size(if (compact) compactArtwork else MenuTokens.ExpandedArtwork)
            .then(
              if (compact && onPlay != null)
                Modifier.clickable(role = Role.Button, onClickLabel = playLabel, onClick = onPlay)
              else Modifier
            ),
      )
      Column(
        Modifier.weight(1f)
          .then(if (compact) Modifier else Modifier.heightIn(min = MenuTokens.ExpandedArtwork)),
        verticalArrangement =
          if (compact) Arrangement.Top else Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            title,
            fontSize = if (compact) MenuTokens.CompactTitle else MenuTokens.ExpandedTitle,
            lineHeight = if (compact) 24.sp else 26.sp,
            // No leading above the first line, so the title starts on the cover's top edge.
            style =
              LocalTextStyle.current.copy(
                lineHeightStyle =
                  LineHeightStyle(
                    LineHeightStyle.Alignment.Center,
                    LineHeightStyle.Trim.FirstLineTop,
                  )
              ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (subtitle.isNotBlank())
            Text(
              subtitle,
              fontSize = if (compact) 13.sp else 14.sp,
              lineHeight = 18.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
        }
        if (!compact)
          CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            Row(
              Modifier.padding(top = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = { onPlay?.invoke() },
                enabled = onPlay != null,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
              ) {
                Icon(painterResource(R.drawable.play), null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.play), maxLines = 1, fontSize = 15.sp)
              }
              toggle()
            }
          }
      }
      if (compact) toggle()
    }
  }
}

@Composable
fun MediaMenuToggle(selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
  val compact = LocalMenuLayout.current == MediaMenuLayout.Compact
  CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
    FilledTonalIconButton(
      onClick = onClick,
      modifier = Modifier.size(40.dp),
      shape = RoundedCornerShape(14.dp),
      colors =
        IconButtonDefaults.filledTonalIconButtonColors(
          containerColor =
            if (compact) Color.Transparent
            else if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
          contentColor =
            if (compact && selected) MaterialTheme.colorScheme.primary
            else if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      content = icon,
    )
  }
}

@Composable
fun MediaMenuActions(
  actions: List<NewAction>,
  layout: MediaMenuLayout,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier
      .fillMaxWidth()
      .padding(
        horizontal = 8.dp,
        vertical = if (layout == MediaMenuLayout.Compact) 12.dp else 16.dp,
      ),
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    actions.forEach { action ->
      Column(
        Modifier.weight(1f)
          .clip(RoundedCornerShape(20.dp))
          .clickable(
            enabled = action.enabled,
            role = Role.Button,
            onClickLabel = action.text,
            onClick = action.onClick,
          ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = if (action.selected) RoundedCornerShape(16.dp) else CircleShape,
          color =
            if (action.selected) MaterialTheme.colorScheme.primaryContainer
            else if (action.backgroundColor == Color.Unspecified)
              MaterialTheme.colorScheme.secondaryContainer
            else action.backgroundColor,
          contentColor =
            if (action.selected) MaterialTheme.colorScheme.onPrimaryContainer
            else if (action.contentColor == Color.Unspecified)
              MaterialTheme.colorScheme.onSecondaryContainer
            else action.contentColor,
        ) {
          Box(
            Modifier.size(if (layout == MediaMenuLayout.Compact) 48.dp else 52.dp),
            contentAlignment = Alignment.Center,
          ) {
            when (action.id) {
              MenuActionId.Enqueue ->
                Icon(painterResource(R.drawable.playlist_add), null, Modifier.size(24.dp))
              MenuActionId.Playlist -> Icon(Icons.Rounded.AddBox, null, Modifier.size(24.dp))
              else -> action.icon()
            }
          }
        }
        val label =
          when (action.id) {
            MenuActionId.PlayNext -> stringResource(R.string.vibra_menu_next)
            MenuActionId.Enqueue -> stringResource(R.string.vibra_menu_queue)
            MenuActionId.Playlist -> stringResource(R.string.vibra_menu_playlist)
            MenuActionId.Download ->
              stringResource(
                if (action.selected) R.string.vibra_menu_downloaded
                else R.string.vibra_menu_download
              )
            MenuActionId.Radio -> stringResource(R.string.vibra_menu_radio)
            MenuActionId.Shuffle -> stringResource(R.string.vibra_menu_shuffle)
            MenuActionId.Search -> stringResource(R.string.vibra_menu_search)
            else -> action.text
          }
        Text(
          label,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          modifier = Modifier.padding(horizontal = 2.dp),
        )
      }
    }
  }
}

/** Rows inherit one card. No row owns an extra surface, divider or outside padding. */
@Composable
fun MediaMenuActionRows(actions: List<NewAction>, modifier: Modifier = Modifier) {
  Column(modifier) {
    actions.forEach { action ->
      MediaMenuRow(
        headlineContent = { Text(action.text) },
        leadingContent = action.icon,
        modifier =
          Modifier.clickable(
            enabled = action.enabled,
            role = Role.Button,
            onClickLabel = action.text,
            onClick = action.onClick,
          ),
      )
    }
  }
}

@Composable
fun MediaMenuRow(
  headlineContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  leadingContent: (@Composable () -> Unit)? = null,
  leadingSize: Dp = MenuTokens.Icon,
  trailingContent: (@Composable () -> Unit)? = null,
  supportingContent: (@Composable () -> Unit)? = null,
  overlineContent: (@Composable () -> Unit)? = null,
  colors: ListItemColors = ListItemDefaults.colors(),
  tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
  shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
  val compact = LocalMenuLayout.current == MediaMenuLayout.Compact
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = if (compact) MenuTokens.CompactRow else MenuTokens.ExpandedRow)
      .padding(horizontal = if (compact) 18.dp else 20.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement =
      Arrangement.spacedBy(if (leadingSize > MenuTokens.Icon) 14.dp else 16.dp),
  ) {
    leadingContent?.let {
      Box(Modifier.size(leadingSize), contentAlignment = Alignment.Center) { it() }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      overlineContent?.invoke()
      ProvideTextStyle(
        MaterialTheme.typography.bodyLarge.copy(fontSize = MenuTokens.RowText, lineHeight = 22.sp)
      ) {
        headlineContent()
      }
      supportingContent?.let { ProvideTextStyle(MaterialTheme.typography.bodySmall) { it() } }
    }
    trailingContent?.invoke()
  }
}

@Composable
fun MediaMenuNavigationRow(onArtist: (() -> Unit)?, onAlbum: (() -> Unit)?) {
  if (onArtist == null && onAlbum == null) return
  MediaMenuRow(
    headlineContent = { Text(stringResource(R.string.vibra_menu_go_to)) },
    leadingContent = { Icon(painterResource(R.drawable.arrow_forward), null) },
    trailingContent = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onArtist != null) MenuChoicePill(stringResource(R.string.vibra_menu_artist), onArtist)
        if (onAlbum != null) MenuChoicePill(stringResource(R.string.vibra_menu_album), onAlbum)
      }
    },
  )
}

@Composable
private fun MenuChoicePill(label: String, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
  ) {
    Text(label, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
  }
}

fun mediaQuickActions(actions: List<NewAction>, kind: MediaMenuKind): List<NewAction> =
  quickActionIds(kind).mapNotNull { id -> actions.firstOrNull { it.id == id } }

fun mediaRemainingActions(actions: List<NewAction>, quick: List<NewAction>): List<NewAction> =
  actions
    .filter { it.id != MenuActionId.Play && it !in quick }
    .distinctBy { if (it.id == MenuActionId.Other) it else it.id }
