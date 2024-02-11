package ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.PI
import kotlin.math.abs

// NOTE: panning doesnt work on mobile browsers https://github.com/JetBrains/compose-multiplatform/issues/3491
inline fun Modifier.reactiveCanvas(
    vararg keys: Any?,
    crossinline onVerticalScroll: (yDelta: Float) -> Unit = { },
    crossinline onPanZoomRotate: (pan: Offset, centroid: Offset, zoom: Float, rotationAngle: Float) -> Unit,
    crossinline onHover: (newPosition: Offset) -> Unit = { },
    crossinline onUp: (position: Offset?) -> Unit = { },
    // down triggers before tap/long press
    crossinline onDown: (position: Offset) -> Unit = { },
    crossinline onTap: (position: Offset) -> Unit = { },
    crossinline onLongDragStart: (position: Offset) -> Unit = { },
    crossinline onLongDrag: (delta: Offset) -> Unit = { },
    crossinline onLongDragCancel: () -> Unit = { },
    crossinline onLongDragEnd: () -> Unit = { },
): Modifier =
    this
        // alternative to zooming for desktop
        .pointerInput(*keys) {
            awaitEachGesture {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll) {
                    val yDelta = event.changes.map { it.scrollDelta.y }.sum()
                    onVerticalScroll(yDelta)
                } else if (event.type == PointerEventType.Move) {
                    // unconsumed by long drag & transform, free hovering moves
                    onHover(event.changes.first().position)
                }
            }
        }
        .pointerInput(*keys) {
            detectTransformGestures(onUp = { onUp(it) }) { centroid, pan, zoom, rotation ->
                onPanZoomRotate(pan, centroid, zoom, rotation)
            }
        }
        .pointerInput(*keys) {
            detectTapGestures(
                onPress = {
                    onDown(it)
                    awaitRelease()
                    onUp(it)
                }
            ) { position ->
                onTap(position)
            }
        }
        // NOTE: the later pointInput-s *can* consume events before passing it higher
//        .pointerInput(*keys) {
//            detectDragGesturesAfterLongPress(
//                onDragStart = { position ->
//                    onLongDragStart(position)
//                },
//                onDrag = { change, dragAmount ->
//                    onLongDrag(dragAmount)
//                },
//                onDragCancel = {
//                    onLongDragCancel()
//                    onUp(null)
//                },
//                onDragEnd = {
//                    onLongDragEnd()
//                    onUp(null)
//                }
//            )
//        }

// had to patch the built-in function a bit to handle onUp
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
