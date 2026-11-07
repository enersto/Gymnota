package com.example.myfit.ui.components.liquidtabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
//import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Job
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    refractContent: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.16f) else Color(0xFF121212).copy(0.38f)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val trackWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val tabWidth = with(density) { ((trackWidth - 8.dp.toPx()) / tabsCount).coerceAtLeast(1f) }

        val offsetAnimation = remember { Animatable(0f) }
        var offsetJob by remember { mutableStateOf<Job?>(null) }
        val selectionCallback by rememberUpdatedState(onTabSelected)
        val panelOffset by remember(density, trackWidth, refractContent) {
            derivedStateOf {
                val fraction = if (refractContent) (offsetAnimation.value / trackWidth).fastCoerceIn(-1f, 1f) else 0f
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }

        val dampedDragAnimation = remember(animationScope, tabWidth, isLtr, tabsCount, refractContent) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.12f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
                    if (currentIndex != targetIndex) {
                        currentIndex = targetIndex
                        selectionCallback(targetIndex)
                    }
                    animateToValue(targetIndex.toFloat())
                    offsetJob?.cancel()
                    offsetJob = animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    if (refractContent) {
                        offsetJob?.cancel()
                        offsetJob = animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                    }
                }
            )
        }

        LaunchedEffect(selectedTabIndex(), dampedDragAnimation) {
            currentIndex = selectedTabIndex()
            dampedDragAnimation.animateToValue(currentIndex.toFloat())
            dampedDragAnimation.release()
            offsetJob?.cancel()
            offsetJob = animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
        }
        


        val interactiveHighlight = remember(animationScope, dampedDragAnimation, tabWidth, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .pointerInput(tabWidth, isLtr, tabsCount) {
                    detectTapGestures(
                        onPress = {
                            dampedDragAnimation.press()
                            try { tryAwaitRelease() } finally { dampedDragAnimation.release() }
                        },
                        onTap = { offset ->
                            val tappedIndex = if (isLtr) {
                                ((offset.x - 4.dp.toPx()) / tabWidth).toInt()
                            } else {
                                tabsCount - 1 - ((offset.x - 4.dp.toPx()) / tabWidth).toInt()
                            }.coerceIn(0, tabsCount - 1)
                            if (currentIndex != tappedIndex) {
                                currentIndex = tappedIndex
                                dampedDragAnimation.animateToValue(tappedIndex.toFloat())
                                selectionCallback(tappedIndex)
                            }
                        }
                    )
                }
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(8f.dp.toPx(), 12f.dp.toPx())
                    },
                    layerBlock = if (refractContent) ({
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    }) else null,
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = { if (refractContent) content() }
        )

        // Text tabs can keep a single foreground instead of refracting a tinted duplicate.
        if (refractContent) {
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides { lerp(1f, 1.04f, dampedDragAnimation.pressProgress) }
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                val progress = if (refractContent) dampedDragAnimation.pressProgress else 0f
                                vibrancy(); blur(4f.dp.toPx())
                                lens(8f.dp.toPx() * progress, 12f.dp.toPx() * progress)
                            },
                            highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(56f.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4f.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else (tabsCount - 1f - dampedDragAnimation.value) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = if (refractContent) rememberCombinedBackdrop(backdrop, tabsBackdrop) else backdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = if (refractContent) dampedDragAnimation.pressProgress else 0f
                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = if (refractContent) dampedDragAnimation.pressProgress else 0f) },
                    shadow = { Shadow(alpha = if (refractContent) dampedDragAnimation.pressProgress else 0f) },
                    innerShadow = {
                        val progress = if (refractContent) dampedDragAnimation.pressProgress else 0f
                        InnerShadow(radius = 8f.dp * progress, alpha = progress)
                    },
                    layerBlock = if (refractContent) ({
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    }) else null,
                    onDrawSurface = {
                        val progress = if (refractContent) dampedDragAnimation.pressProgress else 0f
                        // Match the current app theme.
                        drawRect((if (isLightTheme) Color.Black else Color.White).copy(0.08f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount)
        )

        if (!refractContent) {
            // Same 64 dp bounds and 4 dp insets as the track; labels are drawn once, above the lens.
            Row(
                Modifier.align(Alignment.CenterStart)
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
