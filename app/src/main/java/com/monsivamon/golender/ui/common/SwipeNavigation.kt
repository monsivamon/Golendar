package com.monsivamon.golender.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// ── カレンダー共通スワイプ設定 ──

// 月・週・日すべてで同じ操作感にするためのプリセット。
// ここを変更すれば全カレンダー画面に一括反映される。
object CalendarSwipeDefaults {
    const val REQUIRED_SWIPES = 2
    const val RESET_TIMEOUT_MILLIS = 1000L
    const val IGNORE_CONSUMPTION = true
    const val THRESHOLD_DP = 60f
    const val VELOCITY_THRESHOLD = 1500f
    const val FLING_MAX_DURATION_MILLIS = 250L
}

// カレンダー用スワイプの共通エントリポイント。
// 月・週・日すべてがこれを使うことで、操作感が完全に統一される。
fun Modifier.swipeToNavigateCalendar(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
): Modifier = swipeToNavigate(
    onSwipeUp = onSwipeUp,
    onSwipeDown = onSwipeDown,
    threshold = CalendarSwipeDefaults.THRESHOLD_DP,
    velocityThreshold = CalendarSwipeDefaults.VELOCITY_THRESHOLD,
    flingMaxDurationMillis = CalendarSwipeDefaults.FLING_MAX_DURATION_MILLIS,
    requiredSwipes = CalendarSwipeDefaults.REQUIRED_SWIPES,
    resetTimeoutMillis = CalendarSwipeDefaults.RESET_TIMEOUT_MILLIS,
    ignoreConsumption = CalendarSwipeDefaults.IGNORE_CONSUMPTION,
)

// ── 汎用スワイプModifier ──

// 縦スワイプで前後の期間へ移動するModifier。
fun Modifier.swipeToNavigate(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    threshold: Float = 60f,
    velocityThreshold: Float = 1500f,
    flingMaxDurationMillis: Long = 250L,
    requiredSwipes: Int = 1,
    resetTimeoutMillis: Long = 1000L,
    ignoreConsumption: Boolean = false,
): Modifier = composed {
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)

    this.pointerInput(
        threshold, velocityThreshold, flingMaxDurationMillis,
        requiredSwipes, resetTimeoutMillis, ignoreConsumption,
    ) {
        val thresholdPx = threshold.dp.toPx()

        var swipeCount = 0
        var lastDirection = 0
        var lastSwipeTime = 0L

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val velocityTracker = VelocityTracker()

            var accumulated = 0f
            var lastUptime = down.uptimeMillis
            // 子（LazyColumn等）が消費していないドラッグだけを速度追跡の対象とする。
            // 消費されたドラッグは「スクロール」とみなし、フリック判定から除外する。
            var trackingStarted = false
            val pointerId = down.id

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) break

                lastUptime = change.uptimeMillis

                if (ignoreConsumption || !change.isConsumed) {
                    if (!trackingStarted) {
                        trackingStarted = true
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                    }
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    accumulated += change.position.y - change.previousPosition.y
                }
            }

            val velocityY = if (trackingStarted) velocityTracker.calculateVelocity().y else 0f
            val duration = lastUptime - down.uptimeMillis

            val distanceDirection = when {
                accumulated <= -thresholdPx -> 1
                accumulated >= thresholdPx -> -1
                else -> 0
            }

            val isFling = trackingStarted &&
                    duration in 1..flingMaxDurationMillis &&
                    abs(velocityY) >= velocityThreshold
            val flingDirection = if (isFling) {
                if (velocityY < 0) 1 else -1
            } else 0

            val direction = if (distanceDirection != 0) distanceDirection else flingDirection
            if (direction == 0) return@awaitEachGesture

            val now = System.currentTimeMillis()
            if (direction == lastDirection && now - lastSwipeTime < resetTimeoutMillis) {
                swipeCount++
            } else {
                swipeCount = 1
                lastDirection = direction
            }
            lastSwipeTime = now

            if (swipeCount >= requiredSwipes) {
                swipeCount = 0
                lastDirection = 0
                if (direction == 1) currentOnSwipeUp() else currentOnSwipeDown()
            }
        }
    }
}

// ── 横スワイプ（タブ切替） ──

fun Modifier.swipeToNavigateHorizontal(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    threshold: Float = 60f,
): Modifier = composed {
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)

    this.pointerInput(threshold) {
        val thresholdPx = threshold.dp.toPx()
        var accumulated = 0f

        detectHorizontalDragGestures(
            onDragStart = { accumulated = 0f },
            onDragCancel = { accumulated = 0f },
            onDragEnd = {
                when {
                    accumulated <= -thresholdPx -> currentOnSwipeLeft()
                    accumulated >= thresholdPx -> currentOnSwipeRight()
                }
                accumulated = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
                accumulated += dragAmount
            },
        )
    }
}

// ── 期間切替トランジション ──

fun <T> AnimatedContentTransitionScope<T>.slideVertical(
    isForward: Boolean,
    durationMillis: Int = 250,
): ContentTransform {
    val slideSpec = tween<IntOffset>(durationMillis, easing = FastOutSlowInEasing)
    val fadeSpec = tween<Float>(durationMillis, easing = FastOutSlowInEasing)

    return if (isForward) {
        (slideInVertically(slideSpec) { it } + fadeIn(fadeSpec)) togetherWith
                (slideOutVertically(slideSpec) { -it } + fadeOut(fadeSpec))
    } else {
        (slideInVertically(slideSpec) { -it } + fadeIn(fadeSpec)) togetherWith
                (slideOutVertically(slideSpec) { it } + fadeOut(fadeSpec))
    }
}