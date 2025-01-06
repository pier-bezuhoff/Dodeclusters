package ui.edit_cluster

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.ui.graphics.StampedPathEffectStyle
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GeneralizedCircle
import data.geometry.ImaginaryCircle
import data.geometry.Line
import data.geometry.Point
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.zoom_in
import domain.Arg
import domain.rotateBy
import getPlatform
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.SimpleToolButton
import ui.circle2path
import ui.part2path
import ui.reactiveCanvas
import ui.theme.DodeclustersColors
import ui.theme.extendedColorScheme
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
    val pointRadius = 2.5f * strokeWidth
    val scaleIcon = painterResource(Res.drawable.zoom_in)
    val scaleIconColor = MaterialTheme.colorScheme.secondary
    val scaleIndicatorColor = DodeclustersColors.skyBlue
    val iconDim = with (LocalDensity.current) { 24.dp.toPx() }
    val rotateIcon = painterResource(Res.drawable.rotate_counterclockwise)
    val rotateIconColor = MaterialTheme.colorScheme.secondary
    val rotationIndicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
    val sliderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
    val jCarcassColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    // MAYBE: black/dark grey for light scheme
    val circleColor = MaterialTheme.extendedColorScheme.accentColor.copy(alpha = 0.6f)
    val freeCircleColor = MaterialTheme.extendedColorScheme.highAccentColor
    val pointColor = MaterialTheme.extendedColorScheme.accentColor.copy(alpha = 0.7f)
    val freePointColor = freeCircleColor
    val selectedCircleColor =
//        MaterialTheme.colorScheme.primary
        DodeclustersColors.strongSalad
    val selectedPointColor = selectedCircleColor
    val clusterPathAlpha = 1f
    //0.7f
    val selectionMarkingsColor = DodeclustersColors.gray // center-radius line / bounding rect of selection
    val thiccSelectionCircleAlpha = 0.9f
    val animations: MutableMap<ColoredContourAnimation, Animatable<Float, AnimationVector1D>> =
        remember { mutableMapOf() }
    val coroutineScope = rememberCoroutineScope()
    coroutineScope.launch { // listen to circle animations
        viewModel.animations.collect { event ->
            when (event) {
                is ColoredContourAnimation -> launch { // parallel multiplexer structure
                    val animatable = Animatable(0f)
                    animations[event] = animatable
                    animatable.animateTo(event.maxAlpha, tween(event.alpha01Duration, easing = LinearEasing))
                    animatable.animateTo(
                        targetValue = 0f,
                        tween(event.alpha10Duration, easing = FastOutLinearInEasing),
//                animationSpec = tween(decayDuration, easing = CubicBezierEasing(0f, 0.7f, 0.75f, 0.55f)),
                    )
                    animations.remove(event) // idk, this might be bad
                }
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
//                renderEffect = BlurEffect(20f, 20f) // funi
            )
    ) {
        translate(viewModel.translation.x, viewModel.translation.y) {
            val visibleRect = size.toRect().translate(-viewModel.translation)
            drawAnimation(animations, visibleRect)
            // TODO: hoist VM here
            drawParts(viewModel, visibleRect, clusterPathAlpha, circleStroke)
            if (viewModel.showCircles)
                drawCircles(viewModel, visibleRect, circleColor, freeCircleColor, circleStroke, pointColor, freePointColor, pointRadius)
            drawSelectedCircles(viewModel, visibleRect, selectedCircleColor, thiccSelectionCircleAlpha, circleThiccStroke, selectedPointColor, pointRadius)
            drawPartialConstructs(viewModel, visibleRect, handleRadius, circleStroke, strokeWidth)
            drawHandles(viewModel, visibleRect, selectionMarkingsColor, scaleIconColor, scaleIndicatorColor, rotateIconColor, rotationIndicatorColor, handleRadius, iconDim, scaleIcon, rotateIcon, dottedStroke)
        }
        if (viewModel.circleSelectionIsActive && viewModel.showUI)
            drawSelectionControls(viewModel.canvasSize, viewModel.selectionIsLocked, viewModel.submode, sliderColor, jCarcassColor, rotateIconColor, handleRadius, iconDim, rotateIcon)
    }
    if (viewModel.showUI) {
        if (viewModel.circleSelectionIsActive) {
            if (viewModel.selectionIsLocked) {
                LockedCircleSelectionContextActions(viewModel.canvasSize, viewModel::toolAction, viewModel::getMostCommonCircleColorInSelection)
            } else {
                CircleSelectionContextActions(viewModel.canvasSize, viewModel.showDirectionArrows, viewModel::toolAction, viewModel::getMostCommonCircleColorInSelection)
            }
        } else if (viewModel.pointSelectionIsActive) {
            PointSelectionContextActions(viewModel.canvasSize, viewModel.selectionIsLocked, viewModel::toolAction)
        } else if (
            viewModel.mode == ToolMode.ARC_PATH &&
            viewModel.partialArcPath?.nArcs?.let { it >= 1 } == true
        ) {
            ArcPathContextActions(viewModel.canvasSize, viewModel::toolAction)
        }
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
                val circles = viewModel.selection
                    .mapNotNull { viewModel.objects[it] as? CircleOrLine }
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
        is Circle -> {
            val radius = circle.radius.toFloat()
            val maxRadius = getPlatform().maxCircleRadius
            if (radius <= maxRadius || style != Fill) {
                drawCircle(color, radius, circle.center, alpha, style, blendMode = blendMode)
            } else {
                val line = circle.approximateToLine(visibleRect.center)
                drawCircleOrLine(line, visibleRect, color, alpha, style, blendMode, drawHalfPlanesForLines)
            }
        }
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

private const val ARROW_HEIGHT = 20f
private const val ARROW_WIDTH = 20f
private val ARROW_SHAPE = Path().apply { // >
    lineTo(-ARROW_WIDTH/4, -ARROW_HEIGHT/2)
    lineTo(ARROW_WIDTH*3/4, 0f)
    lineTo(-ARROW_WIDTH/4, ARROW_HEIGHT/2)
    close()
}
private const val ARROW_SPACING = 200f
private val ARROWED_PATH_EFFECT =
    PathEffect.stampedPathEffect(
        shape = ARROW_SHAPE,
        advance = ARROW_SPACING,
        phase = ARROW_SPACING/2,
        style = StampedPathEffectStyle.Rotate
    )
private const val HAIR_LENGTH = 100f
private const val HAIR_WIDTH = 1f
private val HAIR_PATH = Path().apply {
    lineTo(0f, -HAIR_LENGTH)
    lineTo(HAIR_WIDTH, -HAIR_LENGTH)
    lineTo(HAIR_WIDTH, 0f)
    close()
}
private const val HAIR_SPACING = HAIR_LENGTH/4
private val HAIR_PATH_EFFECT =
    PathEffect.stampedPathEffect(
        shape = HAIR_PATH,
        advance = HAIR_SPACING,
        phase = 0f,
        style = StampedPathEffectStyle.Rotate
    )

// MAYBE: instead of path effects, draw arrows directly
private fun DrawScope.drawArrows(
    circle: CircleOrLine,
    visibleRect: Rect,
    color: Color,
) {
    when (circle) {
        is Circle -> {
            val path = Path()
            path.addOval(
                Rect(circle.center, circle.radius.toFloat()),
                if (circle.isCCW) Path.Direction.CounterClockwise
                else Path.Direction.Clockwise
            )
            drawPath(path, color, style = Stroke(pathEffect = HAIR_PATH_EFFECT))
            drawPath(path, color, style = Stroke(pathEffect = ARROWED_PATH_EFFECT))
        }
        is Line -> {
            val maxDim = visibleRect.maxDimension
            val pointClosestToScreenCenter = circle.project(visibleRect.center)
            val direction =  circle.directionVector
            val farBack = pointClosestToScreenCenter - direction * maxDim
            val farForward = pointClosestToScreenCenter + direction * maxDim
            val path = Path()
            path.moveTo(farBack.x, farBack.y)
            path.lineTo(farForward.x, farForward.y)
            drawPath(path, color, style = Stroke(pathEffect = HAIR_PATH_EFFECT))
            drawPath(path, color, style = Stroke(pathEffect = ARROWED_PATH_EFFECT))
        }
    }
}

// FUN FACT: stampedPathEffect on android is garbo
private fun DrawScope.drawArrowsPatchedForAndroid(
    circle: CircleOrLine,
    visibleRect: Rect,
    color: Color,
) {
    when (circle) {
        is Circle -> {
            val path = Path()
            path.addOval(
                Rect(circle.center, circle.radius.toFloat()),
                if (circle.isCCW) Path.Direction.CounterClockwise
                else Path.Direction.Clockwise
            )
            val hairPath = if (circle.isCCW) {
                path
            } else {
                // have to do it this way cuz Android randomly clips outward hair to the path rect
                Path().apply {
                    addOval(
                        Rect(circle.center, circle.radius.toFloat() + HAIR_LENGTH),
                        Path.Direction.CounterClockwise
                    )
                }
            }
            drawPath(hairPath, color, style = Stroke(pathEffect = HAIR_PATH_EFFECT))
            // MAYBE: shift arrows slightly & increase radius to increase bounding rect
            drawPath(path, color, style = Stroke(pathEffect = ARROWED_PATH_EFFECT))
        }
        is Line -> {
            val maxDim = visibleRect.maxDimension
            val pointClosestToScreenCenter = circle.project(visibleRect.center)
            val direction =  circle.directionVector
            val farBack = pointClosestToScreenCenter - direction * maxDim
            val farForward = pointClosestToScreenCenter + direction * maxDim
            // NOTE: Android  clips arrows & hair on near-horizontal and near-vertical lines
            //  do im trying to toe around it
            //  ┏━━━━┛
            val normalExtension = 3f * HAIR_LENGTH // why 3f and not 1f? don't ask me...
            val start = farBack - circle.normalVector * normalExtension
            val end = farForward + circle.normalVector * normalExtension
            val path = Path()
            path.moveTo(start.x, start.y)
            path.lineTo(farBack.x, farBack.y)
            path.lineTo(farForward.x, farForward.y)
            path.lineTo(end.x, end.y)
            drawPath(path, color, style = Stroke(pathEffect = HAIR_PATH_EFFECT))
            drawPath(path, color, style = Stroke(pathEffect = ARROWED_PATH_EFFECT))
        }
    }
}

private fun DrawScope.drawAnimation(
    animations: Map<ColoredContourAnimation, Animatable<Float, AnimationVector1D>>,
    visibleRect: Rect
) {
    val visibleScreenPath = Path().apply { addRect(visibleRect) }
    for ((animation, alpha) in animations) {
        for (circle in animation.objects) {
            val color = animation.color
            when (circle) {
                is Circle -> {
                    val path = circle2path(circle)
                    path.op(path, visibleScreenPath, PathOperation.Intersect)
                    val style =
                        if (animation.fillCircle) Fill
                        else Stroke(20f)
                    drawPath(path, color, style = style, alpha = alpha.value)
                }
                is Line -> {
                    val maxDim = visibleRect.maxDimension
                    val pointClosestToScreenCenter = circle.project(visibleRect.center)
                    val direction =  circle.directionVector
                    val farBack = pointClosestToScreenCenter - direction * maxDim
                    val farForward = pointClosestToScreenCenter + direction * maxDim
                    drawLine(color, farBack, farForward, strokeWidth = 20f, alpha = alpha.value)
//                    val path = visibleHalfPlanePath(circle, visibleRect)
//                    drawPath(path, color, alpha = decayAlpha.value)
                }
                is Point -> {
                    val pointRadius = 15f
                    drawCircle(color, pointRadius, circle.toOffset(), alpha = alpha.value)
                }
                is ImaginaryCircle -> {}
            }
        }
    }
}

private fun DrawScope.drawCircles(
    viewModel: EditClusterViewModel,
    visibleRect: Rect,
    circleColor: Color,
    freeCircleColor: Color,
    circleStroke: Stroke,
    pointColor: Color,
    freePointColor: Color,
    pointRadius: Float
) {
    if (viewModel.circleSelectionIsActive) {
        for ((ix, circle) in viewModel.objects.withIndex()) {
            if (circle is CircleOrLine && ix !in viewModel.selection) {
                val color =
                    viewModel.objectColors[ix] ?:
                    if (viewModel.isFree(ix)) freeCircleColor else circleColor
                drawCircleOrLine(circle, visibleRect, color, style = circleStroke)
            }
        }
    } else {
        for ((ix, circle) in viewModel.objects.withIndex()) {
            if (circle is CircleOrLine) {
                val color =
                    viewModel.objectColors[ix] ?:
                    if (viewModel.isFree(ix)) freeCircleColor else circleColor
                drawCircleOrLine(circle, visibleRect, color, style = circleStroke)
            }
        }
    }
    if (viewModel.pointSelectionIsActive) {
        for ((ix, point) in viewModel.objects.withIndex()) {
            if (point is Point && ix !in viewModel.selection) {
                val color = if (viewModel.isFree(ix)) freePointColor else pointColor
                drawCircle(color, pointRadius, point.toOffset())
            }
        }
    } else {
        for ((ix, point) in viewModel.objects.withIndex()) {
            if (point is Point) {
                val color = if (viewModel.isFree(ix)) freePointColor else pointColor
                drawCircle(color, pointRadius, point.toOffset())
            }
        }
    }
}

private val patchForAndroid = getPlatform().kind == PlatformKind.ANDROID
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
        for (ix in viewModel.selection) {
            val circle = viewModel.objects[ix]
            if (circle is CircleOrLine) {
                val color =
                    viewModel.objectColors[ix] ?:
                    selectedCircleColor
                drawCircleOrLine(
                    circle, visibleRect, color,
                    alpha = thiccSelectionCircleAlpha,
                    style = circleThiccStroke
                )
                if (viewModel.showDirectionArrows) {
                    if (patchForAndroid)
                        drawArrowsPatchedForAndroid(circle, visibleRect, color)
                    else
                        drawArrows(circle, visibleRect, color)
                }
            }
        }
    }
    if (viewModel.pointSelectionIsActive) {
        val points = viewModel.selection.mapNotNull { viewModel.objects[it] as? Point }
        for (point in points) {
            drawCircle(selectedPointColor, pointRadius, point.toOffset())
        }
    }
}

private fun DrawScope.drawParts(
    viewModel: EditClusterViewModel,
    visibleRect: Rect,
    clusterPathAlpha: Float,
    circleStroke: DrawStyle,
) {
    // NOTE: buggy on extreme zoom-in
    if (viewModel.displayChessboardPattern) {
        if (viewModel.showWireframes) {
            for (circle in viewModel.objects)
                if (circle is CircleOrLine)
                    drawCircleOrLine(circle, visibleRect, viewModel.regionColor, style = circleStroke)
        } else {
            if (viewModel.chessboardPatternStartsColored)
                drawRect(viewModel.regionColor, visibleRect.topLeft, visibleRect.size)
            // FIX: flickering, eg when altering spirals
            for (circle in viewModel.objects) { // it used to work poorly but is good now for some reason
                if (circle is CircleOrLine)
                    drawCircleOrLine(circle, visibleRect, viewModel.regionColor, blendMode = BlendMode.Xor, drawHalfPlanesForLines = true)
            }
        }
    } else {
        for (part in viewModel.regions) {
            val path = part2path(viewModel.objects.map { it as? CircleOrLine }, part, visibleRect)
            if (viewModel.showWireframes) {
                drawPath(
                    path,
                    color = part.fillColor,
                    alpha = clusterPathAlpha,
                    style = circleStroke
                )
            } else {
                drawPath( // drawing stroke+fill to prevent seams
                    path,
                    color = part.borderColor ?: part.fillColor,
                    alpha = clusterPathAlpha,
                    style = circleStroke
                )
                drawPath(
                    path,
                    color = part.fillColor,
                    alpha = clusterPathAlpha,
                    style = Fill,
                )
            }
        }
    }
}

// draw stuff for tool modes
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
                is Arg.CircleIndex -> {
                    val circle = viewModel.objects[arg.index]
                    if (circle is CircleOrLine)
                        drawCircleOrLine(
                            circle,
                            visibleRect, creationPrototypeColor, style = circleStroke
                        )
                }
                is Arg.Point.Index -> {
                    val center = viewModel.objects[arg.index]
                    if (center is Point)
                        drawCircle(
                            color = creationPrototypeColor,
                            radius = creationPointRadius,
                            center = center.toOffset()
                        )
                }
                is Arg.Point.XY ->
                    drawCircle(
                        color = creationPrototypeColor,
                        radius = creationPointRadius,
                        center = arg.toOffset()
                    )
                is Arg.CircleOrPoint.Point.Index -> {
                    val center = viewModel.objects[arg.index]
                    if (center is Point)
                        drawCircle(
                            color = creationPrototypeColor,
                            radius = creationPointRadius,
                            center = center.toOffset()
                        )
                }
                is Arg.CircleOrPoint.Point.XY ->
                    drawCircle(
                        color = creationPrototypeColor,
                        radius = creationPointRadius,
                        center = arg.toOffset()
                    )
                is Arg.CircleOrPoint.CircleIndex -> {
                    val circle = viewModel.objects[arg.index]
                    if (circle is CircleOrLine)
                        drawCircleOrLine(
                            circle,
                            visibleRect, creationPrototypeColor, style = circleStroke
                        )
                }
                is Arg.CircleAndPointIndices -> {
                    for (ix in arg.circleIndices) {
                        val circle = viewModel.objects[ix]
                        if (circle is CircleOrLine)
                            drawCircleOrLine(circle, visibleRect, creationPrototypeColor, style = circleStroke)
                    }
                    for (ix in arg.pointIndices) {
                        val point = viewModel.objects[ix]
                        if (point is Point)
                            drawCircle(
                                color = creationPrototypeColor,
                                radius = creationPointRadius,
                                center = point.toOffset()
                            )
                    }
                }
            }
    }
    // custom previews for some tools
    when (viewModel.mode) {
        ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> viewModel.partialArgList!!.args.let { args ->
            if (args.size == 2) {
                val (center, radiusPoint) = args.map {
                    viewModel.getArg(it as Arg.Point)!!
                }
                val radius = center.distanceFrom(radiusPoint)
                drawCircle(
                    color = creationPrototypeColor,
                    style = circleStroke,
                    radius = radius.toFloat(),
                    center = center.toOffset()
                )
            }
        }
        ToolMode.CIRCLE_BY_3_POINTS -> viewModel.partialArgList!!.args.let { args ->
            val gCircles = args.map { viewModel.getArg(it as Arg.CircleOrPoint)!! }
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
        ToolMode.CIRCLE_BY_PENCIL_AND_POINT -> viewModel.partialArgList!!.args.let { args ->
            val gCircles = args.map { viewModel.getArg(it as Arg.CircleOrPoint)!! }
            if (args.size == 2) {
                val line = GeneralizedCircle.parallel2perp1(
                    GeneralizedCircle.fromGCircle(gCircles[0]),
                    GeneralizedCircle.fromGCircle(gCircles[1]),
                    GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY),
                )?.toGCircle() as? Line
                if (line != null)
                    drawCircleOrLine(line, visibleRect, creationPrototypeColor, style = circleStroke)
            } else if (args.size == 3) {
                val circle = GeneralizedCircle.parallel2perp1(
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
                val gCircles = args.map { viewModel.getArg(it as Arg.CircleOrPoint)!! }
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
        ToolMode.ARC_PATH -> viewModel.partialArcPath?.let { arcPath ->
            drawCircle(
                color = creationPrototypeColor,
                radius = creationPointRadius,
                center = arcPath.startVertex.point.toOffset()
            )
            val path = Path()
            path.moveTo(arcPath.startVertex.point.x.toFloat(), arcPath.startVertex.point.y.toFloat())
            for (i in arcPath.vertices.indices) {
                val point = arcPath.vertices[i].point.toOffset()
                when (val circle = arcPath.circles[i].circle) {
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
                val selectedCircle = viewModel.objects[viewModel.selection.single()]
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

            null -> {}
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
fun BoxScope.LockedCircleSelectionContextActions(
    canvasSize: IntSize,
    toolAction: (EditClusterTool) -> Unit,
    getMostCommonCircleColorInSelection: () -> Color?
) {
    with (ConcreteSelectionControlsPositions(canvasSize, LocalDensity.current)) {
        // duplicate & delete buttons
        SimpleToolButton(
            EditClusterTool.Duplicate,
            topRightUnderScaleModifier,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.PickCircleColor,
            halfBottomRightModifier,
            tint = getMostCommonCircleColorInSelection() ?: MaterialTheme.extendedColorScheme.highAccentColor,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.Delete,
            bottomRightModifier,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.Detach,
            bottomLeftModifier,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = toolAction
        )
    }
}

@Composable
fun BoxScope.CircleSelectionContextActions(
    canvasSize: IntSize,
    showDirectionArrows: Boolean,
    toolAction: (EditClusterTool) -> Unit,
    getMostCommonCircleColorInSelection: () -> Color?,
) {
    // infinity button to the left-center
    // + a way to trigger a visual effect over it
//    SimpleButton(
//        painterResource(Res.drawable.infinity),
//        stringResource(Res.string.stub),
//        Modifier.align(Alignment.CenterStart)
//    ) {}
    with (ConcreteSelectionControlsPositions(canvasSize, LocalDensity.current)) {
        // expand & shrink buttons
        SimpleToolButton(
            EditClusterTool.Expand,
            Modifier
                .align(Alignment.TopStart)
                .then(topRightModifier)
            ,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.Shrink,
            scaleBottomRightModifier,
            tint = MaterialTheme.colorScheme.secondary,
            onClick = toolAction
        )
        // duplicate & delete buttons
        SimpleToolButton(
            EditClusterTool.Duplicate,
            topRightUnderScaleModifier,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.PickCircleColor,
            halfBottomRightModifier,
            tint = getMostCommonCircleColorInSelection() ?: MaterialTheme.extendedColorScheme.highAccentColor,
            onClick = toolAction
        )
        SimpleToolButton(
            EditClusterTool.Delete,
            bottomRightModifier,
            onClick = toolAction
        )
        if (showDirectionArrows) {
            SimpleToolButton(
                EditClusterTool.SwapDirection,
                bottomMidModifier,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = toolAction
            )
        }
    }
}

@Composable
fun PointSelectionContextActions(
    canvasSize: IntSize,
    selectionIsLocked: Boolean,
    toolAction: (EditClusterTool) -> Unit,
) {
    with (ConcreteSelectionControlsPositions(canvasSize, LocalDensity.current)) {
        SimpleToolButton(
            EditClusterTool.Delete,
            // awkward position tbh
            bottomRightModifier,
            onClick = toolAction
        )
        if (selectionIsLocked) {
            SimpleToolButton(
                EditClusterTool.Detach,
                halfBottomRightModifier,
                tint = MaterialTheme.colorScheme.secondary,
                onClick = toolAction
            )
        }
    }
}

// TODO: move it somewhere else, this location is bad
@Composable
fun BoxScope.ArcPathContextActions(
    canvasSize: IntSize,
    toolAction: (EditClusterTool) -> Unit,
) {
    val (w, h) = canvasSize
    val verticalMargin = with (LocalDensity.current) {
        (h*SelectionControlsPositions.RELATIVE_VERTICAL_MARGIN).toDp()
    }
    Button(
        onClick = { toolAction(EditClusterTool.CompleteArcPath) },
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
    canvasSize: IntSize,
    selectionIsLocked: Boolean,
    subMode: SubMode,
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
    val (w, h) = canvasSize
    val positions = SelectionControlsPositions(w, h)
    val cornerRect = Rect(
        center = Offset(
            positions.right - positions.cornerRadius,
            positions.bottom - positions.cornerRadius
        ),
        radius = positions.cornerRadius
    )
    if (!selectionIsLocked) {
        val sliderBGPath = Path().apply {
            moveTo(positions.right, positions.top)
            lineTo(positions.right, positions.scaleSliderBottom)
        }
        drawPath(
            sliderBGPath, jCarcassColor,
            style = carcassStyle
        )
    }
    // J-shaped carcass (always active when selection isn't empty)
    val jPath = Path().apply {
        moveTo(positions.right, positions.topUnderScaleSlider)
        lineTo(positions.right, positions.bottom - positions.cornerRadius)
        arcTo(cornerRect, 0f, 90f, forceMoveTo = true)
        lineTo(positions.left, positions.bottom)
    }
    drawPath(
        jPath, jCarcassColor,
        style = carcassStyle
    )
    if (!selectionIsLocked) {
        drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.top))
        drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.scaleSliderBottom))
    }
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.topUnderScaleSlider))
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.halfHigherThanBottom))
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.left, positions.bottom))
    drawCircle(jCarcassColor, radius = buttonBackdropRadius, center = Offset(positions.right, positions.bottom))
    if (!selectionIsLocked) {
        drawLine(
            sliderColor,
            Offset(positions.right, positions.top + positions.sliderPadding),
            Offset(positions.right, positions.scaleSliderBottom - positions.sliderPadding)
        )
        val sliderPercentage = when (subMode) {
            is SubMode.ScaleViaSlider -> subMode.sliderPercentage
            else -> 0.5f
        }
        drawCircle( // slider handle
            sliderColor,
            handleRadius,
            Offset(positions.right, positions.calculateSliderY(sliderPercentage))
        )
        translate(
            positions.rotationHandleOffset.x - iconDim / 2f,
            positions.rotationHandleOffset.y - iconDim / 2f
        ) {
            with (rotateIcon) {
                draw(iconSize, colorFilter = ColorFilter.tint(rotateHandleColor))
            }
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
//      [R]   . x  [delete]
//       ^    .
//       |
//    rotate handle
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
    val halfHigherThanBottom = (topUnderScaleSlider + bottom)/2f

    val right = width * (1 - RELATIVE_RIGHT_MARGIN)
    val left = right - (bottom - topUnderScaleSlider)
    val mid = (right + left)/2

    val sliderMiddleOffset = Offset(right, sliderMiddlePosition)
    val rotationHandleOffset = Offset(left, bottom)

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

@Immutable
data class ConcreteSelectionControlsPositions(
    val size: IntSize,
    val density: Density,
    val halfSize: Dp = (48/2).dp,
) {
    val positions: SelectionControlsPositions = SelectionControlsPositions(size)

    @Stable
    fun offsetModifier(x: Float, y: Float): Modifier =
        with (density) {
            Modifier.offset(
                x = x.toDp() - halfSize,
                y = y.toDp() - halfSize,
            )
        }

    val topRightModifier = offsetModifier(positions.right, positions.top)
    val scaleBottomRightModifier = offsetModifier(positions.right, positions.scaleSliderBottom)
    val topRightUnderScaleModifier = offsetModifier(positions.right, positions.topUnderScaleSlider)
    val halfBottomRightModifier = offsetModifier(positions.right, positions.halfHigherThanBottom)
    val bottomRightModifier = offsetModifier(positions.right, positions.bottom)
    val bottomLeftModifier = offsetModifier(positions.left, positions.bottom)
    val bottomMidModifier = offsetModifier(positions.mid, positions.bottom)
}