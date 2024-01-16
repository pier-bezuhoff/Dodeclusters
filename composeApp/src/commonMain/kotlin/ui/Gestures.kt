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

inline fun Modifier.reactiveCanvas(
    vararg keys: Any?,
    crossinline onPanZoom: (pan: Offset, centroid: Offset, zoom: Float) -> Unit,
    crossinline onVerticalScroll: (yDelta: Float) -> Unit = { },
    crossinline onDown: (position: Offset) -> Unit = { },
    crossinline onTap: (position: Offset) -> Unit = { },
    crossinline onDragStart: (position: Offset) -> Unit = { },
    crossinline onDrag: (delta: Offset) -> Unit,
    crossinline onDragEnd: () -> Unit = { },
): Modifier =
    this
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
                    onDragStart(position)
                },
                onDrag = { change, dragAmount ->
                    onDrag(dragAmount)
                },
                onDragEnd = {
//                println("long drag end")
                    onDragEnd()
                }
            )
        }