package ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

inline fun Modifier.reactiveCanvas(
    vararg keys: Any?,
    crossinline onVerticalScroll: (yDelta: Float) -> Unit = { },
    crossinline onPanZoomRotate: (pan: Offset, centroid: Offset, zoom: Float, rotationAngle: Float) -> Unit,
    crossinline onHover: (newPosition: Offset) -> Unit = { },
    crossinline onUp: (position: Offset?) -> Unit = {  _ -> },
    // down triggers before tap/long press
    crossinline onDown: (position: Offset) -> Unit = { },
    crossinline onTap: (position: Offset, pointerCount: Int) -> Unit = { _, _ -> },
//    crossinline onLongPress: (position: Offset) -> Unit = { },
//    crossinline onLongDragStart: (position: Offset) -> Unit = { },
//    crossinline onLongDrag: (delta: Offset) -> Unit = { },
//    crossinline onLongDragCancel: () -> Unit = { },
//    crossinline onLongDragEnd: () -> Unit = { },
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
                onDown = { position ->
//                    println("onDown")
                    onDown(position)
                },
                onUp = { position ->
//                    println("onUp")
                    onUp(position)
                },
                onTap = { position, pointerCount ->
//                    println("onTap(n=$pointerCount)")
                    onTap(position, pointerCount)
                },
                // NOTE: enabling long-press blocks long-press+panning which can be annoying
//                onLongPress = { position ->
//                    println("onLongPress")
//                    onLongPress(position)
//                },
//                onDoubleTap = { println("onDoubleTap") }, // double tap detection is so-so
            )
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
