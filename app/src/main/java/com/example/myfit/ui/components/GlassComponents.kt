package com.example.myfit.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** CompositionLocal: glass mode on/off */
val LocalGlassMode = compositionLocalOf { true } // Default to true if supported

/** CompositionLocal: shared backdrop captured from background gradient */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Wraps page content with a background gradient layer for glass effects.
 */
@Composable
fun GlassScaffoldContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glassMode = LocalGlassMode.current
    // Child pages reuse the scene background instead of allocating full-screen capture layers.
    if (LocalBackdrop.current != null) {
        Box(modifier = modifier.fillMaxSize(), content = content)
        return
    }

    if (glassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val backdrop = rememberLayerBackdrop()
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.primaryContainer
        val bg = MaterialTheme.colorScheme.background

        val gradientBrush = remember(primary, secondary, bg) {
            Brush.linearGradient(
                colors = listOf(
                    primary.copy(alpha = 0.18f),
                    secondary.copy(alpha = 0.10f),
                    bg
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }

        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(gradientBrush)
            )
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                    content()
                }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}

/**
 * Enhanced Glass Card with better readability.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.fillMaxWidth().glassPanel(shape = shape, interactive = false, tint = containerColor),
        content = content
    )
}

/**
 * A glass chat bubble for AI messages.
 */
@Composable
fun GlassChatBubble(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.glassPanel(shape = shape, interactive = false).clip(shape), content = content)
}

@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    GlassControlSurface(onClick, modifier, enabled = enabled, tint = containerColor, contentColor = contentColor) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}


@Composable
fun GlassChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassControlSurface(onClick, modifier, selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * 专门用于 Dialog 内部的 Glass 容器。
 * 解决 Dialog 独立窗口层导致 LocalBackdrop 断链的问题。
 * 不会 fillMaxSize，不影响 Dialog 的居中布局。
 */

@Composable
fun GlassDialogCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    // A dialog has its own window. Use the regular material instead of sampling another window.
    CompositionLocalProvider(LocalBackdrop provides null) {
        Box(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shape)
            .clip(shape), content = content)
    }
}
