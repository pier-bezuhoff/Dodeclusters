package ui.edit_cluster

import PlatformKind
import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StampedPathEffectStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.GeneralizedCircle
import data.geometry.ImaginaryCircle
import data.geometry.Line
import data.geometry.PartialArcPath
import data.geometry.Point
import data.geometry.fromCorners
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.rotate_counterclockwise
import dodeclusters.composeapp.generated.resources.zoom_in
import domain.Arg
import domain.ChessboardPattern
import domain.Ix
import domain.PartialArgList
import domain.angleDeg
import domain.cluster.LogicalRegion
import domain.expressions.BiInversionParameters
import domain.expressions.InterpolationParameters
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.RotationParameters
import domain.hug
import domain.measureAndPrintPerformance
import domain.rotateBy
import domain.rotateByAround
import getPlatform
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import ui.circle2path
import ui.reactiveCanvas
import ui.region2path
import ui.theme.DodeclustersColors
import ui.theme.extendedColorScheme
import ui.visibleHalfPlanePath
import kotlin.math.max
import kotlin.math.min

private val defaultMaxCircleRadius: Float = getPlatform().maxCircleRadius
private val dottedPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

// TODO: make colors dependent on light/dark scheme
// NOTE: changes to this canvas should be reflected on ScreenshotableCanvas for proper screenshots
@Composable
fun BoxScope.EditClusterCanvas(
    viewModel: EditClusterViewModel,
    modifier: Modifier = Modifier
) {
    // MAYBE: im supposed to remember { } all of these things (thatd be insane)
    val strokeWidth = with (LocalDensity.current) { 2.dp.toPx() }
    val circleStroke = Stroke(
        width = strokeWidth,
    )
    val circleThiccStroke = Stroke(
        width = 2 * strokeWidth,
    )
    val dottedStroke = remember { Stroke(
        width = strokeWidth,
        pathEffect = dottedPathEffect,
    ) }
    val thiccDottedStroke = remember { Stroke(
        width = 2 * strokeWidth,
        pathEffect = dottedPathEffect,
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
    val rotationHandleBackgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    val rotationHandleColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
    // MAYBE: black/dark grey for light scheme
    val circleColor = MaterialTheme.extendedColorScheme.accentColor.copy(alpha = 0.6f)
    val freeCircleColor = MaterialTheme.extendedColorScheme.highAccentColor
    val pointColor = MaterialTheme.extendedColorScheme.accentColor.copy(alpha = 0.7f)
    val freePointColor = freeCircleColor
    val selectedCircleColor = DodeclustersColors.strongSalad
    val selectedPointColor = selectedCircleColor
    val imaginaryCircleColor = DodeclustersColors.fadedRed
    val selectionMarkingsColor = DodeclustersColors.gray // center-radius line / bounding rect of selection
    val stereographicGridColor = MaterialTheme.colorScheme.secondary
    val thiccSelectionCircleAlpha = 0.9f
    val concretePositions = ConcreteOnScreenPositions(viewModel.canvasSize.toSize(), LocalDensity.current)
    val animations: MutableMap<ColoredContourAnimation, Animatable<Float, AnimationVector1D>> =
        remember { mutableStateMapOf() }
    val coroutineScope = rememberCoroutineScope()
    coroutineScope.launch { // listen to circle animations
        viewModel.animations.collect { event ->
            when (event) {
                is ColoredContourAnimation -> launch { // parallel multiplexer structure
                    animations[event]?.stop()
                    val animatable = Animatable(0f)
                    animations[event] = animatable
                    animatable.animateTo(event.maxAlpha, tween(event.alpha01Duration, easing = LinearEasing))
                    animatable.animateTo(
                        targetValue = 0f,
                        tween(event.alpha10Duration, easing = FastOutLinearInEasing),
            //                animationSpec = tween(decayDuration, easing = CubicBezierEasing(0f, 0.7f, 0.75f, 0.55f)),
                    )
                    animations.remove(event)
                }
            }
        }
    }
    if (viewModel.backgroundColor == null)
        viewModel.backgroundColor = MaterialTheme.colorScheme.surface
//    SelectionsCanvas(modifier, viewModel, selectionLinesColor, backgroundColor, selectedCircleColor, circleThiccStroke, thiccSelectionCircleAlpha = thiccSelectionCircleAlpha)
    // key(viewModel.objectAlterationTrigger) { Canvas(...) }
    Canvas(
        modifier
            .reactiveCanvas(
                onTap = viewModel::onTap,
                onUp = viewModel::onUp,
                onDown = viewModel::onDown,
                onPanZoomRotate = viewModel::onPanZoomRotate,
                onVerticalScroll = viewModel::onVerticalScroll,
//                onLongPress = viewModel::onLongPress,
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
//        measureAndPrintPerformance("draw") {
        hug(viewModel.objectModel.invalidations)
        translate(viewModel.translation.x, viewModel.translation.y) {
            val visibleRect = size.toRect().translate(-viewModel.translation)
            val hiddenObjectIndices = if (viewModel.showPhantomObjects) emptySet() else viewModel.phantoms
            drawRegions(objects = viewModel.objects, regions = viewModel.regions, hiddenObjectIndices = hiddenObjectIndices, chessboardPattern = viewModel.chessboardPattern, chessboardColor = viewModel.chessboardColor, showWireframes = viewModel.showWireframes, visibleRect = visibleRect, regionsOpacity = viewModel.regionsOpacity, regionsBlendMode = viewModel.regionsBlendModeType.blendMode, circleStroke = circleStroke)
            drawAnimation(animations = animations, visibleRect = visibleRect, strokeWidth = strokeWidth)
            if (viewModel.showCircles) {
                val selectionIsActive = viewModel.showCircles && viewModel.mode.isSelectingCircles() && viewModel.selection.isNotEmpty()
                drawObjects(objects = viewModel.objects, hiddenObjectIndices = hiddenObjectIndices, objectColors = viewModel.objectModel.objectColors, selection = viewModel.selection, selectionIsActive = selectionIsActive, isObjectFree = { viewModel.isFree(it) }, visibleRect = visibleRect, circleColor = circleColor, freeCircleColor = freeCircleColor, circleStroke = circleStroke, pointColor = pointColor, freePointColor = freePointColor, pointRadius = pointRadius, imaginaryCircleColor = imaginaryCircleColor, imaginaryCircleStroke = dottedStroke)
                drawSelectedCircles(objects = viewModel.objects, objectColors = viewModel.objectModel.objectColors, selection = viewModel.selection, mode = viewModel.mode, selectionIsActive = selectionIsActive, restrictRegionsToSelection = viewModel.restrictRegionsToSelection, showDirectionArrows = viewModel.showDirectionArrows, visibleRect = visibleRect, selectedCircleColor = selectedCircleColor, thiccSelectionCircleAlpha = thiccSelectionCircleAlpha, circleThiccStroke = circleThiccStroke, selectedPointColor = selectedPointColor, pointRadius = pointRadius, imaginaryCircleColor = imaginaryCircleColor, imaginaryCircleThiccStroke = thiccDottedStroke)
            }
            drawPartialConstructs(objects = viewModel.objects, mode = viewModel.mode, partialArgList = viewModel.partialArgList, partialArcPath = viewModel.partialArcPath, getArg = { viewModel.getArg(it) }, visibleRect = visibleRect, handleRadius = handleRadius, circleStroke = circleStroke, imaginaryCircleStroke = dottedStroke)
            drawHandles(objects = viewModel.objects, selection = viewModel.selection, submode = viewModel.submode, handleConfig = viewModel.handleConfig, getSelectionRect = { viewModel.getSelectionRect() }, showCircles = viewModel.showCircles, selectionMarkingsColor = selectionMarkingsColor, scaleIconColor = scaleIconColor, scaleIndicatorColor = scaleIndicatorColor, rotateIconColor = rotateIconColor, rotationIndicatorColor = rotationIndicatorColor, handleRadius = handleRadius, iconDim = iconDim, scaleIcon = scaleIcon, rotateIcon = rotateIcon, dottedStroke = dottedStroke)
            drawGrids(visibleRect = visibleRect, submode = viewModel.submode, stereographicGridColor = stereographicGridColor, stereographicGridStroke = circleStroke, southPointRadius = handleRadius)
//                for (o in viewModel._debugObjects)
//                    when (o) {
//                        is CircleOrLine -> drawCircleOrLine(o, visibleRect, rotateIconColor, style = circleStroke)
//                        is ImaginaryCircle -> drawCircleOrLine(o.toRealCircle(), visibleRect, rotateIconColor, style = circleStroke)
//                        is Point -> drawCircle(rotateIconColor, pointRadius, o.toOffset())
//                    }
        }
        if (viewModel.circleSelectionIsActive && viewModel.showUI) {
            drawRotationHandle(concretePositions.positions, viewModel.rotationHandleAngle, rotationHandleColor, rotationHandleBackgroundColor)
        }
//        } // not that long
    }
    if (viewModel.showUI) { // HUD
        if (viewModel.circleSelectionIsActive) {
            SelectionContextActions(
                concretePositions = concretePositions,
                scaleSliderPercentage = viewModel.scaleSliderPercentage,
                rotationHandleAngle = viewModel.rotationHandleAngle,
                objectColor =
                    viewModel.getMostCommonCircleColorInSelection()
                        ?: if (viewModel.selection.all { viewModel.objects[it] is ImaginaryCircle })
                            imaginaryCircleColor
                        else
                            freeCircleColor
                ,
                showAdjustExprButton = viewModel.showAdjustExprButton,
                showOrientationToggle = viewModel.showDirectionArrows,
                isLocked = viewModel.selectionIsLocked,
                toolAction = viewModel::toolAction,
                toolPredicate = viewModel::toolPredicate,
                onScale = viewModel::scaleViaSlider,
                onScaleFinished = viewModel::concludeSubmode,
                onRotate = viewModel::rotateViaHandle,
                onRotateStarted = { viewModel.startHandleRotation() },
                onRotateFinished = viewModel::concludeSubmode
            )
        } else if (viewModel.pointSelectionIsActive) {
            PointContextActions( // only points are selected
                showAdjustExprButton = viewModel.showAdjustExprButton,
                isLocked = viewModel.selectionIsLocked,
                toolAction = viewModel::toolAction,
                toolPredicate = viewModel::toolPredicate,
            )
        } else if (
            viewModel.mode == ToolMode.ARC_PATH &&
            viewModel.partialArcPath?.nArcs?.let { it >= 1 } == true
        ) {
            ArcPathContextActions(viewModel.canvasSize, viewModel::toolAction)
        } else {
            when (val sm = viewModel.submode) {
                is SubMode.ExprAdjustment -> when (sm.parameters) {
                    is InterpolationParameters ->
                        InterpolationInterface(
                            concretePositions = concretePositions,
                            interpolateCircles = viewModel.interpolateCircles,
                            circlesAreCoDirected = viewModel.circlesAreCoDirected,
                            defaults = viewModel.defaultInterpolationParameters,
                            updateParameters = viewModel::updateParameters,
                            openDetailsDialog = viewModel::openDetailsDialog,
                            confirmParameters = viewModel::confirmAdjustedParameters,
                        )
                    is RotationParameters ->
                        RotationInterface(
                            concretePositions = concretePositions,
                            defaults = viewModel.defaultRotationParameters,
                            updateParameters = viewModel::updateParameters,
                            openDetailsDialog = viewModel::openDetailsDialog,
                            confirmParameters = viewModel::confirmAdjustedParameters,
                        )
                    is BiInversionParameters ->
                        BiInversionInterface(
                            concretePositions = concretePositions,
                            defaults = viewModel.defaultBiInversionParameters,
                            updateParameters = viewModel::updateParameters,
                            openDetailsDialog = viewModel::openDetailsDialog,
                            confirmParameters = viewModel::confirmAdjustedParameters,
                        )
                    is LoxodromicMotionParameters ->
                        LoxodromicMotionInterface(
                            concretePositions = concretePositions,
                            defaults = viewModel.defaultLoxodromicMotionParameters,
                            updateParameters = viewModel::updateParameters,
                            updateBidirectionality = viewModel::updateLoxodromicBidirectionality,
                            openDetailsDialog = viewModel::openDetailsDialog,
                            confirmParameters = viewModel::confirmAdjustedParameters,
                        )
                    else -> {}
                }
                else -> {}
            }
        }
        if (viewModel.mode == SelectionMode.Region && viewModel.showCircles) {
            RegionManipulationStrategySelector(
                currentStrategy = viewModel.regionManipulationStrategy,
                setStrategy = viewModel::setRegionsManipulationStrategy
            )
        }
    }
}

/**
 * Used to make a screenshot of the current state of [viewModel].
 * Recreates the content of [EditClusterCanvas] on screen, saving operations into a
 * graphics layer, and then queues async graphics-layer-to-bitmap conversion with
 * result to be emitted into [bitmapFlow].
 *
 * NOTE: It is blocking and rather slow. Also idk why but the async _always_ happens on
 *  the main thread...
 */
@Composable
fun ScreenshotableCanvas(
    viewModel: EditClusterViewModel,
    bitmapFlow: MutableSharedFlow<ImageBitmap>,
    modifier: Modifier = Modifier
) {
    val strokeWidth = with (LocalDensity.current) { 2.dp.toPx() }
    val circleStroke = Stroke(width = strokeWidth)
    val circleThiccStroke = Stroke(width = 2 * strokeWidth)
    val dottedStroke = remember { Stroke(
        width = strokeWidth,
        pathEffect = dottedPathEffect,
    ) }
    val thiccDottedStroke = remember { Stroke(
        width = 2 * strokeWidth,
        pathEffect = dottedPathEffect,
    ) }
    val pointRadius = 2.5f * strokeWidth
    val circleColor = MaterialTheme.extendedColorScheme.accentColor.copy(alpha = 0.6f)
    val freeCircleColor = MaterialTheme.extendedColorScheme.highAccentColor
    val pointColor = MaterialTheme.extendedColorScheme.accentColor.copy(alpha = 0.7f)
    val freePointColor = freeCircleColor
    val selectedCircleColor = DodeclustersColors.strongSalad
//        MaterialTheme.colorScheme.primary
    val selectedPointColor = selectedCircleColor
    val imaginaryCircleColor = DodeclustersColors.fadedRed
    val thiccSelectionCircleAlpha = 0.9f
    val graphicsLayer = rememberGraphicsLayer()
    Box(modifier
        .fillMaxSize()
        .drawWithCache {
            onDrawWithContent {
                graphicsLayer.record {
                    this@onDrawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            }
        }
    ) {
        Box(Modifier
            .fillMaxSize()
            .drawBehind {
                // have to jump thru 2 ~~hoops~~ boxes for proper bg render
                viewModel.backgroundColor?.let { backgroundColor ->
                    drawRect(backgroundColor, size = size)
                }
            }
        ) {
            Canvas(
                Modifier.fillMaxSize()
                    .graphicsLayer(
                        compositingStrategy = CompositingStrategy.Offscreen, // crucial for proper alpha blending
                    )
            ) {
                translate(viewModel.translation.x, viewModel.translation.y) {
                    val visibleRect = size.toRect().translate(-viewModel.translation)
                    val hiddenObjectIndices = if (viewModel.showPhantomObjects) emptySet() else viewModel.phantoms
                    drawRegions(objects = viewModel.objects, regions = viewModel.regions, hiddenObjectIndices = hiddenObjectIndices, chessboardPattern = viewModel.chessboardPattern, chessboardColor = viewModel.chessboardColor, showWireframes = viewModel.showWireframes, visibleRect = visibleRect, regionsOpacity = viewModel.regionsOpacity, regionsBlendMode = viewModel.regionsBlendModeType.blendMode, circleStroke = circleStroke)
                    if (viewModel.showCircles) {
                        val selectionIsActive = viewModel.showCircles && viewModel.mode.isSelectingCircles() && viewModel.selection.isNotEmpty()
                        drawObjects(objects = viewModel.objects, hiddenObjectIndices = hiddenObjectIndices, objectColors = viewModel.objectModel.objectColors, selection = viewModel.selection, selectionIsActive = selectionIsActive, isObjectFree = { viewModel.isFree(it) }, visibleRect = visibleRect, circleColor = circleColor, freeCircleColor = freeCircleColor, circleStroke = circleStroke, pointColor = pointColor, freePointColor = freePointColor, pointRadius = pointRadius, imaginaryCircleColor = imaginaryCircleColor, imaginaryCircleStroke = dottedStroke)
                        drawSelectedCircles(objects = viewModel.objects, objectColors = viewModel.objectModel.objectColors, selection = viewModel.selection, mode = viewModel.mode, selectionIsActive = selectionIsActive, restrictRegionsToSelection = viewModel.restrictRegionsToSelection, showDirectionArrows = viewModel.showDirectionArrows, visibleRect = visibleRect, selectedCircleColor = selectedCircleColor, thiccSelectionCircleAlpha = thiccSelectionCircleAlpha, circleThiccStroke = circleThiccStroke, selectedPointColor = selectedPointColor, pointRadius = pointRadius, imaginaryCircleColor = imaginaryCircleColor, imaginaryCircleThiccStroke = thiccDottedStroke)
                    }
                }
            }
        }
        LaunchedEffect(viewModel, bitmapFlow) {
            val bitmap = graphicsLayer.toImageBitmap()
            bitmapFlow.emit(bitmap)
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
                // val nLines = 2*halfNLines
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
                for (circle in circles) { // MAYBE: draw circle outlines OVER regions
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
    drawHalfPlanesForLines: Boolean = false,
) {
    when (circle) {
        is Circle -> {
            val radius = circle.radius.toFloat()
            if (radius <= defaultMaxCircleRadius) {
                // NOTE: the condition used to be `r < maxR || style != Fill`
                //  but giant circles taint screenshots & it was unnatural
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
    visibleRect: Rect,
    strokeWidth: Float,
) {
    val borderStrokeWidth = 10*strokeWidth
    val pointRadius = 6*strokeWidth // unused
    val visibleScreenPath = Path().apply {
        addRect(visibleRect.inflate(10*strokeWidth))
    }
    for ((animation, alpha) in animations) {
        for (circle in animation.objects) {
            val color = animation.color
            when (circle) {
                is Circle -> {
                    val path = circle2path(circle)
                    path.op(path, visibleScreenPath, PathOperation.Intersect)
                    val style =
                        if (animation.fillCircle) Fill
                        else Stroke(borderStrokeWidth)
                    drawPath(path, color, style = style, alpha = alpha.value)
                }
                is Line -> {
                    val maxDim = visibleRect.maxDimension
                    val pointClosestToScreenCenter = circle.project(visibleRect.center)
                    val direction =  circle.directionVector
                    val farBack = pointClosestToScreenCenter - direction * maxDim
                    val farForward = pointClosestToScreenCenter + direction * maxDim
                    drawLine(color, farBack, farForward, strokeWidth = borderStrokeWidth, alpha = alpha.value)
//                    val path = visibleHalfPlanePath(circle, visibleRect)
//                    drawPath(path, color, alpha = decayAlpha.value)
                }
                is Point -> {
                    drawCircle(color, pointRadius, circle.toOffset(), alpha = alpha.value)
                }
                is ImaginaryCircle -> {}
            }
        }
    }
}

private inline fun DrawScope.drawObjects(
    objects: List<GCircle?>,
    hiddenObjectIndices: Set<Ix>,
    objectColors: Map<Ix, Color>,
    selection: List<Ix>,
    selectionIsActive: Boolean,
    crossinline isObjectFree: (Ix) -> Boolean,
    visibleRect: Rect,
    circleColor: Color,
    freeCircleColor: Color,
    circleStroke: Stroke,
    pointColor: Color,
    freePointColor: Color,
    pointRadius: Float,
    imaginaryCircleColor: Color,
    imaginaryCircleStroke: Stroke,
) {
    val showImaginaryCircles = EditClusterViewModel.SHOW_IMAGINARY_CIRCLES
    for (ix in objects.indices) {
        if (ix !in hiddenObjectIndices && (!selectionIsActive || ix !in selection)) {
            val objectColor = objectColors[ix]
            when (val o = objects[ix]) {
                is CircleOrLine -> {
                    val color =
                        objectColor ?: if (isObjectFree(ix)) freeCircleColor else circleColor
                    drawCircleOrLine(o, visibleRect, color,
                        style = circleStroke,
                    )
                }
                is Point -> {
                    val color =
                        objectColor ?: if (isObjectFree(ix)) freePointColor else pointColor
                    drawCircle(color, pointRadius, o.toOffset())
                }
                is ImaginaryCircle -> {
                    if (showImaginaryCircles) {
                        drawCircleOrLine(
                            Circle(o.x, o.y, o.radius), visibleRect,
                            color = objectColor ?: imaginaryCircleColor,
                            style = imaginaryCircleStroke,
                        )
                    }
                }
                null -> {}
            }
        }
    }
}

private val patchForAndroid = getPlatform().kind == PlatformKind.ANDROID
private fun DrawScope.drawSelectedCircles(
    objects: List<GCircle?>,
    objectColors: Map<Ix, Color>,
    selection: List<Ix>,
    mode: Mode,
    selectionIsActive: Boolean,
    restrictRegionsToSelection: Boolean,
    showDirectionArrows: Boolean,
    visibleRect: Rect,
    selectedCircleColor: Color,
    thiccSelectionCircleAlpha: Float,
    circleThiccStroke: Stroke,
    selectedPointColor: Color,
    pointRadius: Float,
    imaginaryCircleColor: Color,
    imaginaryCircleThiccStroke: Stroke,
) {
    val showPoints = selectionIsActive
    val showCircles = selectionIsActive || mode == SelectionMode.Region && restrictRegionsToSelection
    val showImaginaryCircles = EditClusterViewModel.SHOW_IMAGINARY_CIRCLES
    for (ix in selection) {
        val objectColor = objectColors[ix]
        when (val o = objects[ix]) {
            is CircleOrLine -> if (showCircles) {
                val color = objectColor ?: selectedCircleColor
                drawCircleOrLine(
                    o, visibleRect, color,
                    alpha = thiccSelectionCircleAlpha,
                    style = circleThiccStroke,
                )
                if (showDirectionArrows) {
                    if (patchForAndroid)
                        drawArrowsPatchedForAndroid(o, visibleRect, color)
                    else
                        drawArrows(o, visibleRect, color)
                }
            }
            is Point -> if (showPoints) {
                drawCircle(objectColor ?: selectedPointColor, pointRadius, o.toOffset())
            }
            is ImaginaryCircle -> if (showImaginaryCircles) {
                drawCircleOrLine(
                    Circle(o.x, o.y, o.radius), visibleRect,
                    color = objectColor ?: imaginaryCircleColor,
                    style = imaginaryCircleThiccStroke,
                )
            }
            else -> {}
        }
    }
}

/**
 * @param[regionsOpacity] `[0; 1]` transparency of regions (filled or contoured),
 * except when used for [chessboardPattern], in that case it's defined by [chessboardColor]
 * */
private fun DrawScope.drawRegions(
    objects: List<GCircle?>,
    regions: List<LogicalRegion>,
    hiddenObjectIndices: Set<Ix>,
    chessboardPattern: ChessboardPattern,
    chessboardColor: Color,
    showWireframes: Boolean,
    visibleRect: Rect,
    @FloatRange(from = 0.0, to = 1.0)
    regionsOpacity: Float,
    regionsBlendMode: BlendMode,
    circleStroke: DrawStyle,
) {
    // NOTE: buggy on extreme zoom-in
    if (chessboardPattern != ChessboardPattern.NONE) {
        if (showWireframes) {
            for (ix in objects.indices) {
                val o = objects[ix]
                if (ix !in hiddenObjectIndices && o is CircleOrLine)
                    drawCircleOrLine(o, visibleRect, chessboardColor, style = circleStroke)
            }
        } else {
            if (chessboardPattern == ChessboardPattern.STARTS_COLORED)
                drawRect(chessboardColor, visibleRect.topLeft, visibleRect.size)
            // FIX: flickering, eg when altering spirals
            for (ix in objects.indices) { // it used to work poorly but is good now for some reason
                val o = objects[ix]
                if (ix !in hiddenObjectIndices && o is CircleOrLine)
                    drawCircleOrLine(o, visibleRect, chessboardColor, blendMode = BlendMode.Xor, drawHalfPlanesForLines = true)
            }
        }
    }
    for (region in regions) {
        val path = region2path(objects.map { it as? CircleOrLine }, region, visibleRect)
        if (showWireframes) {
            drawPath(path,
                color = region.fillColor,
                alpha = regionsOpacity,
                style = circleStroke,
                blendMode = regionsBlendMode
            )
        } else { // drawing stroke+fill to prevent seams
            drawPath(path,
                color = region.borderColor ?: region.fillColor,
                alpha = regionsOpacity,
                style = circleStroke,
                blendMode = regionsBlendMode,
            )
            drawPath(path,
                color = region.fillColor,
                alpha = regionsOpacity,
                style = Fill,
                blendMode = regionsBlendMode,
            )
        }
    }
}

// draw stuff for tool modes
private inline fun DrawScope.drawPartialConstructs(
    objects: List<GCircle?>,
    mode: Mode,
    partialArgList: PartialArgList?,
    partialArcPath: PartialArcPath?,
    crossinline getArg: (Arg) -> GCircle?,
    visibleRect: Rect,
    handleRadius: Float,
    circleStroke: Stroke,
    imaginaryCircleStroke: Stroke,
    creationPointRadius: Float = handleRadius * 3/4,
    selectedArgColor: Color = DodeclustersColors.green, //pureSecondary,
    creationPrototypeColor: Color = DodeclustersColors.green.copy(alpha = 0.7f),
) {
    // generic display for selected tool args
    partialArgList?.args?.let { args ->
        for (arg in args) {
            when (arg) {
                is Arg.Index -> when (val o = getArg(arg)) {
                    is CircleOrLine -> {
                        drawCircleOrLine(o, visibleRect, selectedArgColor,
                            style = circleStroke
                        )
                    }
                    is ImaginaryCircle -> {
                        drawCircleOrLine(o.toRealCircle(), visibleRect, selectedArgColor,
                            style = imaginaryCircleStroke
                        )
                    }
                    is Point -> {
                        drawCircle(
                            color = selectedArgColor,
                            radius = creationPointRadius,
                            center = o.toOffset()
                        )
                    }
                    else -> {}
                }
                is Arg.PointXY -> {
                    drawCircle(
                        color = selectedArgColor,
                        radius = creationPointRadius,
                        center = arg.toOffset()
                    )
                }
                is Arg.InfinitePoint -> {}
                is Arg.Indices -> {
                    for (ix in arg.indices) {
                        when (val o = objects[ix]) {
                            is CircleOrLine ->
                                drawCircleOrLine(o, visibleRect, selectedArgColor,
                                    style = circleStroke
                                )
                            is ImaginaryCircle ->
                                drawCircleOrLine(o.toRealCircle(), visibleRect, selectedArgColor,
                                    style = imaginaryCircleStroke
                                )
                            is Point ->
                                drawCircle(
                                    color = selectedArgColor,
                                    radius = creationPointRadius,
                                    center = o.toOffset()
                                )
                            null -> {}
                        }
                    }
                }
            }
        }
    }
    // custom previews for some tools
    when (mode) {
        ToolMode.CIRCLE_BY_CENTER_AND_RADIUS -> partialArgList!!.args.let { args ->
            if (args.size == 2) {
                val (center, radiusPoint) = args.map {
                    getArg(it) as Point
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
        ToolMode.CIRCLE_BY_3_POINTS -> partialArgList!!.args.let { args ->
            val gCircles = args.map { getArg(it)!! }
            if (args.size == 2) {
                val line = GeneralizedCircle.perp3(
                    GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY),
                    GeneralizedCircle.fromGCircle(gCircles[0]),
                    GeneralizedCircle.fromGCircle(gCircles[1]),
                )?.toGCircle() as? Line
                if (line != null)
                    drawCircleOrLine(line, visibleRect, creationPrototypeColor,
                        style = circleStroke
                    )
            } else if (args.size == 3) {
                val circle = GeneralizedCircle.perp3(
                    GeneralizedCircle.fromGCircle(gCircles[0]),
                    GeneralizedCircle.fromGCircle(gCircles[1]),
                    GeneralizedCircle.fromGCircle(gCircles[2]),
                )?.toGCircle() as? CircleOrLine
                if (circle != null)
                    drawCircleOrLine(circle, visibleRect, creationPrototypeColor,
                        style = circleStroke
                    )
            }
        }
        ToolMode.CIRCLE_BY_PENCIL_AND_POINT -> partialArgList!!.args.let { args ->
            val gCircles = args.map { getArg(it)!! }
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
                    drawCircleOrLine(circle, visibleRect, creationPrototypeColor,
                        style = circleStroke
                    )
            }
        }
        ToolMode.LINE_BY_2_POINTS -> partialArgList!!.args.let { args ->
            if (args.size == 2) {
                val gCircles = args.map { getArg(it)!! }
                val line = GeneralizedCircle.perp3(
                    GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY),
                    GeneralizedCircle.fromGCircle(gCircles[0]),
                    GeneralizedCircle.fromGCircle(gCircles[1]),
                )?.toGCircle() as? Line
                if (line != null)
                    drawCircleOrLine(line, visibleRect, creationPrototypeColor,
                        style = circleStroke
                    )
            }
        }
        ToolMode.ARC_PATH -> partialArcPath?.let { arcPath ->
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

private inline fun DrawScope.drawHandles(
    objects: List<GCircle?>,
    selection: List<Ix>,
    submode: SubMode?,
    handleConfig: HandleConfig?,
    crossinline getSelectionRect: () -> Rect?,
    showCircles: Boolean,
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
    if (showCircles) {
        val iconSize = Size(iconDim, iconDim)
        when (handleConfig) {
            is HandleConfig.SingleCircle -> {
                val selectedCircle = objects[selection.single()]
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
                val rectSubmode = submode as? SubMode.RectangularSelect
                val noHandles = rectSubmode?.corner1 != null && rectSubmode?.corner2 != null
                if (!noHandles) {
                    getSelectionRect()?.let { selectionRect ->
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
            null -> {}
        }
        when (submode) {
            is SubMode.RectangularSelect -> {
                val (corner1, corner2) = submode
                if (corner1 != null && corner2 != null) {
                    val rect = Rect.fromCorners(corner1, corner2)
                    drawRect(
                        color = selectionMarkingsColor,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = dottedStroke,
                    )
                }
            }
            is SubMode.Rotate -> {
                val (center, angle) = submode
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
            else -> {}
        }
    }
}

private fun DrawScope.drawGrids(
    visibleRect: Rect,
    submode: SubMode?,
    stereographicGridColor: Color,
    stereographicGridStroke: Stroke,
    southPointRadius: Float,
    gridLineAlpha: Float = 0.4f,
    equatorGridLineAlpha: Float = 0.7f,
    southPointAlpha: Float = 0.8f,
) {
    when (submode) {
        is SubMode.RotateStereographicSphere -> {
            drawCircle(
                color = stereographicGridColor,
                alpha = southPointAlpha,
                radius = southPointRadius,
                center = submode.south.toOffset(),
            )
            for (i in submode.grid.indices) {
                val circleOrLine = submode.grid[i]
                val alpha =
                    if (i == SubMode.RotateStereographicSphere.EQUATOR_GRID_INDEX) equatorGridLineAlpha
                    else gridLineAlpha
                drawCircleOrLine(circleOrLine,
                    visibleRect = visibleRect,
                    color = stereographicGridColor,
                    alpha = alpha,
                    style = stereographicGridStroke,
                )
            }
        }
        else -> {}
    }
}

private const val MEDIUM_ARROW_TAIL_LENGTH = 5f
private const val MEDIUM_ARROW_HEAD_LENGTH = 30f
private const val MEDIUM_ARROW_HALF_HEIGHT = 10f
/** Filled right/east-oriented arrow */
private val MEDIUM_ARROW_PATH = Path().apply {
    // starts at (0,0)
    lineTo(-MEDIUM_ARROW_TAIL_LENGTH, -MEDIUM_ARROW_HALF_HEIGHT)
    lineTo(MEDIUM_ARROW_HEAD_LENGTH, 0f)
    lineTo(-MEDIUM_ARROW_TAIL_LENGTH, MEDIUM_ARROW_HALF_HEIGHT)
    close()
}

fun DrawScope.drawRotationHandle(
    positions: OnScreenPositions,
    rotationAngle: Float,
    handleColor: Color,
    handleBackgroundColor: Color,
) {
    val (centerX, centerY) = positions.center
    val radius = positions.rotationHandleRadius
    val sweepAngle = 15f
    val preAngle = positions.rotationHandle0Angle - sweepAngle/2f
    val startAngle = preAngle + rotationAngle
    val rect = Rect(positions.center, radius)
    val stroke = Stroke(7.5f)
    val sweepFraction = sweepAngle/360f
    val brush = Brush.sweepGradient(
        0.00f*sweepFraction to handleColor,
        0.20f*sweepFraction to handleBackgroundColor,
        // sweepFraction/2
        0.80f*sweepFraction to handleBackgroundColor,
        1.00f*sweepFraction to handleColor,
        center = positions.center,
    )
    withTransform({
        rotate(startAngle + 90f, positions.center)
        translate(centerX, centerY - radius)
        rotate(180f, Offset.Zero)
    }) {
        drawPath(MEDIUM_ARROW_PATH, handleColor)
    }
    withTransform({
        rotate(startAngle + sweepAngle + 90f, positions.center)
        translate(centerX, centerY - radius)
    }) {
        drawPath(MEDIUM_ARROW_PATH, handleColor)
    }
    rotate(startAngle, positions.center) { // orient Eastward
        drawArc(
            brush,
//        handleColor,
            0f, sweepAngle,
            useCenter = false,
            rect.topLeft,
            rect.size,
            style = stroke,
        )
    }
}

// TODO: remove/decrease bottom margin when in landscape
@Immutable
data class OnScreenPositions(
    val width: Float,
    val height: Float,
) {
    val center = Offset(width/2f, height/2f)
    val east = Offset(width, center.y)

    val top = height * RELATIVE_VERTICAL_MARGIN
    val verticalSliderPadding = height * RELATIVE_VERTICAL_SLIDER_PADDING
    val verticalSliderSpan = height * RELATIVE_VERTICAL_SLIDER_HEIGHT
    val verticalSliderBottom = top + height * RELATIVE_VERTICAL_SLIDER_HEIGHT
    val underVerticalSlider = verticalSliderBottom + height * RELATIVE_VERTICAL_SLIDER_BOTTOM_INDENT
    val bottom = height * (1 - RELATIVE_VERTICAL_MARGIN)
    val halfHigherThanBottom = (underVerticalSlider + bottom)/2f

    val right = width * (1 - RELATIVE_RIGHT_MARGIN)
    val left = right - (bottom - underVerticalSlider)
    val mid = (right + left)/2

    val horizontalSliderStart = width - min(width, height) * RELATIVE_HORIZONTAL_SLIDER_SPAN
    val horizontalSliderSpan = right - horizontalSliderStart

    val rotationHandleRadius =
        0.24f*height + 0.3f*width
    val rotationHandle0Angle = 0.95f * center
        .angleDeg(
            east,
            Offset(width, height)
        )
    val rotationHandle0 = Offset(center.x + rotationHandleRadius, center.y)
        .rotateByAround(rotationHandle0Angle, center)

    @Suppress("NOTHING_TO_INLINE")
    @Stable
    inline fun rotationHandleOffset(rotationAngle: Float): Offset {
        val offset = rotationHandle0
            .rotateByAround(rotationAngle, center)
        return offset
    }

    companion object {
        const val RELATIVE_RIGHT_MARGIN = 0.05f // = % of W
        const val RELATIVE_VERTICAL_MARGIN = 0.15f // = % of H
        const val RELATIVE_VERTICAL_SLIDER_HEIGHT = 0.40f // = % of H
        const val RELATIVE_VERTICAL_SLIDER_PADDING = 0.02f // = % of H
        const val RELATIVE_VERTICAL_SLIDER_BOTTOM_INDENT = 0.10f // = % of H
        const val RELATIVE_HORIZONTAL_SLIDER_SPAN = 0.60f // = 60% of min-dimension
    }
}

@Immutable
data class ConcreteOnScreenPositions(
    val size: Size,
    val density: Density,
    val halfSize: Dp = 24.dp,
) {
    val positions: OnScreenPositions = OnScreenPositions(
        size.width, size.height
    )

    @Stable
    fun offsetModifier(x: Float, y: Float): Modifier =
        with (density) {
            Modifier.offset(
                x = x.toDp() - halfSize,
                y = y.toDp() - halfSize,
            )
        }

    val horizontalSliderWidth = with (density) {
        positions.horizontalSliderSpan.toDp()
    } - halfSize

    val verticalSliderHeight = with (density) {
        positions.verticalSliderSpan.toDp()
    }

    val topRightModifier = offsetModifier(positions.right, positions.top)
    val rightUnderVerticalSliderModifier = offsetModifier(positions.right, positions.underVerticalSlider)
    val midUnderVerticalSliderModifier = offsetModifier(positions.mid, positions.underVerticalSlider)
    val halfBottomRightModifier = offsetModifier(positions.right, positions.halfHigherThanBottom)
    val bottomRightModifier = offsetModifier(positions.right, positions.bottom)
    val horizontalSliderModifier =
        with (density) { Modifier.offset(
            x = positions.horizontalSliderStart.toDp(),
            y = positions.bottom.toDp() - halfSize,
        ) }
    val preHorizontalSliderModifier =
        with (density) { Modifier.offset(
            x = positions.horizontalSliderStart.toDp() - 4.dp - halfSize,
            y = positions.bottom.toDp() - halfSize,
        ) }
    val verticalSliderModifier =
        with (density) { Modifier.offset(
            x = positions.right.toDp() - halfSize,
            y = (positions.top + positions.verticalSliderPadding).toDp()
        ) }
    val topMidModifier = offsetModifier(positions.mid, positions.top)
    val verticalSlider2Modifier =
        with (density) { Modifier.offset(
            x = positions.mid.toDp() - halfSize,
            y = (positions.top + positions.verticalSliderPadding).toDp()
        ) }

    // FIX: it's shivering during rotation (!?)
    @Stable
    fun rotationHandleModifier(rotationAngle: Float): Modifier {
        val offset = positions.rotationHandleOffset(rotationAngle)
        val handleHalfSize = 18.dp
        return with (density) {
            Modifier.offset(
                offset.x.toDp() - handleHalfSize,
                offset.y.toDp() - handleHalfSize,
            )
        }
    }
}