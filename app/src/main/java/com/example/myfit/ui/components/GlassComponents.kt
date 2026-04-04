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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
        Box(modifier = Modifier.fillMaxSize()) {
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
    val glassMode = LocalGlassMode.current
    val backdrop = LocalBackdrop.current

    if (glassMode && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val isDark = isSystemInDarkTheme()
        val surfaceOverlay: DrawScope.() -> Unit = remember(isDark) {
            {
                drawRect(
                    if (isDark) Color.Black.copy(alpha = 0.25f)
                    else Color.White.copy(alpha = 0.35f)
                )
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = surfaceOverlay,
                    highlight = { Highlight.Ambient.copy(alpha = 0.4f) },
                    shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.1f)) }
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
                    if (isDark) Color.Black.copy(alpha = 0.25f)
                    else Color.White.copy(alpha = 0.35f)
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
            modifier = modifier.height(48.dp).widthIn(min = 140.dp),
            shape = Capsule(),
            color = if (enabled) containerColor.copy(alpha = 0.85f) else Color.Gray.copy(alpha = 0.3f),
            contentColor = if (enabled) contentColor else Color.White.copy(alpha = 0.5f),
            border = if (enabled) BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)) else null
        ) {
            Box(modifier = Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp).widthIn(min = 140.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
        ) {
            Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glassMode = LocalGlassMode.current
    val backdrop = LocalBackdrop.current

    if (glassMode && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Surface(
            onClick = onClick,
            modifier = modifier.height(38.dp),
            shape = Capsule(),
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.15f),
            border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    } else {
        FilterChip(selected = selected, onClick = onClick, label = { Text(text) }, modifier = modifier.height(38.dp))
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
    val glassMode = LocalGlassMode.current

    if (glassMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val backdrop = rememberLayerBackdrop()
        val isDark = isSystemInDarkTheme()

        val gradientBrush = remember(isDark) {
            if (isDark) Brush.linearGradient(
                colors = listOf(Color.White.copy(0.07f), Color.White.copy(0.03f)),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            ) else Brush.linearGradient(
                colors = listOf(Color.White.copy(0.55f), Color.White.copy(0.25f)),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }

        val surfaceOverlay: DrawScope.() -> Unit = remember(isDark) {
            {
                drawRect(
                    if (isDark) Color.Black.copy(alpha = 0.18f)
                    else Color.White.copy(alpha = 0.45f)
                )
            }
        }

        // 第一层：提供 backdrop 捕获源（局部渐变背景）
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop)
                    .clip(shape)
                    .background(gradientBrush)
            )
            // 第二层：GlassCard 效果，backdrop 由上方局部层捕获
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                lens(10f.dp.toPx(), 20f.dp.toPx())
                            },
                            onDrawSurface = surfaceOverlay,
                            highlight = { Highlight.Ambient.copy(alpha = 0.5f) },
                            shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.15f)) }
                        )
                )
                Box(modifier = Modifier.clip(shape), content = content)
            }
        }
    } else {
        // Fallback：用更亮的 surface 而非 surfaceVariant，减轻 scrim 压暗效果
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(content = content)
        }
    }
}
