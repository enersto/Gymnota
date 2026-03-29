package com.example.myfit.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** CompositionLocal: glass mode on/off */
val LocalGlassMode = compositionLocalOf { true } // Default to true if supported

/** CompositionLocal: shared backdrop captured from background gradient */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Wraps page content with a background gradient layer for glass effects.
 * The gradient is captured by layerBackdrop; page content is a sibling (not child).
 */
@Composable
fun GlassScaffoldContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glassMode = LocalGlassMode.current

    if (glassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val backdrop = rememberLayerBackdrop()
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.primaryContainer
        val bg = MaterialTheme.colorScheme.background

        // Gradient colors for the background layer
        val gradientBrush = remember(primary, secondary, bg) {
            Brush.linearGradient(
                colors = listOf(
                    primary.copy(alpha = 0.3f),
                    secondary.copy(alpha = 0.2f),
                    bg
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }

        Box(modifier = modifier.fillMaxSize()) {
            // Layer 1: Background gradient captured by layerBackdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 关键：确保 layerBackdrop 捕获的是全屏背景
                    .layerBackdrop(backdrop)
                    .background(gradientBrush)
            )

            // Layer 2: Actual page content (sibling, not child of layerBackdrop)
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalBackdrop provides backdrop
                ) {
                    content()
                }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * A card that renders as frosted glass (blurring the background gradient)
 * when glass mode is on, or as a normal Material3 Card when off.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit
) {
    val glassMode = LocalGlassMode.current
    val backdrop = LocalBackdrop.current

    if (glassMode && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val isDark = isSystemInDarkTheme()
        val surfaceOverlay: DrawScope.() -> Unit = remember(isDark) {
            {
                drawRect(
                    if (isDark) Color.Black.copy(alpha = 0.15f)
                    else Color.White.copy(alpha = 0.2f)
                )
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(32f.dp) },
                    effects = {
                        vibrancy()
                        lens(16f.dp.toPx(), 32f.dp.toPx())
                    },
                    onDrawSurface = surfaceOverlay
                ),
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Box(content = content)
        }
    }
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
    val glassMode = LocalGlassMode.current
    val backdrop = LocalBackdrop.current

    if (glassMode && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val isDark = isSystemInDarkTheme()
        val surfaceOverlay: DrawScope.() -> Unit = remember(isDark) {
            {
                drawRect(
                    if (isDark) Color.Black.copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.25f)
                )
            }
        }

        Box(
            modifier = modifier
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        lens(8f.dp.toPx(), 16f.dp.toPx())
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.6f) },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.06f)) },
                    onDrawSurface = surfaceOverlay
                ),
            content = content
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            content = content
        )
    }
}

/**
 * A Liquid Glass styled button.
 */
@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    onClick: () -> Unit
) {
    val glassMode = LocalGlassMode.current
    val backdrop = LocalBackdrop.current

    if (glassMode && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp),
            shape = Capsule(),
            color = if (enabled) containerColor.copy(alpha = 0.85f) else Color.Gray.copy(alpha = 0.3f),
            contentColor = if (enabled) contentColor else Color.White.copy(alpha = 0.5f),
            border = if (enabled) BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        androidx.compose.material3.Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
