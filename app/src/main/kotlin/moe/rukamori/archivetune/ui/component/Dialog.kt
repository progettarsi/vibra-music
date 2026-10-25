/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vibra.ui.components.VibraDialog
import app.vibra.ui.components.VibraDialogButton
import app.vibra.ui.components.VibraDialogButtonSpacing
import app.vibra.ui.components.VibraFieldShape
import app.vibra.ui.components.vibraTextFieldColors
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R

// Vibra: every engine dialog is a VibraDialog card (spring entrance, Vibra radius and
// surface); the optional icon sits in a tonal circle and the actions are pills.
@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentScrollable: Boolean = false,
    constrainContentHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    VibraDialog(onDismissRequest = onDismiss, modifier = modifier) {
        val bodyModifier =
            when {
                contentScrollable ->
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                constrainContentHeight -> Modifier.weight(1f, fill = false)
                else -> Modifier
            }

        Column(
            horizontalAlignment = horizontalAlignment,
            modifier = bodyModifier,
        ) {
            if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer) {
                        icon()
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
            if (title != null) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)) {
                    Box(
                        Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally),
                    ) {
                        title()
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            content()
        }

        if (buttons != null) {
            Spacer(Modifier.height(24.dp))

            FlowRow(
                horizontalArrangement = VibraDialogButtonSpacing,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) flowRowScope@{
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.labelLarge,
                    ) {
                        this@flowRowScope.buttons()
                    }
                }
            }
        }
    }
}

@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    VibraDialog(onDismissRequest = onDismiss) {
        if (titleBar != null) {
            Row {
                titleBar()
            }
            Spacer(Modifier.height(16.dp))
        } else if (title != null) {
            Text(
                text = title,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
        }

        content()

        Spacer(Modifier.height(24.dp))

        FlowRow(
            horizontalArrangement = VibraDialogButtonSpacing,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (onReset != null) {
                VibraDialogButton(onClick = onReset) {
                    Text(stringResource(R.string.reset))
                }
            }
            if (onCancel != null) {
                VibraDialogButton(onClick = onCancel) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
            VibraDialogButton(onClick = onConfirm, primary = true) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    VibraDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 24.dp),
    ) {
        LazyColumn(content = content)
    }
}

@Composable
fun InfoLabel(text: String) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(4.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(), // legacy
    textFieldValue: String? = null,
    onTextFieldValueChange: ((String) -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    enabled: Boolean = true,
    dismissOnDone: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    onDone: (String) -> Unit = {},
    // new multi-field support
    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val legacyFieldState = remember { mutableStateOf(initialTextFieldValue) }
    val currentLegacyValue = textFieldValue ?: legacyFieldState.value.text

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        icon = icon,
        title = title,
        contentScrollable = true,
        buttons = {
            VibraDialogButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }

            val isValid =
                textFields?.all { isInputValid(it.second.text) }
                    ?: isInputValid(currentLegacyValue)

            VibraDialogButton(
                primary = true,
                enabled = enabled && isValid,
                onClick = {
                    if (textFields != null && onDoneMultiple != null) {
                        onDoneMultiple(textFields.map { it.second.text })
                    } else {
                        onDone(currentLegacyValue)
                    }
                    if (dismissOnDone) {
                        onDismiss()
                    }
                },
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    ) {
        Column {
            if (textFields != null) {
                textFields.forEachIndexed { index, (label, value) ->
                    TextField(
                        value = value,
                        onValueChange = { onTextFieldsChange?.invoke(index, it) },
                        placeholder = { Text(label) },
                        enabled = enabled,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        shape = VibraFieldShape,
                        colors = vibraTextFieldColors(),
                        keyboardOptions = keyboardOptions,
                        visualTransformation = visualTransformation,
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    val areFieldsValid = textFields.all { isInputValid(it.second.text) }
                                    if (enabled && areFieldsValid && onDoneMultiple != null) {
                                        onDoneMultiple(textFields.map { it.second.text })
                                        if (dismissOnDone) {
                                            onDismiss()
                                        }
                                    }
                                },
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }
            } else {
                TextField(
                    value = currentLegacyValue,
                    onValueChange = { value ->
                        if (onTextFieldValueChange != null) {
                            onTextFieldValueChange(value)
                        } else {
                            legacyFieldState.value = TextFieldValue(value)
                        }
                    },
                    placeholder = placeholder,
                    enabled = enabled,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    shape = VibraFieldShape,
                        colors = vibraTextFieldColors(),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (enabled && isInputValid(currentLegacyValue)) {
                                    onDone(currentLegacyValue)
                                    if (dismissOnDone) {
                                        onDismiss()
                                    }
                                }
                            },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                )
            }

            extraContent?.invoke()
        }
    }
}

@Composable
fun EditPlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    var nameField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }

    val canSave by remember {
        derivedStateOf { nameField.text.isNotBlank() }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
        title = { Text(text = stringResource(R.string.edit_playlist)) },
        contentScrollable = true,
        buttons = {
            VibraDialogButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
            VibraDialogButton(
                primary = true,
                enabled = canSave,
                onClick = {
                    keyboardController?.hide()
                    onSave(nameField.text.trim())
                    onDismiss()
                },
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
    ) {
        TextField(
            value = nameField,
            onValueChange = { nameField = it },
            placeholder = { Text(text = stringResource(R.string.playlist_name)) },
            singleLine = true,
            shape = VibraFieldShape,
                        colors = vibraTextFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (!canSave) return@KeyboardActions
                        keyboardController?.hide()
                        onSave(nameField.text.trim())
                        onDismiss()
                    },
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
