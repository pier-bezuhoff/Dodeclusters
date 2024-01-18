package ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

// NOTE: panning doesnt work on mobile browsers https://github.com/JetBrains/compose-multiplatform/issues/3491
inline fun Modifier.reactiveCanvas(
    vararg keys: Any?,
    crossinline onPanZoom: (pan: Offset, centroid: Offset, zoom: Float) -> Unit,
    crossinline onVerticalScroll: (yDelta: Float) -> Unit = { },
    // down triggers before tap/long press
    crossinline onDown: (position: Offset) -> Unit = { },
    crossinline onTap: (position: Offset) -> Unit = { },
    crossinline onLongDragStart: (position: Offset) -> Unit = { },
    crossinline onLongDrag: (delta: Offset) -> Unit,
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
//                    println("scroll")
                    onVerticalScroll(yDelta)
                }
            }
        }
        .pointerInput(*keys) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
//        println("transform")
                onPanZoom(pan, centroid, zoom)
            }
        }
        .pointerInput(*keys) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
//            println("down")
                onDown(down.position)
            }
        }
        .pointerInput(*keys) {
            detectTapGestures { position ->
//            println("tap")
                onTap(position)
            }
        }
        // NOTE: the later pointInput-s *can* consume events before passing it higher
        .pointerInput(*keys) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
//                println("long drag start")
                    onLongDragStart(position)
                },
                onDrag = { change, dragAmount ->
                    onLongDrag(dragAmount)
                },
                onDragCancel = { onLongDragCancel() },
                onDragEnd = {
//                println("long drag end")
                    onLongDragEnd()
                }
            )
        }