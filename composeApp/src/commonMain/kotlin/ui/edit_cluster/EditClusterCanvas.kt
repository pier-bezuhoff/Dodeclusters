package ui.edit_cluster

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import data.Circle
import data.PartialArgList
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.copy
import dodeclusters.composeapp.generated.resources.delete_forever
import dodeclusters.composeapp.generated.resources.delete_name
import dodeclusters.composeapp.generated.resources.duplicate_name
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.stub
import dodeclusters.composeapp.generated.resources.zoom_in
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.PathType
import ui.part2path
import ui.reactiveCanvas
import ui.theme.DodeclustersColors
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalResourceApi::class)
@Composable
fun BoxScope.EditClusterCanvas(
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
    val dottedStroke = remember { Stroke(
        width = strokeWidth,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    ) }
    // handles stuff
    val handleRadius = 8f // with (LocalDensity.current) { 8.dp.toPx() }
    val scaleIcon = painterResource(Res.drawable.zoom_in)
    val scaleHandleColor = MaterialTheme.colorScheme.secondary
    val iconDim = with (LocalDensity.current) { 20.dp.toPx() }
    val deleteIcon = painterResource(Res.drawable.delete_forever)
    val deleteIconTint = DodeclustersColors.lightRed
    val rotateIcon = painterResource(Res.drawable.rotate_counterclockwise)
    val rotateIconTint = MaterialTheme.colorScheme.secondary
    val rotationIndicatorRadius = handleRadius * 3/4
    val rotationIndicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
    val jCarcassColor = MaterialTheme.colorScheme.secondaryContainer

    val backgroundColor = MaterialTheme.colorScheme.background
    val circleColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f) // MAYBE: black for light scheme
    val selectedCircleColor = MaterialTheme.colorScheme.tertiary
    val clusterPathAlpha = 0.7f
//    val clusterPathAlpha = 1f // TODO: add a switch for this
    val selectionLinesColor = DodeclustersColors.gray
    val selectionMarkingsColor = DodeclustersColors.gray // center-radius line / bounding rect of selection
    val maxDecayAlpha = 0.2f
    val decayDuration = 1_500
    val animations: MutableMap<CircleAnimation, Animatable<Float, AnimationVector1D>> =
        remember { mutableMapOf() }
    val coroutineScope = rememberCoroutineScope()
    coroutineScope.launch { // listen to circle animations
        viewModel.circleAnimations.collect { event ->
            launch {
                val animatable = Animatable(0f)
                animations[event] = animatable
                animatable.animateTo(maxDecayAlpha, tween(decayDuration/30, easing = LinearEasing))
                animatable.animateTo(
                    targetValue = 0f,
                    tween(decayDuration*29/30, easing = LinearEasing),
//                animationSpec = tween(decayDuration, easing = CubicBezierEasing(0f, 0.7f, 0.75f, 0.55f)),
                )
                animations.remove(event) // idk, this might be bad
            }
        }
    }
    SelectionsCanvas(modifier, viewModel, selectionLinesColor, backgroundColor, selectedCircleColor, circleThiccStroke)
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
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.Offscreen, // crucial for proper alpha blending
//                renderEffect = BlurEffect(20f, 20f)
            )
    ) {
        translate(viewModel.translation.x, viewModel.translation.y) {
            drawAnimation(animations)
            drawParts(viewModel, clusterPathAlpha, circleStroke)
            if (viewModel.showCircles)
                drawCircles(viewModel, circleColor, circleStroke)
            drawPartialConstructs(viewModel, handleRadius, circleStroke, strokeWidth)
            drawHandles(viewModel, selectionMarkingsColor, scaleHandleColor, deleteIconTint, rotateIconTint, rotationIndicatorColor, handleRadius, iconDim, scaleIcon, deleteIcon, rotateIcon, dottedStroke)
        }
        if (viewModel.circleSelectionIsActive)
            drawSelectionControls(viewModel, jCarcassColor, scaleHandleColor, rotateIconTint, rotationIndicatorColor, rotationIndicatorRadius, handleRadius, iconDim, scaleIcon, rotateIcon)
    }
    if (viewModel.circleSelectionIsActive) {
        HUD(viewModel)
    }
}

@Composable
private fun SelectionsCanvas(
    modifier: Modifier,
    viewModel: EditClusterViewModel,
    selectionLinesColor: Color,
    backgroundColor: Color,
    selectedCircleColor: Color,
    circleThiccStroke: DrawStyle,
    halfNLines: Int = 200, // not all of them are visible, since we are simplifying to a square
    thiccSelectionCircleAlpha: Float = 0.4f,
) {
    Canvas(
        modifier.fillMaxSize()
            .onSizeChanged { size ->
                viewModel.canvasSize = size
            }
            // selection hatching lines, 45° SE
            .drawBehind {
                val (w, h) = size
                val maxDimension = max(w, h)
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
        translate(viewModel.translation.x, viewModel.translation.y) {
            if (viewModel.showCircles &&
                (viewModel.mode.isSelectingCircles() ||
                    viewModel.mode == SelectionMode.Region && viewModel.restrictRegionsToSelection
                )
            ) {
                val circles = viewModel.selection.map { viewModel.circles[it] }
                for (circle in circles) {
                    drawCircle( // alpha = where selection lines are shown
                        color = Color.Black,
                        radius = circle.radius.toFloat(),
                        center = circle.offset,
                        style = Fill,
                        blendMode = BlendMode.DstOut, // dst out = erase the BG rectangle => show hatching thats drawn behind it
                    )
                }
                for (circle in circles) { // MAYBE: draw circle outlines OVER parts
                    drawCircle( // thiccer lines
                        color = selectedCircleColor,
                        alpha = thiccSelectionCircleAlpha,
                        radius = circle.radius.toFloat(),
                        center = circle.offset,
                        style = circleThiccStroke,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawAnimation(
    animations: Map<CircleAnimation, Animatable<Float, AnimationVector1D>>
) {
    for ((circleAnimation, decayAlpha) in animations) {
        for (circle in circleAnimation.circles) {
            val color = when (circleAnimation) {
                is CircleAnimation.Entrance -> Color.Green
                is CircleAnimation.ReEntrance -> Color.Blue
                is CircleAnimation.Exit -> Color.Red
            }
            drawCircle(
                color = color,
                alpha = decayAlpha.value,
                radius = circle.radius,
                center = circle.center
            )
        }
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
) {
    for (part in viewModel.parts) {
//        println(part)
        val (path, pathType) = part2path(viewModel.circles, part, useChessboardPatternForOutsides = false)
        val normalizedPath = if (pathType == PathType.INVERTED) {
            val visibleRect = size.toRect()
                .inflate(100f) // slightly bigger than the screen so that the borders are invisible
                .translate(-viewModel.translation)
            val normalPath = Path()
            normalPath.addRect(visibleRect)
            normalPath.op(normalPath, path, PathOperation.Difference)
            normalPath
        } else path
        drawPath(
            normalizedPath,
            color = part.fillColor,
            alpha = clusterPathAlpha,
            style = if (viewModel.showWireframes) circleStroke else Fill,
        )
    }
}

private fun DrawScope.drawPartialConstructs(
    viewModel: EditClusterViewModel,
    handleRadius: Float,
    circleStroke: DrawStyle,
    strokeWidth: Float,
    creationPointRadius: Float = handleRadius * 3/4,
    creationPrototypeColor: Color = DodeclustersColors.green,
) {
    when (viewModel.mode) {
        ToolMode.CIRCLE_INVERSION -> viewModel.partialArgList!!.args.let { args ->
            val circles = args.map { viewModel.circles[(it as PartialArgList.Arg.CircleIndex).index] }
            if (circles.isNotEmpty()) {
                drawCircle(
                    color = creationPrototypeColor,
                    radius = circles[0].radius.toFloat(),
                    center = circles[0].offset,
                    style = circleStroke
                )
            }
            if (circles.size == 2) {
                drawCircle(
                    color = creationPrototypeColor.copy(alpha = 0.6f),
                    radius = circles[1].radius.toFloat(),
                    center = circles[1].offset,
                    style = circleStroke
                )
            }
        }
        ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> viewModel.partialArgList!!.args.let { args ->
            if (args.isNotEmpty()) {
                val center = viewModel.absolute((args[0] as PartialArgList.Arg.XYPoint).toOffset())
                drawCircle(
                    color = creationPrototypeColor,
                    radius = creationPointRadius,
                    center = center
                )
                if (args.size == 2) {
                    val radiusPoint = viewModel.absolute((args[1] as PartialArgList.Arg.XYPoint).toOffset())
                    drawCircle(
                        color = creationPrototypeColor,
                        radius = creationPointRadius,
                        center = radiusPoint
                    )
                    drawCircle(
                        color = creationPrototypeColor,
                        style = circleStroke,
                        radius = (radiusPoint - center).getDistance(),
                        center = center
                    )
                }
            }
        }
        ToolMode.CIRCLE_BY_3_POINTS -> viewModel.partialArgList!!.args.let { args ->
            val points = args.map {
                viewModel.absolute((it as PartialArgList.Arg.XYPoint).toOffset())
            }
            for (point in points)
                drawCircle(
                    color = creationPrototypeColor,
                    radius = creationPointRadius,
                    center = point
                )
            if (args.size == 2)
                drawLine(
                    color = creationPrototypeColor,
                    start = points[0],
                    end = points[1],
                    strokeWidth = strokeWidth
                )
            else if (args.size == 3) {
                try {
                    val c = Circle.by3Points(points[0], points[1], points[2])
                    drawCircle(
                        color = creationPrototypeColor,
                        radius = c.radius.toFloat(),
                        center = c.offset,
                        style = circleStroke,
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
    selectionMarkingsColor: Color, // for selection rect
    scaleHandleColor: Color,
    deleteIconTint: Color,
    rotateIconTint: Color,
    rotationIndicatorColor: Color,
    handleRadius: Float,
    iconDim: Float,
    scaleIcon: Painter,
    deleteIcon: Painter,
    rotateIcon: Painter,
    dottedStroke: DrawStyle,
) {
    if (viewModel.showCircles) {
        val iconSize = Size(iconDim, iconDim)
        when (viewModel.handleConfig) {
            is HandleConfig.SingleCircle -> {
                val selectedCircle = viewModel.circles[viewModel.selection.single()]
                val right = selectedCircle.offset + Offset(selectedCircle.radius.toFloat(), 0f)
                drawLine( // radius marker
                    color = selectionMarkingsColor,
                    start = selectedCircle.offset,
                    end = right,
                )
                drawCircle( // radius handle
                    color = scaleHandleColor,
                    radius = handleRadius,
                    center = right
                )
            }

            is HandleConfig.SeveralCircles -> {
                val selectionRect = viewModel.getSelectionRect()
                val bottom = selectionRect.bottomCenter
                drawRect( // selection rect
                    color = selectionMarkingsColor,
                    topLeft = selectionRect.topLeft,
                    size = selectionRect.size,
                    style = dottedStroke,
                )
                // scale handle
                drawCircle(
                    color = scaleHandleColor,
                    radius = handleRadius/4f,
                    center = selectionRect.topRight,
                )
                translate(selectionRect.right - iconDim/2, selectionRect.top - iconDim/2) {
                    with (scaleIcon) {
                        draw(iconSize, colorFilter = ColorFilter.tint(scaleHandleColor))
                    }
                }
                // rotate handle icon
                drawCircle( // scale handle
                    color = rotateIconTint,
                    radius = handleRadius/4f,
                    center = selectionRect.bottomRight,
                )
                translate(selectionRect.right - iconDim/2, selectionRect.bottom - iconDim/2) {
                    with (rotateIcon) {
                        draw(iconSize, colorFilter = ColorFilter.tint(rotateIconTint))
                    }
                }
                (viewModel.submode as? SubMode.Rotate)?.let { submode ->
                    submode.lastPos?.let {
                        val d = it - submode.center
                        val maxDim = viewModel.canvasSize.run { max(width, height) }
                        val sameDirectionFarAway = submode.center + d / d.getDistance() * maxDim.toFloat()
                        drawLine(
                            color = rotationIndicatorColor,
                            start = submode.center,
                            end = sameDirectionFarAway,
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.HUD(viewModel: EditClusterViewModel) {
    val rightMargin = (viewModel.canvasSize.width * SelectionControlsPosition.RELATIVE_RIGHT_MARGIN).dp
    val bottomMargin = (viewModel.canvasSize.height * SelectionControlsPosition.RELATIVE_TOP_MARGIN).dp
    // infinity button to the left-center
    // + a way to trigger a visual effect over it
//    SimpleButton(
//        painterResource(Res.drawable.infinity),
//        stringResource(Res.string.stub),
//        Modifier.align(Alignment.CenterStart)
//    ) {}

//    SimpleButton(
//        painterResource(Res.drawable.zoom_in),
//        stringResource(Res.string.stub),
//        Modifier
//            .align(Alignment.TopEnd)
//            .offset(x = -rightMargin, y = bottomMargin),
//        tint = MaterialTheme.colorScheme.secondary
//    ) {}
//    SimpleButton(
//        painterResource(Res.drawable.rotate_counterclockwise),
//        stringResource(Res.string.stub),
//        Modifier
//            .align(Alignment.BottomEnd)
//            .offset(x = -rightMargin, y = -bottomMargin),
//        tint = MaterialTheme.colorScheme.secondary
//    ) {}

    // duplicate & delete buttons
    SimpleButton(
        painterResource(Res.drawable.copy),
        stringResource(Res.string.duplicate_name),
        Modifier
            .align(Alignment.CenterEnd)
            .offset(x = -rightMargin + 30.dp),
        tint = DodeclustersColors.skyBlue.copy(alpha = 0.8f)
    ) { viewModel.duplicateCircles() }
    SimpleButton(
        painterResource(Res.drawable.delete_forever),
        stringResource(Res.string.delete_name),
        Modifier
            .align(Alignment.BottomEnd)
            .offset(
                x = -rightMargin - bottomMargin,
                y = -bottomMargin + 30.dp
            ),
        tint = DodeclustersColors.lightRed.copy(alpha = 0.8f)
    ) { viewModel.deleteCircles() }
}

fun DrawScope.drawSelectionControls(
    viewModel: EditClusterViewModel,
    jCarcassColor: Color,
    scaleHandleColor: Color,
    rotateHandleColor: Color,
    rotationIndicatorColor: Color,
    rotationIndicatorRadius: Float,
    handleRadius: Float,
    iconDim: Float,
    scaleIcon: Painter,
    rotateIcon: Painter,
) {
    val iconSize = Size(iconDim, iconDim)
    val (w, h) = viewModel.canvasSize
    val right = w * (1 - SelectionControlsPosition.RELATIVE_RIGHT_MARGIN)
    val top = h * SelectionControlsPosition.RELATIVE_TOP_MARGIN
    val cornerRadius = h * SelectionControlsPosition.REALTIVE_CORNER_RADIUS
    val bottom = h * (1 - SelectionControlsPosition.RELATIVE_TOP_MARGIN)
    val left = w * (1 - SelectionControlsPosition.RELATIVE_RIGHT_MARGIN) - h * SelectionControlsPosition.RELATIVE_TOP_MARGIN
    val cornerRect = Rect(Offset(right - cornerRadius, bottom - cornerRadius), cornerRadius)
    // J-shaped carcass (always active when selection isn't empty)
    val jPath = Path().apply {
        moveTo(right, top)
        lineTo(right, bottom - cornerRadius)
        arcTo(cornerRect, 0f, 90f, forceMoveTo = true)
        lineTo(left, bottom)
    }
    drawPath(
        jPath, jCarcassColor,
        style = Stroke(24f, cap = StrokeCap.Round)
    )
    translate(right - iconDim/2f, top - iconDim/2f) {
        with (scaleIcon) {
            draw(iconSize, colorFilter = ColorFilter.tint(scaleHandleColor))
        }
    }
    translate(right - iconDim/2f, bottom - iconDim/2f) {
        with (rotateIcon) {
            draw(iconSize, colorFilter = ColorFilter.tint(rotateHandleColor))
        }
    }
    // when needed, draw rotation indicator
    // when in rotation mode, draw rotation anchor/center

    // potentially add custom fields to specify angle & scale manually
    // selection rect's scale & rotate handles are also always active
    // tho when they aren't visible they can't be grabbed obv
}

//            .
//            .
//           (+)   [scale up]
//            |
//            |
//            ^
//            O    [scale slider]
//            v
//            |
//            |
//           (-)   [scale down]
//            .
//            | x2 [copy]
//            |
//        ____J
//      [x]   . R  [rotation handle]
//            .
object SelectionControlsPosition {
    val RELATIVE_RIGHT_MARGIN = 0.15f // = 15% of W
    val RELATIVE_TOP_MARGIN = 0.10f // = 10% of H
    val RELATIVE_SCALE_SLIDER_HEIGHT = 0.5f // = 50% of H
    val RELATIVE_SCALE_SLIDER_TO_ROTATE_ARC_INDENTION = 0.05f // = 5% of H
    val REALTIVE_CORNER_RADIUS = 0.1f // 10% of minDim
    // NE: scale handle
    // E: copy button
    // SE: rotate handle + (screen) center indicator & rotation indicator when it's grabbed
    // S: delete button
}