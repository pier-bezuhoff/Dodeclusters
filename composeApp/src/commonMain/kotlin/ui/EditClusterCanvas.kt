package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import data.Circle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max

@OptIn(ExperimentalResourceApi::class)
@Composable
fun EditClusterCanvas(
    viewModel: EditClusterViewModel,
    modifier: Modifier = Modifier
) {
    val strokeWidth = with (LocalDensity.current) { 2.dp.toPx() }
    val circleStroke = remember { Stroke(
        width = strokeWidth
    ) }
    val circleThiccStroke = remember { Stroke(
        width = 2 * strokeWidth
    ) }
    val circleFill = remember { Fill }
    val dottedStroke = remember { Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    ) }
    // handles stuff
    val handleRadius = 8f // with (LocalDensity.current) { 8.dp.toPx() }
    val scaleHandleColor = Color.Gray
    val iconDim = with (LocalDensity.current) { 18.dp.toPx() }
    val iconSize = Size(iconDim, iconDim)
//    val deleteIcon = painterResource("icons/cancel.xml")
    val deleteIcon = rememberVectorPainter(Icons.Default.Delete)
    val deleteIconTint = Color.Red
    val rotateIcon = painterResource("icons/rotate_counterclockwise.xml")
    val rotateIconTint = Color(0f, 0.5f, 0f)
    val rotationIndicatorRaidus = handleRadius * 3/4
    val rotationIndicatorColor = Color.Green.copy(alpha = 0.5f)

    val backgroundColor = Color.White
    val circleColor = Color.Black
    val clusterPathAlpha = 0.7f
    val selectionLinesColor = Color.Gray
    val selectionMarkingsColor = Color.DarkGray // center-radius line / bounding rect of selection
    val maxDecayAlpha = 0.5f
    val decayDuration = 1_500
    val decayAlpha = remember { Animatable(0f) }
    val decayingCircles by viewModel.decayingCircles.collectAsState(DecayingCircles(emptyList(), Color.White))
    val coroutineScope = rememberCoroutineScope()
    coroutineScope.launch {
        viewModel.decayingCircles.collect { event ->
            decayAlpha.snapTo(maxDecayAlpha)
            decayAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(decayDuration, easing = LinearEasing),
//                animationSpec = tween(decayDuration, easing = CubicBezierEasing(0f, 0.7f, 0.75f, 0.55f)),
            )
        }
    }
    SelectionsCanvas(modifier, viewModel, selectionLinesColor, backgroundColor, circleColor, circleFill, circleThiccStroke)
    Canvas(
        modifier
            // NOTE: turned off long press for now (also inside of reactiveCanvas)
            .reactiveCanvas(
                onTap = viewModel::onTap,
                onUp = viewModel::onUp,
                onDown = viewModel::onDown,
                onPanZoomRotate = viewModel::onPanZoomRotate,
                onVerticalScroll = viewModel::onVerticalScroll,
//                onLongDragStart = viewModel::onLongDragStart,
//                onLongDrag = viewModel::onLongDrag,
//                onLongDragCancel = viewModel::onLongDragCancel,
//                onLongDragEnd = viewModel::onLongDragEnd,
            )
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) // crucial for proper alpha blending
    ) {
        translate(viewModel.translation.value.x, viewModel.translation.value.y) {
            drawAnimation(decayingCircles, decayAlpha)
            if (viewModel.showCircles)
                drawCircles(viewModel, circleColor, circleStroke)
            drawParts(viewModel, clusterPathAlpha, circleStroke, circleFill)
            drawCreationPrototypes(viewModel, handleRadius, circleStroke, strokeWidth)
            drawHandles(viewModel, selectionMarkingsColor, scaleHandleColor, deleteIconTint, rotateIconTint, rotationIndicatorColor, rotationIndicatorRaidus, handleRadius, iconDim, iconSize, deleteIcon, rotateIcon, dottedStroke)
        }
    }
}

@Composable
private fun SelectionsCanvas(
    modifier: Modifier,
    viewModel: EditClusterViewModel,
    selectionLinesColor: Color,
    backgroundColor: Color,
    circleColor: Color,
    circleFill: DrawStyle,
    circleThiccStroke: DrawStyle,
) {
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
            if (viewModel.mode.isSelectingCircles() && viewModel.showCircles)
                for (ix in viewModel.selection) {
                    val circle = viewModel.circles[ix]
                    drawCircle( // alpha = where selection lines are shown
                        color = Color.Black,
                        radius = circle.radius.toFloat(),
                        center = circle.offset,
                        style = circleFill,
                        blendMode = BlendMode.DstOut, // dst out = erase the BG rectangle => show hatching thats drawn behind it
                    )
                    drawCircle( // thiccer lines
                        color = circleColor,
                        alpha = 0.4f,
                        radius = circle.radius.toFloat(),
                        center = circle.offset,
                        style = circleThiccStroke,
                    )
                }
        }
    }
}

private fun DrawScope.drawAnimation(
    decayingCircles: DecayingCircles,
    decayAlpha: Animatable<Float, AnimationVector1D>,
) {
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

private fun DrawScope.drawCircles(
    viewModel: EditClusterViewModel,
    circleColor: Color,
    circleStroke: DrawStyle,
) {
    for (circle in viewModel.circles) {
        drawCircle(
            color = circleColor,
            radius = circle.radius.toFloat(),
            center = circle.offset,
            style = circleStroke,
        )
    }
}

private fun DrawScope.drawParts(
    viewModel: EditClusterViewModel,
    clusterPathAlpha: Float,
    circleStroke: DrawStyle,
    circleFill: DrawStyle,
) {
    for (part in viewModel.parts) {
        drawPath(
            viewModel.part2path(part),
            color = part.fillColor,
            alpha = clusterPathAlpha,
            style = if (viewModel.showWireframes) circleStroke else circleFill,
        )
    }
}

private fun DrawScope.drawCreationPrototypes(
    viewModel: EditClusterViewModel,
    handleRadius: Float,
    circleStroke: DrawStyle,
    strokeWidth: Float,
) {
    when (val m = viewModel.mode) {
        is CreationMode.CircleByCenterAndRadius.Center ->
            m.center?.let {
                drawCircle(color = Color.Green, radius = handleRadius * 3/4, center = viewModel.absolute(it))
            }
        is CreationMode.CircleByCenterAndRadius.Radius -> {
            drawCircle(color = Color.Green, radius = handleRadius * 3/4, center = viewModel.absolute(m.center))
            m.radiusPoint?.let {
                drawCircle(
                    color = Color.Green,
                    style = circleStroke,
                    radius = (it - m.center).getDistance(),
                    center = viewModel.absolute(m.center)
                )
            }
        }
        is CreationMode.CircleBy3Points -> {
            m.points.forEach {
                drawCircle(color = Color.Green, radius = handleRadius * 3/4, center = viewModel.absolute(it))
            }
            if (m.points.size == 2)
                drawLine(
                    Color.Green,
                    start = viewModel.absolute(m.points.first()),
                    end = viewModel.absolute(m.points.last()),
                    strokeWidth
                )
            else if (m.points.size == 3) {
                try {
                    val c = Circle.by3Points(m.points[0], m.points[1], m.points[2])
                    drawCircle(
                        color = Color.Green,
                        style = circleStroke,
                        radius = c.radius.toFloat(),
                        center = viewModel.absolute(c.offset)
                    )
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                }
            }
        }
        else -> {}
    }
}

private fun DrawScope.drawHandles(
    viewModel: EditClusterViewModel,
//    selectionLinesColor: Color,
    selectionMarkingsColor: Color,
    scaleHandleColor: Color,
    deleteIconTint: Color,
    rotateIconTint: Color,
    rotationIndicatorColor: Color,
    rotationIndicatorRaidus: Float,
    handleRadius: Float,
    iconDim: Float,
    iconSize: Size,
    deleteIcon: Painter,
    rotateIcon: Painter,
    dottedStroke: DrawStyle,
) {
    if (viewModel.showCircles)
        when (viewModel.handleConfig.value) {
            // TODO: delete handle bottom left + for multi rotate bottom right
            is HandleConfig.SingleCircle -> {
                val selectedCircle = viewModel.circles[viewModel.selection.single()]
                val right = selectedCircle.offset + Offset(selectedCircle.radius.toFloat(), 0f)
                val bottom = selectedCircle.offset + Offset(0f, selectedCircle.radius.toFloat())
                drawLine( // radius marker
                    color = selectionMarkingsColor,
                    selectedCircle.offset,
                    right,
                )
                drawCircle( // radius handle
                    color = scaleHandleColor,
                    radius = handleRadius,
                    center = right
                )
                translate(bottom.x - iconDim/2, bottom.y - iconDim/2) {
                    with (deleteIcon) {
                        draw(iconSize, colorFilter = ColorFilter.tint(Color.Red))
                    }
                }
            }
            is HandleConfig.SeveralCircles -> {
                val selectionRect = viewModel.getSelectionRect()
                val bottom = selectionRect.bottomCenter
                drawRect( // selection rect
                    color = selectionMarkingsColor,
                    topLeft = selectionRect.topLeft,
                    size = selectionRect.size,
                    style = dottedStroke,
                ) // TODO: if minSize > screen maybe show a context menu with scale & rotate sliders
                // or group such tools into a transform panel
                drawCircle( // scale handle
                    color = scaleHandleColor,
                    radius = handleRadius,
                    center = selectionRect.topRight,
                )
                // rotate handle icon
                translate(selectionRect.right - iconDim/2, selectionRect.bottom - iconDim/2) {
                    with (rotateIcon) {
                        draw(iconSize, colorFilter = ColorFilter.tint(rotateIconTint))
                    }
                }
                viewModel.rotationIndicatorPosition?.let {
                    drawCircle( // rotation indicator
                        color = rotationIndicatorColor,
                        radius = rotationIndicatorRaidus,
                        center = it,
                    )
                }
                translate(bottom.x - iconDim/2, bottom.y - iconDim/2) {
                    with (deleteIcon) {
                        draw(iconSize, colorFilter = ColorFilter.tint(deleteIconTint))
                    }
                }
            }
        }
}