package ui

import androidx.compose.foundation.gestures.PressGestureScope
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs

// NOTE: panning doesnt work on mobile browsers https://github.com/JetBrains/compose-multiplatform/issues/3491
inline fun Modifier.reactiveCanvas(
    vararg keys: Any?,
    crossinline onVerticalScroll: (yDelta: Float) -> Unit = { },
    crossinline onPanZoomRotate: (pan: Offset, centroid: Offset, zoom: Float, rotationAngle: Float) -> Unit,
    crossinline onHover: (newPosition: Offset) -> Unit = { },
    crossinline onUp: (position: Offset?) -> Unit = {  _ -> },
    // down triggers before tap/long press
    crossinline onDown: (position: Offset) -> Unit = { },
    crossinline onTap: (position: Offset, pointerCount: Int) -> Unit = { _, _ -> },
    crossinline onLongDragStart: (position: Offset) -> Unit = { },
    crossinline onLongDrag: (delta: Offset) -> Unit = { },
    crossinline onLongDragCancel: () -> Unit = { },
    crossinline onLongDragEnd: () -> Unit = { },
): Modifier =
    this
        // alternative to zooming for desktop
        .pointerInput(*keys) { // mouse wheel scrolling
            awaitEachGesture {
                val event = awaitPointerEvent(PointerEventPass.Initial) // parent->children pass
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
            detectTapGesturesCountingPointers(
                onDown = { position -> onDown(position) },
                onUp = { position ->  onUp(position) },
                onTap = { position, pointerCount ->
                    onTap(position, pointerCount)
                }
            )
//            detectTapGestures(
//                onPress = { position ->
//                    onDown(position)
//                    awaitRelease()
//                    onUp(position)
//                },
//                onTap = { position ->
//                    onTap(position)
//                }
//            )
        }
        // NOTE: the latter pointInput-s *can* consume events before passing it higher
        //  more: https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures
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
