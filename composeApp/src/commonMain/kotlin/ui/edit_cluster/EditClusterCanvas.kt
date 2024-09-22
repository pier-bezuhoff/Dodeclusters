package ui.edit_cluster

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import data.PartialArgList
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GeneralizedCircle
import data.geometry.Line
import data.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.zoom_in
import domain.rotateBy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.SimpleButton
import ui.chessboardPath
import ui.circle2path
import ui.part2path
import ui.reactiveCanvas
import ui.theme.DodeclustersColors
import ui.tools.EditClusterTool
import ui.visibleHalfPlanePath
import kotlin.math.max
import kotlin.math.min

@Composable
fun BoxScope.EditClusterCanvas(
    viewModel: EditClusterViewModel,
    modifier: Modifier = Modifier
) {
    val strokeWidth = with (LocalDensity.current) { 2.dp.toPx() }
    val circleStroke = Stroke(
        width = strokeWidth
    )
    val circleThiccStroke = Stroke(
        width = 2 * strokeWidth
    )
    val dottedStroke = remember { Stroke(
        width = strokeWidth,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    ) }
    // handles stuff
    val handleRadius = 8f // with (LocalDensity.current) { 8.dp.toPx() }
    val pointRadius = 5f
    val scaleIcon = painterResource(Res.drawable.zoom_in)
    val scaleIconColor = MaterialTheme.colorScheme.secondary
    val scaleIndicatorColor = DodeclustersColors.skyBlue
    val iconDim = with (LocalDensity.current) { 24.dp.toPx() }
    val rotateIcon = painterResource(Res.drawable.rotate_counterclockwise)
    val rotateIconColor = MaterialTheme.colorScheme.secondary
    val rotationIndicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
    val sliderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
    val jCarcassColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    val circleColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f) // MAYBE: black for light scheme
    val pointColor = circleColor
    val selectedCircleColor =
//        MaterialTheme.colorScheme.primary
        DodeclustersColors.strongSalad
    val selectedPointColor = selectedCircleColor
    val clusterPathAlpha = 1f
        //0.7f
    val selectionMarkingsColor = DodeclustersColors.gray // center-radius line / bounding rect of selection
    val thiccSelectionCircleAlpha = 0.9f
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
//    SelectionsCanvas(modifier, viewModel, selectionLinesColor, backgroundColor, selectedCircleColor, circleThiccStroke, thiccSelectionCircleAlpha = thiccSelectionCircleAlpha)
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
            .onSizeChanged { size ->
                viewModel.changeCanvasSize(size)
            }
            .fillMaxSize()
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.Offscreen, // crucial for proper alpha blending
//                renderEffect = BlurEffect(20f, 20f)
            )
    ) {
        translate(viewModel.translation.x, viewModel.translation.y) {
            val visibleRect = size.toRect().translate(-viewModel.translation)
            drawAnimation(animations, visibleRect)
            drawParts(viewModel, visibleRect, clusterPathAlpha, circleStroke)
            if (viewModel.showCircles)
                drawCircles(viewModel, visibleRect, circleColor, circleStroke, pointColor, pointRadius)
            drawSelectedCircles(viewModel, visibleRect, selectedCircleColor, thiccSelectionCircleAlpha, circleThiccStroke, selectedPointColor, pointRadius)
            drawPartialConstructs(viewModel, visibleRect, handleRadius, circleStroke, strokeWidth)
            drawHandles(viewModel, visibleRect, selectionMarkingsColor, scaleIconColor, scaleIndicatorColor, rotateIconColor, rotationIndicatorColor, handleRadius, iconDim, scaleIcon, rotateIcon, dottedStroke)
        }
        if (viewModel.circleSelectionIsActive)
            drawSelectionControls(viewModel, sliderColor, jCarcassColor, rotateIconColor, handleRadius, iconDim, rotateIcon)
    }
    if (viewModel.circleSelectionIsActive) {
        CircleSelectionContextActions(viewModel)
    } else if (viewModel.pointSelectionIsActive) {
        PointSelectionContextActions(viewModel)
    } else if (
        viewModel.mode == ToolMode.ARC_PATH &&
        viewModel.arcPathUnderConstruction?.nArcs?.let { it >= 1 } == true
    ) {
        ArcPathContextActions(viewModel)
    }
}

@Composable
private fun SelectionsCanvas(
    modifier: Modifier,
    viewModel: EditClusterViewModel,
    selectedCircleColor: Color,
    circleThiccStroke: Stroke,
    selectionLinesColor: Color = DodeclustersColors.gray,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    halfNLines: Int = 200, // not all of them are visible, since we are simplifying to a square
    thiccSelectionCircleAlpha: Float = 0.9f,
) {
    Canvas(
        modifier.fillMaxSize()
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
            val visibleRect = size.toRect().translate(-viewModel.translation)
            if (viewModel.showCircles &&
                (viewModel.mode.isSelectingCircles() ||
                    viewModel.mode == SelectionMode.Region && viewModel.restrictRegionsToSelection
                )
            ) {
                val circles = viewModel.selection.map { viewModel.circles[it] }
                for (circle in circles) {
                    // alpha = where selection lines are shown
                    // dst out = erase the BG rectangle => show hatching thats drawn behind it
                    drawCircleOrLine(circle, visibleRect, Color.Black, blendMode = BlendMode.DstOut)
                }
                for (circle in circles) { // MAYBE: draw circle outlines OVER parts
                    drawCircleOrLine(
                        circle, visibleRect, selectedCircleColor,
                        alpha = thiccSelectionCircleAlpha,
                        style = circleThiccStroke
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCircleOrLine(
    circle: CircleOrLine,
    visibleRect: Rect,
    color: Color,
    alpha: Float = 1f,
    style: DrawStyle = Fill,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
    drawHalfPlanesForLines: Boolean = false
) {
    when (circle) {
        // TODO: when the radius is exceedingly high, circles disappear
        //  so we need to approx them with lines
        is Circle -> drawCircle(
            color, circle.radius.toFloat(), circle.center, alpha, style, blendMode = blendMode
        )
        is Line -> {
            val maxDim = visibleRect.maxDimension
            val pointClosestToScreenCenter = circle.project(visibleRect.center)
            val direction =  circle.directionVector
            val farBack = pointClosestToScreenCenter - direction * maxDim
            val farForward = pointClosestToScreenCenter + direction * maxDim
            when (style) {
                Fill -> if (drawHalfPlanesForLines) {
                    val halfPlanePath = visibleHalfPlanePath(circle, visibleRect)
                    drawPath(halfPlanePath, color, alpha, style, blendMode = blendMode)
                }
                is Stroke -> {
                    drawLine(
                        color, farBack, farForward,
                        alpha = alpha,
                        strokeWidth = style.width,
                        pathEffect = style.pathEffect,
                        blendMode = blendMode
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawAnimation(
    animations: Map<CircleAnimation, Animatable<Float, AnimationVector1D>>,
    visibleRect: Rect
) {
    val visibleScreenPath = Path().apply { addRect(visibleRect) }
    for ((circleAnimation, decayAlpha) in animations) {
        for (circle in circleAnimation.circles) {
            val color = when (circleAnimation) {
                is CircleAnimation.Entrance -> Color.Green
                is CircleAnimation.ReEntrance -> Color.Blue
                is CircleAnimation.Exit -> Color.Red
            }
            when (circle) {
                is Circle -> {
                    val path = circle2path(circle) // MAYBE: excessive, it optimizes naught
                    path.op(path, visibleScreenPath, PathOperation.Intersect)
                    drawPath(path, color, alpha = decayAlpha.value)
                }
                is Line -> {
                    val maxDim = visibleRect.maxDimension
                    val pointClosestToScreenCenter = circle.project(visibleRect.center)
                    val direction =  circle.directionVector
                    val farBack = pointClosestToScreenCenter - direction * maxDim
                    val farForward = pointClosestToScreenCenter + direction * maxDim
                    drawLine(color, farBack, farForward, strokeWidth = 20f, alpha = decayAlpha.value)
//                    val path = visibleHalfPlanePath(circle, visibleRect)
//                    drawPath(path, color, alpha = decayAlpha.value)
                }
            }
        }
    }
}

private fun DrawScope.drawCircles(
    viewModel: EditClusterViewModel,
    visibleRect: Rect,
    circleColor: Color,
    circleStroke: DrawStyle,
    pointColor: Color,
    pointRadius: Float
) {
    if (viewModel.circleSelectionIsActive) {
        for ((ix, circle) in viewModel.circles.withIndex()) {
            if (ix !in viewModel.selection)
                drawCircleOrLine(circle, visibleRect, circleColor, style = circleStroke)
        }
    } else {
        for (circle in viewModel.circles) {
            drawCircleOrLine(circle, visibleRect, circleColor, style = circleStroke)
        }
    }
    if (viewModel.pointSelectionIsActive) {
        for ((ix, point) in viewModel.points.withIndex())
            if (ix !in viewModel.selectedPoints)
                drawCircle(pointColor, pointRadius, point.toOffset())
    } else {
        for (point in viewModel.points)
            drawCircle(pointColor, pointRadius, point.toOffset())
    }
}

private fun DrawScope.drawSelectedCircles(
    viewModel: EditClusterViewModel,
    visibleRect: Rect,
    selectedCircleColor: Color,
    thiccSelectionCircleAlpha: Float,
    circleThiccStroke: Stroke,
    selectedPointColor: Color,
    pointRadius: Float
) {
    if (viewModel.showCircles &&
        (viewModel.circleSelectionIsActive ||
        viewModel.mode == SelectionMode.Region && viewModel.restrictRegionsToSelection)
    ) {
        val circles = viewModel.selection.map { viewModel.circles[it] }
        for (circle in circles) {
            drawCircleOrLine(
                circle, visibleRect, selectedCircleColor,
                alpha = thiccSelectionCircleAlpha,
                style = circleThiccStroke
            )
        }
    }
    if (viewModel.pointSelectionIsActive) {
        val points = viewModel.selectedPoints.map { viewModel.points[it] }
        for (point in points) {
            drawCircle(selectedPointColor, pointRadius, point.toOffset())
        }
    }
}

// TODO: draw fill+stroke for to avoid seams
private fun DrawScope.drawParts(
    viewModel: EditClusterViewModel,
    visibleRect: Rect,
    clusterPathAlpha: Float,
    circleStroke: DrawStyle,
) {
    // NOTE: buggy on extreme zoom-in
    if (viewModel.displayChessboardPattern) {
        if (viewModel.showWireframes) {
            for (circle in viewModel.circles)
                drawCircleOrLine(circle, visibleRect, viewModel.regionColor, style = circleStroke)
        } else {
            if (viewModel.invertedChessboard)
                drawRect(viewModel.regionColor, visibleRect.topLeft, visibleRect.size)
            for (circle in viewModel.circles) { // it used to work poorly but is good now for some reason
                drawCircleOrLine(circle, visibleRect, viewModel.regionColor, blendMode = BlendMode.Xor, drawHalfPlanesForLines = true)
            }
        }
    } else {
        for (part in viewModel.parts) {
            val path = part2path(viewModel.circles, part, visibleRect)
            drawPath(
                path,
                color = part.fillColor,
                alpha = clusterPathAlpha,
                style = if (viewModel.showWireframes) circleStroke else Fill,
            )
        }
    }
}

private fun DrawScope.drawPartialConstructs(
    viewModel: EditClusterViewModel,
    visibleRect: Rect,
    handleRadius: Float,
    circleStroke: DrawStyle,
    strokeWidth: Float,
    creationPointRadius: Float = handleRadius * 3/4,
    creationPrototypeColor: Color = DodeclustersColors.green,
) {
    // generic display for selected tool args
    viewModel.partialArgList?.args?.let { args ->
        for (arg in args)
            when (arg) {
                is PartialArgList.Arg.CircleIndex ->
                    drawCircleOrLine(
                        viewModel.circles[arg.index],
                        visibleRect, creationPrototypeColor, style = circleStroke
                    )
                is PartialArgList.Arg.XYPoint ->
                    drawCircle(
                        color = creationPrototypeColor,
                        radius = creationPointRadius,
                        center = arg.toOffset()
                    )
                is PartialArgList.Arg.GeneralizedCircle ->
                    when (val gCircle = arg.gCircle) {
                        is CircleOrLine ->
                            drawCircleOrLine(
                                gCircle,
                                visibleRect, creationPrototypeColor, style = circleStroke
                            )
                        is Point ->
                            drawCircle(
                                color = creationPrototypeColor,
                                radius = creationPointRadius,
                                center = gCircle.toOffset()
                            )
                        else -> {}
                    }
                is PartialArgList.Arg.CircleAndPointIndices -> {
                    for (ix in arg.circleIndices)
                        drawCircleOrLine(
                            viewModel.circles[ix],
                            visibleRect, creationPrototypeColor, style = circleStroke
                        )
                    for (ix in arg.pointIndices)
                        drawCircle(
                            color = creationPrototypeColor,
                            radius = creationPointRadius,
                            center = viewModel.points[ix].toOffset()
                        )
                }
            }
    }
    // custom previews for some tools
    when (viewModel.mode) {
        ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> viewModel.partialArgList!!.args.let { args ->
            if (args.size == 2) {
                val center = (args[0] as PartialArgList.Arg.XYPoint).toOffset()
                val radiusPoint = (args[1] as PartialArgList.Arg.XYPoint).toOffset()
                drawCircle(
                    color = creationPrototypeColor,
                    style = circleStroke,
                    radius = (radiusPoint - center).getDistance(),
                    center = center
                )
            }
        }
        ToolMode.CIRCLE_BY_3_POINTS -> viewModel.partialArgList!!.args.let { args ->
            val gCircles = args.map { (it as PartialArgList.Arg.GeneralizedCircle).gCircle }
            if (args.size == 2) {
                val line = GeneralizedCircle.perp3(
                    GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY),
                    GeneralizedCircle.fromGCircle(gCircles[0]),
                    GeneralizedCircle.fromGCircle(gCircles[1]),
                )?.toGCircle() as? Line
                if (line != null)
                    drawCircleOrLine(line, visibleRect, creationPrototypeColor, style = circleStroke)
            } else if (args.size == 3) {
                val circle = GeneralizedCircle.perp3(
                    GeneralizedCircle.fromGCircle(gCircles[0]),
                    GeneralizedCircle.fromGCircle(gCircles[1]),
                    GeneralizedCircle.fromGCircle(gCircles[2]),
                )?.toGCircle() as? CircleOrLine
                if (circle != null)
                    drawCircleOrLine(circle, visibleRect, creationPrototypeColor, style = circleStroke)
            }
        }
        ToolMode.LINE_BY_2_POINTS -> viewModel.partialArgList!!.args.let { args ->
            if (args.size == 2) {
                val gCircles = args.map { (it as PartialArgList.Arg.GeneralizedCircle).gCircle }
                val line = GeneralizedCircle.perp3(
                    GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY),
                    GeneralizedCircle.fromGCircle(gCircles[0]),
                    GeneralizedCircle.fromGCircle(gCircles[1]),
                )?.toGCircle() as? Line
                if (line != null)
                    drawCircleOrLine(
                        line,
                        visibleRect, creationPrototypeColor, style = circleStroke
                    )
            }
        }
        ToolMode.ARC_PATH -> viewModel.arcPathUnderConstruction?.let { arcPath ->
            drawCircle(
                color = creationPrototypeColor,
                radius = creationPointRadius,
                center = arcPath.startPoint.toOffset()
            )
            val path = Path()
            path.moveTo(arcPath.startPoint.x.toFloat(), arcPath.startPoint.y.toFloat())
            for (i in arcPath.points.indices) {
                val point = arcPath.points[i].toOffset()
                when (val circle = arcPath.circles[i]) {
                    is Circle -> path.arcToRad(
                        Rect(circle.center, circle.radius.toFloat()),
                        arcPath.startAngles[i].toFloat(),
                        arcPath.sweepAngles[i].toFloat(),
                        forceMoveTo = true
                    )
                    null -> path.lineTo(point.x, point.y)
                }
                drawCircle(
                    color = creationPrototypeColor,
                    radius = creationPointRadius,
                    center = point
                )
                drawCircle(
                    color = creationPrototypeColor,
                    radius = creationPointRadius,
                    center = arcPath.midpoints[i].toOffset()
                )
            }
            drawPath(path, creationPrototypeColor, style = circleStroke)
        }
        else -> {}
    }
}

private fun DrawScope.drawHandles(
    viewModel: EditClusterViewModel,
    visibleRect: Rect,
    selectionMarkingsColor: Color, // for selection rect
    scaleIconColor: Color,
    scaleIndicatorColor: Color,
    rotateIconColor: Color,
    rotationIndicatorColor: Color,
    handleRadius: Float,
    iconDim: Float,
    scaleIcon: Painter,
    rotateIcon: Painter,
    dottedStroke: DrawStyle,
) {
    if (viewModel.showCircles) {
        val iconSize = Size(iconDim, iconDim)
        when (viewModel.handleConfig) {
            is HandleConfig.SingleCircle -> {
                val selectedCircle = viewModel.circles[viewModel.selection.single()]
                if (selectedCircle is Circle) {
                    val right = selectedCircle.center + Offset(selectedCircle.radius.toFloat(), 0f)
                    drawLine( // radius marker
                        color = scaleIndicatorColor,
                        start = selectedCircle.center,
                        end = right,
                    )
                    drawCircle( // radius handle
                        color = scaleIndicatorColor,
                        radius = handleRadius,
                        center = right
                    )
                }
            }
            is HandleConfig.SeveralCircles -> {
                viewModel.getSelectionRect()?.let { selectionRect ->
                    drawRect( // selection rect
                        color = selectionMarkingsColor,
                        topLeft = selectionRect.topLeft,
                        size = selectionRect.size,
                        style = dottedStroke,
                    )
                    // scale handle
                    drawCircle(
                        color = scaleIconColor,
                        radius = handleRadius/4f,
                        center = selectionRect.topRight,
                    )
                    translate(selectionRect.right - iconDim/2, selectionRect.top - iconDim/2) {
                        with (scaleIcon) {
                            draw(iconSize, colorFilter = ColorFilter.tint(scaleIconColor))
                        }
                    }
                    // rotate handle icon
                    drawCircle( // scale handle
                        color = rotateIconColor,
                        radius = handleRadius/4f,
                        center = selectionRect.bottomRight,
                    )
                    translate(selectionRect.right - iconDim/2, selectionRect.bottom - iconDim/2) {
                        with (rotateIcon) {
                            draw(iconSize, colorFilter = ColorFilter.tint(rotateIconColor))
                        }
                    }
                }
            }
        }
        (viewModel.submode as? SubMode.Rotate)?.let { (center, angle) ->
            val currentDirection = Offset(0f, -1f).rotateBy(angle.toFloat())
            val maxDim = size.maxDimension
            val sameDirectionFarAway =
                center + currentDirection * maxDim
            drawLine(
                color = rotationIndicatorColor,
                start = center,
                end = sameDirectionFarAway,
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun BoxScope.CircleSelectionContextActions(viewModel: EditClusterViewModel) {
    val (w, h) = viewModel.canvasSize
    val positions = SelectionControlsPositions(w, h)
    val halfSize = (48/2).dp
    // infinity button to the left-center
    // + a way to trigger a visual effect over it
//    SimpleButton(
//        painterResource(Res.drawable.infinity),
//        stringResource(Res.string.stub),
//        Modifier.align(Alignment.CenterStart)
//    ) {}
    with (LocalDensity.current) {
        // expand & shrink buttons
        SimpleButton(
            painterResource(EditClusterTool.Expand.icon),
            stringResource(EditClusterTool.Expand.name),
            Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = positions.right.toDp() - halfSize,
                    y = positions.top.toDp() - halfSize
                ),
            tint = MaterialTheme.colorScheme.secondary
        ) { viewModel.toolAction(EditClusterTool.Expand) }
        SimpleButton(
            painterResource(EditClusterTool.Shrink.icon),
            stringResource(EditClusterTool.Shrink.name),
            Modifier.offset(
                x = positions.right.toDp() - halfSize,
                y = positions.scaleSliderBottom.toDp() - halfSize
            ),
            tint = MaterialTheme.colorScheme.secondary
        ) { viewModel.toolAction(EditClusterTool.Shrink) }
        // BUG: in browser after using "<url>?sample=0" shrink & copy button icons randomly disappear
        // duplicate & delete buttons
        SimpleButton(
            painterResource(EditClusterTool.Duplicate.icon),
            stringResource(EditClusterTool.Duplicate.name),
            Modifier.offset(
                x = positions.right.toDp() - halfSize,
                y = positions.topUnderScaleSlider.toDp() - halfSize
            ),
            tint = DodeclustersColors.skyBlue.copy(alpha = 0.9f)
        ) { viewModel.toolAction(EditClusterTool.Duplicate) }
        SimpleButton(
            painterResource(EditClusterTool.Delete.icon),
            stringResource(EditClusterTool.Delete.name),
            Modifier.offset(
                x = positions.left.toDp() - halfSize,
                y = positions.bottom.toDp() - halfSize
            ),
            tint = DodeclustersColors.lightRed.copy(alpha = 0.9f)
        ) { viewModel.toolAction(EditClusterTool.Delete) }
    }
}

@Composable
fun BoxScope.PointSelectionContextActions(viewModel: EditClusterViewModel) {
    val (w, h) = viewModel.canvasSize
    val positions = SelectionControlsPositions(w, h)
    val halfSize = (48/2).dp
    with (LocalDensity.current) {
        // delete buttons
        SimpleButton(
            painterResource(EditClusterTool.Delete.icon),
            stringResource(EditClusterTool.Delete.name),
            // awkward position tbh
            Modifier.offset(
                x = positions.right.toDp() - halfSize,
                y = positions.bottom.toDp() - halfSize
            ),
            tint = DodeclustersColors.lightRed.copy(alpha = 0.9f)
        ) { viewModel.toolAction(EditClusterTool.Delete) }
    }
}

// TODO: move it somewhere else, this location is bad
@Composable
fun BoxScope.ArcPathContextActions(viewModel: EditClusterViewModel) {
    val (w, h) = viewModel.canvasSize
    val verticalMargin = with (LocalDensity.current) {
        (h*SelectionControlsPositions.RELATIVE_VERTICAL_MARGIN).toDp()
    }
    Button(
        onClick = { viewModel.toolAction(EditClusterTool.CompleteArcPath) },
        Modifier // NOTE: this position is not optimal, especially for desktop
            .align(Alignment.BottomEnd)
            .offset(y = -verticalMargin)
        ,
        colors = ButtonDefaults.buttonColors()
            .copy(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
    ) {
        Icon(
            painterResource(EditClusterTool.CompleteArcPath.icon),
            stringResource(EditClusterTool.CompleteArcPath.name),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(EditClusterTool.CompleteArcPath.description),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

fun DrawScope.drawSelectionControls(
    viewModel: EditClusterViewModel,
    sliderColor: Color,
    jCarcassColor: Color,
    rotateHandleColor: Color,
    handleRadius: Float,
    iconDim: Float,
    rotateIcon: Painter,
) {
    val carcassStyle = Stroke(0.7f * iconDim, cap = StrokeCap.Round)
    val buttonBackdropRadius = 0.8f * iconDim
    val iconSize = Size(iconDim, iconDim)
    val (w, h) = viewModel.canvasSize
    val positions = SelectionControlsPositions(w, h)
    val cornerRect = Rect(
        center = Offset(
            positions.right - positions.cornerRadius,
            positions.bottom - positions.cornerRadius
        ),
        radius = positions.cornerRadius
    )
    val sliderBGPath = Path().apply {
        moveTo(positions.right, positions.top)
        lineTo(positions.right, positions.scaleSliderBottom)
    }
    // J-shaped carcass (always active when selection isn't empty)
    val jPath = Path().apply {
        moveTo(positions.right, positions.topUnderScaleSlider)
        lineTo(positions.right, positions.bottom - positions.cornerRadius)
        arcTo(cornerRect, 0f, 90f, forceMoveTo = true)
        lineTo(positions.left, positions.bottom)
    }
    drawPath(
        sliderBGPath, jCarcassColor,
        style = carcassStyle
    )
    drawPath(
        jPath, jCarcassColor,
        style = carcassStyle
    )
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.top))
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.scaleSliderBottom))
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.topUnderScaleSlider))
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.left, positions.bottom))
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.bottom))
    drawLine(
        sliderColor,
        Offset(positions.right, positions.top + positions.sliderPadding),
        Offset(positions.right, positions.scaleSliderBottom - positions.sliderPadding)
    )
    val sliderPercentage = when (val submode = viewModel.submode) {
        is SubMode.ScaleViaSlider -> submode.sliderPercentage
        else -> 0.5f
    }
    drawCircle( // slider handle
        sliderColor,
        handleRadius,
        Offset(positions.right, positions.calculateSliderY(sliderPercentage))
    )
    translate(positions.right - iconDim/2f, positions.bottom - iconDim/2f) {
        with (rotateIcon) {
            draw(iconSize, colorFilter = ColorFilter.tint(rotateHandleColor))
        }
    }
    // MAYBE: when in rotation mode, draw rotation anchor/center
    //  potentially add custom fields to specify angle & scale manually
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
// TODO: remove/decrease bottom margin when in landscape
@Immutable
data class SelectionControlsPositions(
    val width: Int,
    val height: Int
) {
    constructor(intSize: IntSize) : this(intSize.width, intSize.height)

    val minDim = min(width, height)
    val cornerRadius = minDim * RELATIVE_CORNER_RADIUS

    val top = height * RELATIVE_VERTICAL_MARGIN
    val sliderPadding = height * RELATIVE_SCALE_SLIDER_PADDING
    val sliderFullHeight = height * RELATIVE_SCALE_SLIDER_HEIGHT
    val sliderHeight = sliderFullHeight - 2*sliderPadding
    val sliderMiddlePosition = top + sliderFullHeight / 2f
    val scaleSliderBottom = top + height * RELATIVE_SCALE_SLIDER_HEIGHT
    val topUnderScaleSlider = scaleSliderBottom + height * RELATIVE_SCALE_SLIDER_TO_ROTATE_ARC_INDENT
    val bottom = height * (1 - RELATIVE_VERTICAL_MARGIN)

    val right = width * (1 - RELATIVE_RIGHT_MARGIN)
    val left = right - (bottom - topUnderScaleSlider)

    val sliderMiddleOffset = Offset(right, sliderMiddlePosition)
    val rotationHandleOffset = Offset(right, bottom)

    @Stable
    fun calculateSliderY(percentage: Float = 0.5f): Float =
        top + sliderPadding + sliderHeight * (1 - percentage)

    @Stable
    fun addPanToPercentage(currentPercentage: Float, pan: Offset): Float {
        val p = -pan.y/sliderHeight
        return (currentPercentage + p).coerceIn(0f, 1f)
    }

    companion object {
        const val RELATIVE_RIGHT_MARGIN = 0.05f // = % of W
        const val RELATIVE_VERTICAL_MARGIN = 0.15f // = % of H
        const val RELATIVE_SCALE_SLIDER_HEIGHT = 0.40f // = % of H
        const val RELATIVE_SCALE_SLIDER_PADDING = 0.02f // = % of H
        const val RELATIVE_SCALE_SLIDER_TO_ROTATE_ARC_INDENT = 0.10f // = % of H
        const val RELATIVE_CORNER_RADIUS = 0.10f // % of minDim
    }
}