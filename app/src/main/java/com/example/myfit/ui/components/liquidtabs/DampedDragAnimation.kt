package com.example.myfit.ui.components.liquidtabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
) {
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val pressAnimation = Animatable(0f)
    private val scaleXAnimation = Animatable(initialScale)
    private val scaleYAnimation = Animatable(initialScale)
    private var requestedValue by mutableFloatStateOf(initialValue)
    private var motionJob: Job? = null
    private var pressJob: Job? = null

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / range
    val targetValue: Float get() = requestedValue
    val pressProgress: Float get() = pressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = valueAnimation.velocity / range
    private val range: Float get() = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { onDragStarted(it.position); press() },
            onDragEnd = { onDragStopped(); release() },
            onDragCancel = { onDragStopped(); release() }
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    fun press() = animatePress(true)
    fun release() = animatePress(false)

    private fun animatePress(pressed: Boolean) {
        pressJob?.cancel()
        pressJob = animationScope.launch {
            launch { pressAnimation.animateTo(if (pressed) 1f else 0f, spring(1f, 800f)) }
            launch { scaleXAnimation.animateTo(if (pressed) pressedScale else initialScale, spring(0.8f, 350f)) }
            launch { scaleYAnimation.animateTo(if (pressed) pressedScale else initialScale, spring(0.8f, 350f)) }
        }
    }

    fun updateValue(value: Float) = moveTo(value)
    fun animateToValue(value: Float) = moveTo(value)

    private fun moveTo(value: Float) {
        val target = value.coerceIn(valueRange)
        if (target == requestedValue && (motionJob?.isActive == true || abs(this.value - target) <= visibilityThreshold)) return
        requestedValue = target
        motionJob?.cancel()
        motionJob = animationScope.launch {
            valueAnimation.animateTo(target, spring(1f, 1000f, visibilityThreshold))
        }
    }
}
