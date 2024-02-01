package ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max

@OptIn(ExperimentalResourceApi::class)
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
//    val deleteIcon = painterResource("icons/cancel.xml")
    val deleteIcon = rememberVectorPainter(Icons.Default.Delete)
    val deleteIconS = with (LocalDensity.current) { 18.dp.toPx() }
    val deleteIconSize = Size(deleteIconS, deleteIconS)
    val backgroundColor = Color.White
    val circleColor = Color.Black
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
            if (viewModel.selectionMode.isSelectingCircles() && viewModel.showCircles)
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
            if (viewModel.showCircles)
                for (circle in viewModel.circles) {
                    drawCircle(
                        color = circleColor,
                        radius = circle.radius.toFloat(),
                        center = circle.offset,
                        style = circleStroke,
                    )
                }
            for (part in viewModel.parts) {
                drawPath(
                    viewModel.part2path(part),
                    color = part.fillColor,
                    alpha = clusterPathAlpha,
                    style = if (viewModel.showWireframes) circleStroke else circleFill,
                )
            }
            // handles
            if (viewModel.showCircles)
                when (viewModel.handles.value) {
                    // TODO: delete handle bottom left + for multi rotate bottom right
                    is Handles.SingleCircle -> {
                        val selectedCircle = viewModel.circles[viewModel.selection.single()]
                        val right = selectedCircle.offset + Offset(selectedCircle.radius.toFloat(), 0f)
                        val bottom = selectedCircle.offset + Offset(0f, selectedCircle.radius.toFloat())
                        drawLine( // radius marker
                            color = selectionMarkingsColor,
                            selectedCircle.offset,
                            right,
                        )
                        drawCircle( // radius handle
                            color = handleColor,
                            radius = handleRadius,
                            center = right
                        )
                        translate(bottom.x - deleteIconS/2, bottom.y - deleteIconS/2) {
                            with (deleteIcon) {
                                draw(deleteIconSize, colorFilter = ColorFilter.tint(Color.Red))
                            }
                        }
                    }
                    is Handles.SeveralCircles -> {
                        val selectionRect = viewModel.getSelectionRect()
                        val bottom = selectionRect.bottomCenter
                        drawRect( // selection rect
                            color = selectionMarkingsColor,
                            topLeft = selectionRect.topLeft,
                            size = selectionRect.size,
                            style = dottedStroke,
                        )
                        drawCircle( // scale handle
                            color = handleColor,
                            radius = handleRadius,
                            center = selectionRect.topRight,
                        )
                        // rotate handle + centroid
                        translate(bottom.x - deleteIconS/2, bottom.y - deleteIconS/2) {
                            with (deleteIcon) {
                                draw(deleteIconSize, colorFilter = ColorFilter.tint(Color.Red))
                            }
                        }
                    }
                }
        }
    }
}
