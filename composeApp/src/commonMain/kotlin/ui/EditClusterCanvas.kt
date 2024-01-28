package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun EditClusterCanvas(
    coroutineScope: CoroutineScope,
    viewModel: EditClusterViewModel,
    modifier: Modifier = Modifier
) {
    val circleStroke = remember { Stroke(width = 2f) }
    val circleThiccStroke = remember { Stroke(width = 4f) }
    val circleFill = remember { Fill }
    val dottedStroke = remember { Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    ) }
    val emptyPaint = remember { Paint() }
    val backgroundColor = Color.White
    val circleColor = Color.Black
    val clusterPartColor = Color.Cyan
    val clusterPathAlpha = 0.7f
    val selectionLinesColor = Color.Gray
    val selectionMarkingsColor = Color.DarkGray // center-radius line / bounding rect of selection
    val handleColor = Color.Gray
    val handleRadius = 8f
    val maxDecayAlpha = 0.5f
    val decayDuration = 1_500
    val decayAlpha = remember { Animatable(0f) }
    val decayingCircles by viewModel.decayingCircles.collectAsState(DecayingCircles(emptyList(), Color.White))
    coroutineScope.launch {
        viewModel.decayingCircles.collect { event ->
            decayAlpha.snapTo(maxDecayAlpha)
            decayAlpha.animateTo(
                targetValue = 0f,
//                animationSpec = tween(decayDuration, easing = LinearEasing),
                animationSpec = tween(decayDuration, easing = CubicBezierEasing(0f, 0.7f, 0.75f, 0.55f)),
            )
        }
    }
    Canvas(
        modifier.fillMaxSize()
            // selection hatching lines, 45° SE
            .drawBehind {
                val (w, h) = size
                val maxDimension = max(w, h)
                val halfNLines = 200 // not all of them are visible, since we are simplifying to a square
                val nLines = 2*halfNLines
                for (i in 0 until halfNLines) {
                    val x = (i+1).toFloat()/halfNLines*maxDimension
                    val y = (i+1).toFloat()/halfNLines*maxDimension
                    drawLine(selectionLinesColor, start = Offset(0f, y), end = Offset(x, 0f))
                    drawLine(selectionLinesColor, start = Offset(x, maxDimension), end = Offset(maxDimension, y))
                }
            }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) // crucial for proper alpha blending
    ) {
        drawRect(backgroundColor)
        // overlay w/ selected circles
        translate(viewModel.translation.value.x, viewModel.translation.value.y) {
            if (viewModel.selectionMode.isSelectingCircles() && viewModel.showCircles.value)
                for (ix in viewModel.selection) {
                    val circle = viewModel.circles[ix]
                    drawCircle( // alpha = where selection lines are shown
                        color = Color.Black,
                        radius = circle.radius.toFloat(),
                        center = circle.offset,
                        style = circleFill,
                        blendMode = BlendMode.DstOut, // dst out = eraze the BG rectangle => show hatching thats drawn behind it
                    )
                    drawCircle( // thiccer lines
                        color = circleColor,
                        alpha = 0.7f,
                        radius = circle.radius.toFloat(),
                        center = circle.offset,
                        style = circleThiccStroke,
                    )
                }
            // animations
            for (circle in decayingCircles.circles) {
                drawCircle(
                    color = decayingCircles.color,
                    alpha = decayAlpha.value,
                    radius = circle.radius.toFloat(),
                    center = circle.offset
                )
            }
        }
    }
    Canvas(
        modifier
            .reactiveCanvas(
                onTap = viewModel::onTap,
                onDown = viewModel::onDown,
                onPanZoom = viewModel::onPanZoom,
                onVerticalScroll = viewModel::onVerticalScroll,
                onLongDragStart = viewModel::onLongDragStart,
                onLongDrag = viewModel::onLongDrag,
                onLongDragCancel = viewModel::onLongDragCancel,
                onLongDragEnd = viewModel::onLongDragEnd,
            )
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) // crucial for proper alpha blending
    ) {
        translate(viewModel.translation.value.x, viewModel.translation.value.y) {
            if (viewModel.showCircles.value)
                for (circle in viewModel.circles) {
                    drawCircle(
                        color = circleColor,
                        radius = circle.radius.toFloat(),
                        center = circle.offset,
                        style = circleStroke,
                    )
                }
            // handles
            if (viewModel.showCircles.value)
                when (viewModel.handle.value) {
                    is Handle.Radius -> {
                        val selectedCircle = viewModel.circles[viewModel.selection.single()]
                        val right =
                            selectedCircle.offset + Offset(selectedCircle.radius.toFloat(), 0f)
                        drawLine(
                            color = selectionMarkingsColor,
                            selectedCircle.offset,
                            right,
                        )
                        drawCircle(
                            color = handleColor,
                            radius = handleRadius,
                            center = right
                        )
                    }

                    is Handle.Scale -> {
                        val selectionRect = viewModel.getSelectionRect()
                        drawRect(
                            color = selectionMarkingsColor,
                            topLeft = selectionRect.topLeft,
                            size = selectionRect.size,
                            style = dottedStroke,
                        )
                        drawCircle(
                            color = handleColor,
                            radius = handleRadius,
                            center = selectionRect.topRight,
                        )
                    }
                }
            for (part in viewModel.parts) {
                drawPath(
                    viewModel.part2path(part),
                    color = clusterPartColor,
                    alpha = clusterPathAlpha,
                    style = if (viewModel.showWireframes.value) circleStroke else circleFill,
                )
            }
        }
    }
}
