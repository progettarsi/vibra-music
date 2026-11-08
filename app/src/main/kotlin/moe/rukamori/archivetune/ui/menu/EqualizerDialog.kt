/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vibra.ui.components.*
import app.vibra.ui.components.VibraAlertDialog as AlertDialog
import app.vibra.ui.components.VibraDialogButton
import app.vibra.ui.components.VibraFieldShape
import app.vibra.ui.components.VibraLoadingIndicator
import app.vibra.ui.components.vibraTextFieldColors
import app.vibra.ui.motion.VibraIconButton as IconButton
import app.vibra.ui.motion.VibraOutlinedButton as OutlinedButton
import app.vibra.ui.motion.vibraControlFeedback
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.equalizer.EqualizerControlMode
import moe.rukamori.archivetune.equalizer.EqualizerTone
import moe.rukamori.archivetune.viewmodels.EqualizerEffect
import moe.rukamori.archivetune.viewmodels.EqualizerProfileUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerScreenState
import moe.rukamori.archivetune.viewmodels.EqualizerUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerViewModel

@Composable
fun EqualizerDialog(
  onDismiss: () -> Unit,
  openSystemEqualizer: () -> Unit,
  viewModel: EqualizerViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let(viewModel::importProfiles)
    }
  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
      uri ->
      uri?.let(viewModel::exportProfile)
    }

  LaunchedEffect(viewModel, context, importLauncher, exportLauncher) {
    viewModel.effects.collect { effect ->
      when (effect) {
        is EqualizerEffect.ShowMessage -> {
          val message =
            effect.quantity?.let { context.getString(effect.messageResId, it) }
              ?: context.getString(effect.messageResId)
          snackbarHostState.showSnackbar(message)
        }

        EqualizerEffect.OpenImportDocument -> {
          importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
        }

        is EqualizerEffect.CreateExportDocument -> {
          exportLauncher.launch(effect.suggestedName)
        }
      }
    }
  }

  VibraBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
      EqualizerScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss,
        onOpenSystemEqualizer = openSystemEqualizer,
        onEnabledChange = viewModel::setEnabled,
        onModeChange = viewModel::setControlMode,
        onPresetClick = viewModel::applyPreset,
        onToneValueChange = viewModel::updateToneDraft,
        onToneValueChangeFinished = viewModel::commitTone,
        onBandValueChange = viewModel::updateBandDraft,
        onBandValueChangeFinished = viewModel::commitBands,
        onResetBands = viewModel::resetBands,
        onOutputGainEnabledChange = viewModel::setOutputGainEnabled,
        onOutputGainValueChange = viewModel::updateOutputGainDraft,
        onOutputGainValueChangeFinished = viewModel::commitOutputGain,
        onBassBoostEnabledChange = viewModel::setBassBoostEnabled,
        onBassBoostValueChange = viewModel::updateBassBoostDraft,
        onBassBoostValueChangeFinished = viewModel::commitBassBoost,
        onVirtualizerEnabledChange = viewModel::setVirtualizerEnabled,
        onVirtualizerValueChange = viewModel::updateVirtualizerDraft,
        onVirtualizerValueChangeFinished = viewModel::commitVirtualizer,
        onAutoHeadroomEnabledChange = viewModel::setAutoHeadroomEnabled,
        onShowSaveProfile = viewModel::showSaveProfileDialog,
        onProfileNameChange = viewModel::updateProfileName,
        onSaveProfile = viewModel::saveProfile,
        onDismissSaveProfile = viewModel::dismissSaveProfileDialog,
        onShowManageProfiles = viewModel::showManageProfiles,
        onDismissManageProfiles = viewModel::dismissManageProfiles,
        onApplyProfile = viewModel::applyProfile,
        onDeleteProfile = viewModel::deleteProfile,
        onImportProfiles = viewModel::requestImport,
        onExportProfile = viewModel::requestExport,
      )
    }
  }
}

@Composable
private fun EqualizerScreen(
  state: EqualizerScreenState,
  snackbarHostState: SnackbarHostState,
  onDismiss: () -> Unit,
  onOpenSystemEqualizer: () -> Unit,
  onEnabledChange: (Boolean) -> Unit,
  onModeChange: (EqualizerControlMode) -> Unit,
  onPresetClick: (String) -> Unit,
  onToneValueChange: (EqualizerTone, Int) -> Unit,
  onToneValueChangeFinished: (EqualizerTone) -> Unit,
  onBandValueChange: (Int, Int) -> Unit,
  onBandValueChangeFinished: () -> Unit,
  onResetBands: () -> Unit,
  onOutputGainEnabledChange: (Boolean) -> Unit,
  onOutputGainValueChange: (Int) -> Unit,
  onOutputGainValueChangeFinished: () -> Unit,
  onBassBoostEnabledChange: (Boolean) -> Unit,
  onBassBoostValueChange: (Int) -> Unit,
  onBassBoostValueChangeFinished: () -> Unit,
  onVirtualizerEnabledChange: (Boolean) -> Unit,
  onVirtualizerValueChange: (Int) -> Unit,
  onVirtualizerValueChangeFinished: () -> Unit,
  onAutoHeadroomEnabledChange: (Boolean) -> Unit,
  onShowSaveProfile: () -> Unit,
  onProfileNameChange: (String) -> Unit,
  onSaveProfile: () -> Unit,
  onDismissSaveProfile: () -> Unit,
  onShowManageProfiles: () -> Unit,
  onDismissManageProfiles: () -> Unit,
  onApplyProfile: (String) -> Unit,
  onDeleteProfile: (String) -> Unit,
  onImportProfiles: () -> Unit,
  onExportProfile: (String) -> Unit,
) {
  Box(Modifier.fillMaxWidth()) {
    when (state) {
      is EqualizerScreenState.Success ->
        EqualizerContent(
          model = state.model,
          onApplyProfile = onApplyProfile,
          contentPadding = PaddingValues(0.dp),
          onDismiss = onDismiss,
          onOpenSystemEqualizer = onOpenSystemEqualizer,
          onEnabledChange = onEnabledChange,
          onModeChange = onModeChange,
          onPresetClick = onPresetClick,
          onToneValueChange = onToneValueChange,
          onToneValueChangeFinished = onToneValueChangeFinished,
          onBandValueChange = onBandValueChange,
          onBandValueChangeFinished = onBandValueChangeFinished,
          onResetBands = onResetBands,
          onOutputGainEnabledChange = onOutputGainEnabledChange,
          onOutputGainValueChange = onOutputGainValueChange,
          onOutputGainValueChangeFinished = onOutputGainValueChangeFinished,
          onBassBoostEnabledChange = onBassBoostEnabledChange,
          onBassBoostValueChange = onBassBoostValueChange,
          onBassBoostValueChangeFinished = onBassBoostValueChangeFinished,
          onVirtualizerEnabledChange = onVirtualizerEnabledChange,
          onVirtualizerValueChange = onVirtualizerValueChange,
          onVirtualizerValueChangeFinished = onVirtualizerValueChangeFinished,
          onAutoHeadroomEnabledChange = onAutoHeadroomEnabledChange,
          onShowSaveProfile = onShowSaveProfile,
          onShowManageProfiles = onShowManageProfiles,
          onImportProfiles = onImportProfiles,
        )
      EqualizerScreenState.Loading ->
        MenuCard {
          Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            VibraLoadingIndicator()
          }
        }
      else ->
        MenuCard {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.equalizer), style = MaterialTheme.typography.titleLarge)
            Text(
              stringResource(
                if (state is EqualizerScreenState.Error) state.messageResId
                else R.string.eq_waiting_for_audio_session
              )
            )
            VibraDialogButton(onClick = onOpenSystemEqualizer, primary = true) {
              Text(stringResource(R.string.eq_open_system_equalizer))
            }
            VibraDialogButton(onClick = onDismiss) {
              Text(stringResource(R.string.vibra_menu_done))
            }
          }
        }
    }
    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
  }

  val model = (state as? EqualizerScreenState.Success)?.model
  if (model?.saveProfileDialog?.visible == true) {
    SaveProfileDialog(
      name = model.saveProfileDialog.name,
      onNameChange = onProfileNameChange,
      onSave = onSaveProfile,
      onDismiss = onDismissSaveProfile,
    )
  }
  if (model?.manageProfilesVisible == true) {
    ManageProfilesDialog(
      profiles = model.profiles,
      onApply = onApplyProfile,
      onDelete = onDeleteProfile,
      onExport = onExportProfile,
      onDismiss = onDismissManageProfiles,
    )
  }
}

@Composable
private fun EqualizerContent(
  model: EqualizerUiModel,
  onApplyProfile: (String) -> Unit,
  onDismiss: () -> Unit,
  contentPadding: PaddingValues,
  onOpenSystemEqualizer: () -> Unit,
  onEnabledChange: (Boolean) -> Unit,
  onModeChange: (EqualizerControlMode) -> Unit,
  onPresetClick: (String) -> Unit,
  onToneValueChange: (EqualizerTone, Int) -> Unit,
  onToneValueChangeFinished: (EqualizerTone) -> Unit,
  onBandValueChange: (Int, Int) -> Unit,
  onBandValueChangeFinished: () -> Unit,
  onResetBands: () -> Unit,
  onOutputGainEnabledChange: (Boolean) -> Unit,
  onOutputGainValueChange: (Int) -> Unit,
  onOutputGainValueChangeFinished: () -> Unit,
  onBassBoostEnabledChange: (Boolean) -> Unit,
  onBassBoostValueChange: (Int) -> Unit,
  onBassBoostValueChangeFinished: () -> Unit,
  onVirtualizerEnabledChange: (Boolean) -> Unit,
  onVirtualizerValueChange: (Int) -> Unit,
  onVirtualizerValueChangeFinished: () -> Unit,
  onAutoHeadroomEnabledChange: (Boolean) -> Unit,
  onShowSaveProfile: () -> Unit,
  onShowManageProfiles: () -> Unit,
  onImportProfiles: () -> Unit,
) {
  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    val selectedName =
      (0 until model.profiles.size).map { model.profiles[it] }.firstOrNull { it.isSelected }?.name
        ?: (0 until model.presets.size)
          .map { model.presets[it] }
          .firstOrNull { it.isSelected }
          ?.let { it.name ?: stringResource(R.string.eq_flat) }
        ?: stringResource(R.string.eq_custom_profile)
    val equalizerLabel = stringResource(R.string.equalizer)
    ControlSheetHeader(
      stringResource(R.string.equalizer),
      stringResource(R.string.vibra_menu_profile, menuDisplayText(selectedName)),
      R.drawable.equalizer,
      trailing = {
        Switch(
          checked = model.enabled,
          onCheckedChange = onEnabledChange,
          modifier = Modifier.semantics { contentDescription = equalizerLabel },
        )
      },
    )
    MenuCard {
      Row(
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val chipColors =
          FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        FilterChip(
          selected = model.selectedProfileId == "flat",
          onClick = { onPresetClick("flat") },
          enabled = model.enabled,
          label = { Text(stringResource(R.string.eq_flat)) },
          shape = CircleShape,
          border = null,
          colors = chipColors,
        )
        repeat(model.profiles.size) { index ->
          val profile = model.profiles[index]
          FilterChip(
            selected = profile.isSelected,
            onClick = { onApplyProfile(profile.id) },
            enabled = model.enabled,
            label = { Text(menuDisplayText(profile.name)) },
            shape = CircleShape,
            border = null,
            colors = chipColors,
          )
        }
        OutlinedButton(onClick = onShowSaveProfile, enabled = model.enabled) {
          Icon(painterResource(R.drawable.add), null, Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(stringResource(R.string.save))
        }
        repeat(model.presets.size) { index ->
          val preset = model.presets[index]
          if (preset.id != "flat")
            FilterChip(
              selected = preset.isSelected,
              onClick = { onPresetClick(preset.id) },
              enabled = model.enabled,
              label = { Text(menuDisplayText(preset.name.orEmpty())) },
              shape = CircleShape,
              border = null,
              colors = chipColors,
            )
        }
      }
    }
    MenuCard {
      BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        val cell = maxOf(48.dp, maxWidth / model.bands.size.coerceAtLeast(1))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
          repeat(model.bands.size) { index ->
            val band = model.bands[index]
            EqualizerBandControl(
              band.levelMb,
              model.minimumBandLevelMb,
              model.maximumBandLevelMb,
              shortFrequency(band.centerFrequencyHz),
              shortDecibels(band.levelMb),
              model.enabled,
              onValueChange = { onBandValueChange(index, it) },
              onFinished = onBandValueChangeFinished,
              modifier = Modifier.width(cell),
            )
          }
        }
      }
    }
    MenuCard {
      Column(Modifier.padding(20.dp)) {
        EqualizerStrengthRow(
          stringResource(R.string.vibra_menu_bass),
          model.bassBoostStrength,
          model.bassBoostEnabled,
          model.enabled,
          onBassBoostEnabledChange,
          onBassBoostValueChange,
          onBassBoostValueChangeFinished,
        )
        EqualizerStrengthRow(
          stringResource(R.string.eq_virtualizer),
          model.virtualizerStrength,
          model.virtualizerEnabled,
          model.enabled,
          onVirtualizerEnabledChange,
          onVirtualizerValueChange,
          onVirtualizerValueChangeFinished,
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      VibraDialogButton(onClick = onResetBands, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.reset))
      }
      VibraDialogButton(onClick = onDismiss, primary = true, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.vibra_menu_done))
      }
    }
    MenuCard {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleSlider(
          title = stringResource(R.string.eq_output_gain),
          description = stringResource(R.string.vibra_menu_output_gain_description),
          enabled = model.outputGainEnabled,
          controlsEnabled = model.enabled && !model.autoHeadroomEnabled,
          value = model.outputGainMb,
          valueRange = -1500..1500,
          valueLabel = formatDecibels(model.outputGainMb),
          onEnabledChange = onOutputGainEnabledChange,
          onValueChange = onOutputGainValueChange,
          onValueChangeFinished = onOutputGainValueChangeFinished,
        )
        SettingsToggle(
          stringResource(R.string.vibra_menu_headroom_title),
          stringResource(R.string.vibra_menu_headroom_description),
          model.autoHeadroomEnabled,
          model.enabled,
          onAutoHeadroomEnabledChange,
        )
      }
      MediaMenuRow(
        headlineContent = { Text(stringResource(R.string.eq_profiles)) },
        modifier = Modifier.clickable { onShowManageProfiles() },
      )
      MediaMenuRow(
        headlineContent = { Text(stringResource(R.string.eq_import)) },
        modifier = Modifier.clickable { onImportProfiles() },
      )
      MediaMenuRow(
        headlineContent = { Text(stringResource(R.string.vibra_menu_system_equalizer)) },
        modifier = Modifier.clickable { onOpenSystemEqualizer() },
      )
    }
  }
}

@Composable
private fun EqualizerStrengthRow(
  title: String,
  value: Int,
  effectEnabled: Boolean,
  enabled: Boolean,
  onEnabledChange: (Boolean) -> Unit,
  onValueChange: (Int) -> Unit,
  onFinished: () -> Unit,
) {
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, modifier = Modifier.weight(1f))
      Text(
        "${if (effectEnabled) value / 10 else 0}%",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    ReferenceSlider(
      value = (if (effectEnabled) value else 0).toFloat(),
      label = title,
      valueLabel = "${if (effectEnabled) value / 10 else 0}%",
      valueRange = 0f..1000f,
      enabled = enabled,
      onValueChange = {
        if ((it > 0) != effectEnabled) onEnabledChange(it > 0)
        onValueChange(it.roundToInt())
      },
      onValueChangeFinished = onFinished,
    )
  }
}

@Composable
private fun ToggleSlider(
  title: String,
  description: String,
  enabled: Boolean,
  controlsEnabled: Boolean,
  value: Int,
  valueRange: IntRange,
  valueLabel: String,
  onEnabledChange: (Boolean) -> Unit,
  onValueChange: (Int) -> Unit,
  onValueChangeFinished: () -> Unit,
) {
  Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = title, style = MaterialTheme.typography.titleMedium)
          Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = enabled,
          onCheckedChange = onEnabledChange,
          enabled = controlsEnabled,
          modifier = Modifier.vibraControlFeedback(enabled = controlsEnabled),
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ReferenceSlider(
          value = value.toFloat(),
          label = title,
          valueLabel = valueLabel,
          onValueChange = { onValueChange(it.roundToInt()) },
          onValueChangeFinished = onValueChangeFinished,
          enabled = controlsEnabled && enabled,
          valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
          modifier = Modifier.weight(1f),
        )
        ValuePill(valueLabel)
      }
    }
  }
}

@Composable
private fun SettingsToggle(
  title: String,
  description: String,
  checked: Boolean,
  enabled: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  SegmentedListItem(
    onClick = { onCheckedChange(!checked) },
    enabled = enabled,
    shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
    colors =
      ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    supportingContent = { Text(text = description) },
    content = { Text(text = title) },
  )
}

@Composable
private fun ValuePill(value: String) {
  Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
    Text(
      text = value,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
  }
}

@Composable
private fun SaveProfileDialog(
  name: String,
  onNameChange: (String) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.eq_save_profile)) },
    text = {
      TextField(
        shape = VibraFieldShape,
        colors = vibraTextFieldColors(),
        value = name,
        onValueChange = onNameChange,
        label = { Text(text = stringResource(R.string.eq_profile_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      VibraDialogButton(primary = true, onClick = onSave, enabled = name.isNotBlank()) {
        Text(text = stringResource(R.string.save))
      }
    },
    dismissButton = {
      VibraDialogButton(onClick = onDismiss) { Text(text = stringResource(R.string.eq_close)) }
    },
  )
}

@Composable
private fun ManageProfilesDialog(
  profiles: moe.rukamori.archivetune.viewmodels.EqualizerProfileUiModels,
  onApply: (String) -> Unit,
  onDelete: (String) -> Unit,
  onExport: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.eq_profiles)) },
    text = {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(count = profiles.size, key = { profiles[it].id }, contentType = { "profile" }) { index
          ->
          ProfileRow(profiles[index], onApply, onDelete, onExport)
        }
      }
    },
    confirmButton = {
      VibraDialogButton(primary = true, onClick = onDismiss) {
        Text(text = stringResource(R.string.eq_close))
      }
    },
  )
}

@Composable
private fun ProfileRow(
  profile: EqualizerProfileUiModel,
  onApply: (String) -> Unit,
  onDelete: (String) -> Unit,
  onExport: (String) -> Unit,
) {
  SegmentedListItem(
    onClick = { onApply(profile.id) },
    shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
    colors =
      ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    leadingContent = {
      Icon(
        painter =
          painterResource(if (profile.isSelected) R.drawable.check else R.drawable.equalizer),
        contentDescription = null,
      )
    },
    trailingContent = {
      Row {
        IconButton(onClick = { onExport(profile.id) }) {
          Icon(
            painter = painterResource(R.drawable.share),
            contentDescription = stringResource(R.string.export),
          )
        }
        IconButton(onClick = { onDelete(profile.id) }) {
          Icon(
            painter = painterResource(R.drawable.delete),
            contentDescription = stringResource(R.string.delete),
          )
        }
      }
    },
    supportingContent = { Text(text = stringResource(R.string.eq_custom_profile)) },
    content = {
      Text(
        text = profile.name.ifBlank { stringResource(R.string.eq_imported_profile) },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
  )
}

@Composable
private fun formatDecibels(valueMb: Int): String =
  stringResource(R.string.eq_decibels, valueMb / 100f)

@Composable
private fun formatFrequency(frequencyHz: Int): String =
  if (frequencyHz >= 1000) {
    stringResource(R.string.eq_frequency_kilohertz, frequencyHz / 1000f)
  } else {
    stringResource(R.string.eq_frequency_hertz, frequencyHz)
  }

@Composable
private fun shortDecibels(valueMb: Int): String =
  if (valueMb == 0) "0"
  else
    java.text
      .DecimalFormat(
        "+0.#;-0.#",
        java.text.DecimalFormatSymbols.getInstance(
          androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
        ),
      )
      .format(valueMb / 100.0)

@Composable
private fun shortFrequency(hz: Int): String =
  if (hz < 1000) hz.toString()
  else
    java.text
      .DecimalFormat(
        "0.#",
        java.text.DecimalFormatSymbols.getInstance(
          androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
        ),
      )
      .format(hz / 1000.0) + "k"
