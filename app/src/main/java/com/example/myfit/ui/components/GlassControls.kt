package com.example.myfit.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule

@Composable
fun Modifier.glassPanel(
    shape: Shape = Capsule(),
    selected: Boolean = false,
    interactive: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary
): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val backdrop = LocalBackdrop.current
    val overlay by animateColorAsState(
        if (selected) tint.copy(alpha = if (dark) 0.28f else 0.18f)
        else colors.surface.copy(alpha = if (dark) 0.50f else 0.16f),
        tween(160), label = "glass tint"
    )
    val edge = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.65f)
    val panel = if (LocalGlassMode.current && backdrop != null && Build.VERSION.SDK_INT >= 31) {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (interactive) {
                    blur(3.dp.toPx())
                    lens(5.dp.toPx(), 8.dp.toPx())
                }
            },
            onDrawSurface = { drawRect(overlay) },
            highlight = { Highlight.Ambient.copy(alpha = if (dark) 0.25f else 0.35f) }
        )
    } else {
        background(if (selected) colors.secondaryContainer else colors.surfaceVariant, shape)
    }
    return panel.border(BorderStroke(0.75.dp, edge), shape)
}

@Composable
fun GlassControlSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = Capsule(),
    role: Role = Role.Button,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "glass press")
    val foreground = contentColor.copy(alpha = if (enabled) contentColor.alpha else 0.38f)
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassPanel(shape, selected == true, tint = tint)
            .clip(shape)
            .semantics {
                if (selected != null) {
                    if (role == Role.Checkbox) toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                    else this.selected = selected
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) { content() }
    }
}

@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.glassPanel(interactive = false).padding(3.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            GlassControlSurface(
                onClick = { if (index != selectedIndex) onSelected(index) },
                selected = index == selectedIndex,
                role = Role.Tab
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "glass switch position")
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "glass switch press")
    val colors = MaterialTheme.colorScheme
    val thumb by animateColorAsState(
        if (checked) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.75f),
        tween(180), label = "glass switch thumb"
    )
    Box(
        modifier.size(width = 64.dp, height = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.38f }
            .semantics { if (label != null) contentDescription = label }
            .toggleable(
                value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = interaction, indication = null,
                onValueChange = onCheckedChange
            )
    ) {
        Box(Modifier.align(Alignment.Center).size(width = 60.dp, height = 34.dp)
            .glassPanel(selected = checked))
        Box(Modifier.align(Alignment.CenterStart).size(26.dp)
            .graphicsLayer { translationX = (6f + 26f * progress).dp.toPx() }
            .background(thumb, Capsule())
            .border(0.75.dp, colors.surface.copy(alpha = 0.6f), Capsule()))
    }
}
