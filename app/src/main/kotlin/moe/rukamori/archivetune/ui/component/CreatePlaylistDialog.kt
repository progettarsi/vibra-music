/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibra.ui.components.*
import app.vibra.ui.components.VibraLoadingIndicator
import coil3.compose.AsyncImage
import app.vibra.ui.motion.vibraControlFeedback
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.CreatePlaylistEvent
import moe.rukamori.archivetune.viewmodels.CreatePlaylistScreenState
import moe.rukamori.archivetune.viewmodels.CreatePlaylistUiData
import moe.rukamori.archivetune.viewmodels.CreatePlaylistViewModel

@Composable
fun CreatePlaylistDialog(
  onDismiss: () -> Unit,
  initialTextFieldValue: String? = null,
  allowSyncing: Boolean = true,
  viewModel: CreatePlaylistViewModel = hiltViewModel(),
) {
  val screenState by viewModel.screenState.collectAsStateWithLifecycle()
  val currentOnDismiss by rememberUpdatedState(onDismiss)
  val dismiss =
    remember(viewModel) {
      {
        viewModel.close()
        currentOnDismiss()
      }
    }
  val updateName = remember(viewModel) { viewModel::updateName }
  val updateSyncRequested = remember(viewModel) { viewModel::updateSyncRequested }
  val submit = remember(viewModel) { viewModel::submit }

  LaunchedEffect(initialTextFieldValue, allowSyncing, viewModel) {
    viewModel.open(initialName = initialTextFieldValue.orEmpty(), allowSyncing = allowSyncing)
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      if (event == CreatePlaylistEvent.Created) {
        viewModel.close()
        currentOnDismiss()
      }
    }
  }

  when (val state = screenState) {
    CreatePlaylistScreenState.Loading -> {
      CreatePlaylistDialogContent(
        data =
          CreatePlaylistUiData(
            name = initialTextFieldValue.orEmpty(),
            allowSyncing = allowSyncing,
            isSignedIn = false,
            isSyncEnabled = false,
            syncRequested = false,
            isSubmitting = false,
          ),
        isLoading = true,
        errorMessageResId = null,
        onNameChange = updateName,
        onCoverStyleChange = viewModel::updateCoverStyle,
        onCoverUriChange = viewModel::updateCoverUri,
        onSyncRequestedChange = updateSyncRequested,
        onSubmit = submit,
        onDismiss = dismiss,
      )
    }

    is CreatePlaylistScreenState.Success -> {
      CreatePlaylistDialogContent(
        data = state.data,
        isLoading = false,
        errorMessageResId = null,
        onNameChange = updateName,
        onCoverStyleChange = viewModel::updateCoverStyle,
        onCoverUriChange = viewModel::updateCoverUri,
        onSyncRequestedChange = updateSyncRequested,
        onSubmit = submit,
        onDismiss = dismiss,
      )
    }

    CreatePlaylistScreenState.Empty -> {
      CreatePlaylistDialogContent(
        data =
          CreatePlaylistUiData(
            name = "",
            allowSyncing = allowSyncing,
            isSignedIn = false,
            isSyncEnabled = false,
            syncRequested = false,
            isSubmitting = false,
          ),
        isLoading = false,
        errorMessageResId = null,
        onNameChange = updateName,
        onCoverStyleChange = viewModel::updateCoverStyle,
        onCoverUriChange = viewModel::updateCoverUri,
        onSyncRequestedChange = updateSyncRequested,
        onSubmit = submit,
        onDismiss = dismiss,
      )
    }

    is CreatePlaylistScreenState.Error -> {
      CreatePlaylistDialogContent(
        data = state.data,
        isLoading = false,
        errorMessageResId = state.messageResId,
        onNameChange = updateName,
        onCoverStyleChange = viewModel::updateCoverStyle,
        onCoverUriChange = viewModel::updateCoverUri,
        onSyncRequestedChange = updateSyncRequested,
        onSubmit = submit,
        onDismiss = dismiss,
      )
    }
  }
}

@Composable
private fun CreatePlaylistDialogContent(
  data: CreatePlaylistUiData,
  isLoading: Boolean,
  @StringRes errorMessageResId: Int?,
  onNameChange: (String) -> Unit,
  onCoverStyleChange: (String) -> Unit,
  onCoverUriChange: (String?) -> Unit,
  onSyncRequestedChange: (Boolean) -> Unit,
  onSubmit: () -> Unit,
  onDismiss: () -> Unit,
) {
  val enabled = !isLoading && !data.isSubmitting
  VibraDialog(
    onDismissRequest = onDismiss,
    groupContent = false,
    contentPadding = PaddingValues(0.dp),
    properties =
      DialogProperties(
        dismissOnClickOutside = !data.isSubmitting,
        dismissOnBackPress = !data.isSubmitting,
      ),
  ) {
    Column(
      Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      ControlSheetHeader(stringResource(R.string.create_playlist), "", R.drawable.playlist_add)
      MenuCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          val nameLabel = stringResource(R.string.playlist_name)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              nameLabel,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = data.name,
              onValueChange = onNameChange,
              enabled = enabled,
              singleLine = true,
              placeholder = { Text(stringResource(R.string.vibra_menu_playlist_name_hint)) },
              modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameLabel },
              shape = VibraFieldShape,
              colors = vibraOutlinedTextFieldColors(),
            )
          }
          if (data.allowSyncing) SyncPlaylistSection(data, enabled, onSyncRequestedChange)
          if (errorMessageResId != null)
            Text(stringResource(errorMessageResId), color = MaterialTheme.colorScheme.error)
        }
      }
      MenuCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          PlaylistCoverStylePicker(
            parsePlaylistCoverStyle(data.coverStyle),
            onChange = { onCoverStyleChange(it.name) },
          )
          CreatePlaylistCoverImage(data, enabled, onCoverUriChange)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VibraDialogButton(
          onClick = onDismiss,
          enabled = !data.isSubmitting,
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(android.R.string.cancel))
        }
        VibraDialogButton(
          onClick = onSubmit,
          primary = true,
          enabled = enabled && data.name.isNotBlank(),
          modifier = Modifier.weight(1f),
        ) {
          if (data.isSubmitting)
            VibraLoadingIndicator(Modifier.size(20.dp), color = LocalContentColor.current)
          else Text(stringResource(R.string.vibra_menu_create))
        }
      }
    }
  }
}

/** The image is only remembered here and applied once the playlist exists (same path as Edit). */
@Composable
private fun CreatePlaylistCoverImage(
  data: CreatePlaylistUiData,
  enabled: Boolean,
  onCoverUriChange: (String?) -> Unit,
) {
  val pickCover =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { onCoverUriChange(it.toString()) }
    }
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    data.coverUri?.let { uri ->
      AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(48.dp).clip(parsePlaylistCoverStyle(data.coverStyle).shape()),
      )
    }
    VibraDialogButton(onClick = { pickCover.launch(arrayOf("image/*")) }, enabled = enabled) {
      Text(
        stringResource(
          if (data.coverUri == null) R.string.vibra_choose_cover else R.string.vibra_change_cover
        )
      )
    }
    if (data.coverUri != null)
      VibraDialogButton(onClick = { onCoverUriChange(null) }, enabled = enabled, destructive = true) {
        Text(stringResource(R.string.vibra_remove_cover))
      }
  }
}

@Composable
private fun SyncPlaylistSection(
  data: CreatePlaylistUiData,
  enabled: Boolean,
  onSyncRequestedChange: (Boolean) -> Unit,
) {
  val syncDescriptionResId =
    when {
      !data.isSignedIn -> R.string.not_logged_in_youtube
      !data.isSyncEnabled -> R.string.sync_disabled
      else -> R.string.allows_for_sync_witch_youtube
    }

  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      painter = painterResource(R.drawable.sync),
      contentDescription = null,
      tint =
        if (data.syncRequested) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
      modifier = Modifier.padding(top = 2.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sync_playlist),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = stringResource(syncDescriptionResId),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = data.syncRequested,
      onCheckedChange = onSyncRequestedChange,
      enabled = enabled,
      modifier = Modifier.vibraControlFeedback(enabled = enabled),
    )
  }
}
