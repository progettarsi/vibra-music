/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package app.vibra.ui.motion
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
fun VibraButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, shapes: ButtonShapes? = null, colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.Button(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.Button(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
}

@Composable
fun VibraFilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, shapes: ButtonShapes? = null, colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.FilledTonalButton(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.FilledTonalButton(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
}

@Composable
fun VibraTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, shapes: ButtonShapes? = null, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.TextButton(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.TextButton(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
}

@Composable
fun VibraOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, shapes: ButtonShapes? = null, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.OutlinedButton(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
}

@Composable
fun VibraElevatedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, shapes: ButtonShapes? = null, colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.elevatedButtonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.ElevatedButton(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.ElevatedButton(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
}

@Composable
fun VibraIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.standardShape, shapes: IconButtonShapes? = null,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.IconButton(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.IconButton(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, interactionSource = interactionSource, content = content)
}

@Composable
fun VibraFilledIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape, shapes: IconButtonShapes? = null,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.FilledIconButton(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.FilledIconButton(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, interactionSource = interactionSource, content = content)
}

@Composable
fun VibraFilledTonalIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape, shapes: IconButtonShapes? = null,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.FilledTonalIconButton(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.FilledTonalIconButton(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, interactionSource = interactionSource, content = content)
}

@Composable
fun VibraOutlinedIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.outlinedShape, shapes: IconButtonShapes? = null,
    colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(), border: BorderStroke? = IconButtonDefaults.outlinedIconButtonBorder(enabled),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val bounce = rememberTapBounce()
    val feedback = modifier.then(if (enabled) Modifier.tapBounce(bounce) else Modifier)
    if (shapes == null) androidx.compose.material3.OutlinedIconButton(onClick = onClick, modifier = feedback, enabled = enabled,
        shape = shape, colors = colors, border = border, interactionSource = interactionSource, content = content)
    else androidx.compose.material3.OutlinedIconButton(onClick = onClick, shapes = shapes, modifier = feedback, enabled = enabled,
        colors = colors, border = border, interactionSource = interactionSource, content = content)
}
