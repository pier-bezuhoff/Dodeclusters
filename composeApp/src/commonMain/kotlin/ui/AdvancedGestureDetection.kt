package ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.coroutineScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

// patched detectTransformGestures to handle onUp
/**
 * A gesture detector for rotation, panning, and zoom. Once touch slop has been reached, the
 * user can use rotation, panning and zoom gestures. [onGesture] will be called when any of the
 * rotation, zoom or pan occurs, passing the rotation angle in degrees, zoom in scale factor and
 * pan as an offset in pixels. Each of these changes is a difference between the previous call
 * and the current gesture. This will consume all position changes after touch slop has
 * been reached. [onGesture] will also provide centroid of all the pointers that are down.
 *
 * If [panZoomLock] is `true`, rotation is allowed only if touch slop is detected for rotation
 * before pan or zoom motions. If not, pan and zoom gestures will be detected, but rotation
 * gestures will not be. If [panZoomLock] is `false`, once touch slop is reached, all three
 * gestures are detected.
 *
 * Example Usage:
 * @sample androidx.compose.foundation.samples.DetectTransformGestures
 */
suspend fun PointerInputScope.detectTransformGestures(
    panZoomLock: Boolean = false,
    onUp: (position: Offset?) -> Unit = { },
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                if (event.changes.all { !it.pressed }) // mine
                    onUp(event.changes.firstOrNull()?.position)
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()
                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }
                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f ||
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(centroid, panChange, zoomChange, effectiveRotation)
                    }
                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

// Xiaomi on 3+ pointer events:
// "cancle motionEvent because of threeGesture detecting"
/**
 * patched detectTapGestures to additionally count max pressed pointers for onTap
 * note: onTap/pointerCount works only on Android, but Xiaomi steals 3+ pointer events
 * */
suspend fun PointerInputScope.detectTapGesturesCountingPointers(
    onDoubleTap: ((position: Offset) -> Unit)? = null,
    onLongPress: ((position: Offset) -> Unit)? = null,
    onDown: ((position: Offset) -> Unit)? = null,
    onUp: ((position: Offset) -> Unit)? = null,
    onTap: ((position: Offset, pointerCount: Int) -> Unit)? = null
) = coroutineScope {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onDown?.invoke(down.position)
        val longPressTimeout = onLongPress?.let {
            viewConfiguration.longPressTimeoutMillis
        } ?: (Long.MAX_VALUE / 2)
        var maxPressedPointerCount = 1
        var upOrCancel: PointerInputChange? = null
        try {
            // wait for first tap up or long press
            upOrCancel = withTimeout(longPressTimeout) {
                val (change, count) = waitForUpOrCancellationCountingPointers()
                maxPressedPointerCount = max(maxPressedPointerCount, count)
                change
            }
            if (upOrCancel != null) {
                upOrCancel.consume()
                onUp?.invoke(down.position)
            }
        } catch (_: PointerEventTimeoutCancellationException) {
            onLongPress?.invoke(down.position)
            maxPressedPointerCount = consumeUntilUpCountingPointers()
            onUp?.invoke(down.position)
        }
        if (upOrCancel != null) {
            // tap was successful.
            if (onDoubleTap == null) {
                onTap?.invoke(upOrCancel.position, maxPressedPointerCount) // no need to check for double-tap.
            } else {
                // check for second tap
                val secondDown = awaitSecondDown(upOrCancel)
                if (secondDown == null) {
                    onTap?.invoke(upOrCancel.position, maxPressedPointerCount) // no valid second tap started
                } else {
                    // Second tap down detected
                    onDown?.invoke(secondDown.position)
                    try {
                        // Might have a long second press as the second tap
                        withTimeout(longPressTimeout) {
                            val (secondUp, count) = waitForUpOrCancellationCountingPointers()
                            maxPressedPointerCount = max(maxPressedPointerCount, count)
                            if (secondUp != null) {
                                secondUp.consume()
                                onUp?.invoke(secondDown.position)
                                onDoubleTap(secondUp.position)
                            } else {
                                onTap?.invoke(upOrCancel.position, maxPressedPointerCount)
                            }
                        }
                    } catch (e: PointerEventTimeoutCancellationException) {
                        // The first tap was valid, but the second tap is a long press.
                        // notify for the first tap
                        onTap?.invoke(upOrCancel.position, maxPressedPointerCount)
                        // notify for the long press
                        onLongPress?.invoke(secondDown.position)
                        maxPressedPointerCount = consumeUntilUpCountingPointers()
                        onUp?.invoke(secondDown.position)
                    }
                }
            }
        }
    }
}

/**
 * Reads events in the given [pass] until all pointers are up or the gesture was canceled.
 * The gesture is considered canceled when a pointer leaves the event region, a position
 * change has been consumed or a pointer down change event was already consumed in the given
 * pass. If the gesture was not canceled, the final up change is returned or `null` if the
 * event was canceled.
 */
suspend fun AwaitPointerEventScope.waitForUpOrCancellationCountingPointers(
    pass: PointerEventPass = PointerEventPass.Main
): Pair<PointerInputChange?, Int> {
    var maxPressedPointerCount = 1
    while (true) {
        val event = awaitPointerEvent(pass)
        maxPressedPointerCount = max(maxPressedPointerCount, event.changes.count { it.pressed })
        if (event.changes.fastAll { it.changedToUp() }) {
            // All pointers are up
            return event.changes[0] to maxPressedPointerCount
        }
        if (event.changes.fastAny {
                it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding)
            }
        ) {
            return null to maxPressedPointerCount // Canceled
        }
        // Check for cancel by position consumption. We can look on the Final pass of the
        // existing pointer event because it comes after the pass we checked above.
        val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
        maxPressedPointerCount = max(maxPressedPointerCount, event.changes.count { it.pressed })
        if (consumeCheck.changes.fastAny { it.isConsumed }) {
            return null to maxPressedPointerCount
        }
    }
}

/**
 * Consumes all pointer events until nothing is pressed and then returns. This method assumes
 * that something is currently pressed.
 */
private suspend fun AwaitPointerEventScope.consumeUntilUpCountingPointers(): Int {
    var maxPressedPointerCount = 1
    do {
        val event = awaitPointerEvent()
        val pressedPointerCount = event.changes.count { it.pressed }
        maxPressedPointerCount = max(maxPressedPointerCount, pressedPointerCount)
        event.changes.fastForEach { it.consume() }
    } while (event.changes.fastAny { it.pressed })
    return maxPressedPointerCount
}

/**
 * Waits for [ViewConfiguration.doubleTapTimeoutMillis] for a second press event. If a
 * second press event is received before the time out, it is returned or `null` is returned
 * if no second press is received.
 */
private suspend fun AwaitPointerEventScope.awaitSecondDown(
    firstUp: PointerInputChange
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
    val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
    var change: PointerInputChange
    // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
    do {
        change = awaitFirstDown()
    } while (change.uptimeMillis < minUptime)
    change
}

