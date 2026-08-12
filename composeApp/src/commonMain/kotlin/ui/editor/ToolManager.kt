package ui.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.viewModelScope
import core.geometry.CircleOrLine
import core.geometry.CircleOrLineOrPoint
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteArcPath
import core.geometry.ImaginaryCircle
import core.geometry.Line
import core.geometry.Point
import domain.Ix
import domain.PointSnapResult
import domain.entails
import domain.expressions.ArcPath
import domain.expressions.ConformalExpressions
import domain.expressions.Expr
import domain.expressions.ExtrapolationParameters
import domain.expressions.computeConcentricCircle
import domain.model.Arg
import domain.model.ConformalObjectModel
import domain.model.PartialArgList
import domain.model.Selection
import domain.model.Styling
import domain.never
import domain.settings.Settings
import kotlinx.coroutines.launch
import ui.editor.EditorViewModel.Companion.downscale
import ui.editor.EditorViewModel.Companion.upscale
import ui.editor.dialogs.DefaultExtrapolationParameters
import ui.tools.Tool

@Stable
class ToolManager(
    private val objectModel: ConformalObjectModel,
    modeState: MutableState<Mode>,
    submodeState: MutableState<Submode?>,
    selectionState: MutableState<Selection>,
    partialArgListState: MutableState<PartialArgList?>,
) {
    private val mode: Mode by modeState
    private var submode: Submode? by submodeState
    private var selection: Selection by selectionState
    // NOTE: Arg.XYPoint & co use absolute positioning
    /** Partly filled [Tool] arg-list during [ToolMode] */
    private var partialArgList: PartialArgList? by partialArgListState

    private val objects: List<GCircleOrConcreteArcPath?> = objectModel.displayObjects
    private inline val expressions: ConformalExpressions get() =
        objectModel.expressions


    // MAYBE: axis-aligned cross centered at a point
    context(viewModel: EditorViewModel)
    fun insertCenteredCross() {
        val (midX, midY) = viewModel.canvasState.canvasSize.toSize()/2f
        val horizontalLine = Line.by2Points(
            viewModel.absolute(Offset(0f, midY)),
            viewModel.absolute(Offset(2*midX, midY)),
        )
        val verticalLine = Line.by2Points(
            viewModel.absolute(Offset(midX, 0f)),
            viewModel.absolute(Offset(midX, 2*midY)),
        )
        viewModel.updateCanvasState { it.copy(
            showCircles = true
        ) }
        expressions.addFree()
        expressions.addFree()
        viewModel.createNewGCircles(listOf(horizontalLine, verticalLine))
        viewModel.switchToMode(SelectionMode.Multiselect) // idk it's weird
        val indices = listOf(objects.size - 2, objects.size - 1)
        selection = Selection(gCircles = indices)
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    fun downToolArg(absolutePosition: Offset) {
        val argList = partialArgList
        val nextType = argList?.nextArgType
        if (nextType != null) {
            val inInterpolationMode = mode == ToolMode.CIRCLE_OR_POINT_INTERPOLATION
            val inFastCenteredCircle =
                Settings.FAST_CENTERED_CIRCLE && mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS
            /** flags whether we already selected/found an object and there's no
             * more need to proceed further */
            var found = false
            var pointSnap: PointSnapResult? = null
            // try selecting an existing (indexed) point
            if (nextType.acceptsPointIndex) {
                pointSnap = viewModel.snapped(absolutePosition, includePoints = mode != ToolMode.POINT)
                when (pointSnap) {
                    is PointSnapResult.Eq -> {
                        val newArg = Arg.PointIndex(pointSnap.pointIndex)
                        if (inFastCenteredCircle && argList.currentArg == null) {
                            partialArgList = argList
                                .addArg(newArg, confirmThisArg = true)
                                .addArg(Arg.PointXY(pointSnap.result), confirmThisArg = false)
                                .copy(lastSnap = pointSnap)
                            found = true
                        } else {
                            val sameArgsForInterpolation = inInterpolationMode entails
                                (argList.args.isEmpty() || argList.currentArg is Arg.Point)
                            if (argList.validateNewArg(newArg) && sameArgsForInterpolation) {
                                partialArgList = argList
                                    .addArg(newArg, confirmThisArg = false)
                                    .copy(lastSnap = pointSnap)
                            }
                            found = true
                        }
                    }
                    else -> {}
                }
            }
            // try selecting an existing (indexed) object
            if (!found && nextType.acceptsCLI &&
                (inInterpolationMode entails (argList.currentArg?.type !is Arg.Type.Point))
            ) {
                viewModel.getCirclesAround(absolutePosition).firstOrNull()?.let { ix ->
                    val newArg = Arg.IndexOf(ix, objects[ix] as GCircle)
                    // test non-equality conditions
                    if (argList.validateNewArg(newArg)) {
                        if (inFastCenteredCircle && argList.currentArg == null) {
                            pointSnap = viewModel.snapped(absolutePosition, excludedIndices = setOf(ix))
                            partialArgList = argList
                                .addArg(newArg, confirmThisArg = true)
                                .addArg(Arg.PointXY(pointSnap.result), confirmThisArg = false)
                                .copy(lastSnap = pointSnap)
                        } else {
                            val confirm = !inInterpolationMode
                            partialArgList = argList.addArg(newArg, confirmThisArg = confirm)
                        }
                    }
                    found = true
                }
            }
            // try selecting a new point
            if (!found && nextType.acceptsPointXY) {
                val snap = pointSnap
                    ?: viewModel.snapped(absolutePosition, includePoints = mode != ToolMode.POINT)
                if (inFastCenteredCircle && argList.currentArg == null) {
                    // we have to realize the first point here so we don't forget its
                    // snap after panning
                    val newArg = viewModel.realizePointSnap(snap).toArgPoint()
                    val newArg2 = Arg.PointXY(snap.result)
                    partialArgList = argList
                        .addArg(newArg, confirmThisArg = true)
                        .addArg(newArg2, confirmThisArg = false)
                        .copy(lastSnap = pointSnap)
                    found = true
                } else if (
                // first point-interpolation arg cannot be XY ig
                    inInterpolationMode entails (argList.currentArg is Arg.Point)
                ) {
                    val newArg = Arg.PointXY(snap.result)
                    if (argList.validateNewArg(newArg)) {
                        partialArgList = argList
                            .addArg(newArg, confirmThisArg = false)
                            .copy(lastSnap = snap)
                    }
                    found = true
                }
            }
            // try selecting an existing object (singular as a group)
            if (!found && nextType.acceptsIndices) {
                val selectedPointIndex = viewModel.getPointsAround(absolutePosition).firstOrNull()
                if (selectedPointIndex == null) {
                    val selectedCircleIndex = viewModel.getCirclesAround(absolutePosition).firstOrNull()
                    if (selectedCircleIndex == null) {
                        val selectedArcPathIndex = viewModel.getArcPathsAround(absolutePosition).firstOrNull()
                        // we don't select in-filled arc-paths here i think
                        if (selectedArcPathIndex != null) {
                            val newArg = Arg.Indices(listOf(selectedArcPathIndex))
                            if (argList.validateNewArg(newArg)) {
                                partialArgList = argList.addArg(newArg, confirmThisArg = true)
                            }
                            found = true
                        }
                    } else {
                        val newArg = Arg.Indices(listOf(selectedCircleIndex))
                        if (argList.validateNewArg(newArg)) {
                            partialArgList = argList.addArg(newArg, confirmThisArg = true)
                        }
                        found = true
                    }
                } else {
                    val newArg = Arg.Indices(listOf(selectedPointIndex))
                    if (argList.validateNewArg(newArg)) {
                        partialArgList = argList.addArg(newArg, confirmThisArg = true)
                    }
                    found = true
                }
            }
        }
    }

    /** @return whether a tool arg is actually updated */
    context(viewModel: EditorViewModel)
    fun tryUpdatingToolArg(absolutePosition: Offset): Boolean {
        val snap = viewModel.snapped(absolutePosition, includePoints = mode != ToolMode.POINT)
        val absolutePoint = snap.result
        val argList = partialArgList
        val currentArg = argList?.currentArg
        val currentArgType = argList?.currentArgType
        if (mode is ToolMode &&
            currentArgType?.possibleTypes?.any { it is Arg.Type.Point } == true &&
            ((mode == ToolMode.CIRCLE_OR_POINT_INTERPOLATION) entails (currentArg?.type is Arg.Type.Point))
        ) {
            val newArg = when (snap) {
                is PointSnapResult.Eq -> Arg.PointIndex(snap.pointIndex)
                else -> Arg.PointXY(absolutePoint)
            }
            if (argList.validateUpdatedArg(newArg)) {
                partialArgList = argList
                    .updateCurrentArg(newArg, confirmThisArg = false)
                    .copy(lastSnap = snap)
            }
            return true
        }
        return false
    }

    context(viewModel: EditorViewModel)
    fun upToolMode(absolutePosition: Offset?) {
        if (submode == null) {
            var argList = partialArgList
            // we only confirm args in 0nUp, they are created in 0nDown etc.
            val newArg = when (argList?.currentArg) {
                is Arg.Point -> absolutePosition?.let {
                    val args = argList.args
                    val snap = viewModel.snapped(absolutePosition,
                        includePoints = mode != ToolMode.POINT,
                    )
                    // we cant realize it here since for fast circles the first point already has been
                    // realized in 0nDown and we don't know yet if we moved far enough from it to
                    // create the second point
                    if (mode == ToolMode.CIRCLE_BY_CENTER_AND_RADIUS &&
                        Settings.FAST_CENTERED_CIRCLE &&
                        args.size == 2
                    ) {
                        val centerPoint: CircleOrLineOrPoint =
                            when (val centerArg = args[0]) {
                                is Arg.Index -> objects[centerArg.index] as CircleOrLineOrPoint
                                is Arg.FixedPoint -> centerArg.toPoint()
                                else -> never(centerArg)
                            }
                        val secondPointIsTooClose = centerPoint.distanceFrom(snap.result) < 1e-3
                        if (secondPointIsTooClose) { // haxxz
                            argList = argList.copy(
                                args = args.take(1),
                                lastArgIsConfirmed = true,
                                lastSnap = null
                            )
                            null
                        } else {
                            viewModel.realizePointSnap(snap).toArgPoint()
                        }
                    } else {
                        viewModel.realizePointSnap(snap).toArgPoint()
                        // realized, but might be invalid (nonEqualityConditions)
                    }
                }
                else -> null
            }
            partialArgList = if (
                newArg == null ||
                argList?.validateUpdatedArg(newArg) != true
            )
                argList?.copy(lastArgIsConfirmed = true)
            else
                argList.updateCurrentArg(newArg, confirmThisArg = true)
            if (partialArgList?.isFull == true) {
                viewModel.completeToolMode()
            }
        }
    }

    context(viewModel: EditorViewModel)
    fun setActiveSelectionAsToolArg() {
        val argList = partialArgList
        val validState = viewModel.toolbarState.activeTool.let { tool ->
            tool is Tool.MultiArg &&
            Arg.Indices in tool.signature.argTypes.first().possibleTypes &&
            selection.isNotEmpty() &&
            argList != null
        }
        if (!validState) { // in case snackbar prompt outlives validity
            println("Illegal state in setActiveSelectionAsToolArg(): tool = ${viewModel.toolbarState.activeTool}, selection == $selection")
            return
        }
        partialArgList = argList?.addArg(
            Arg.Indices(selection.indices),
            confirmThisArg = true
        )
        if (partialArgList?.isFull == true) {
            viewModel.completeToolMode()
        }
    }

    context(viewModel: EditorViewModel)
    fun addInfinitePointArg() {
        val argList = partialArgList
        require(
            argList != null && !argList.isFull &&
            argList.nextArgType?.let { nextArgType ->
                Arg.InfinitePoint in nextArgType.possibleTypes
            } == true
        )
        val infinityIndex = objectModel.getInfinityIndex()
            ?: viewModel.createNewFreePoint(Point.CONFORMAL_INFINITY)
        val newArg =
            if (Arg.Indices in argList.nextArgType.possibleTypes)
                Arg.Indices(listOf(infinityIndex))
            else
                Arg.PointIndex(infinityIndex)
        if (argList.validateNewArg(newArg)) {
            partialArgList = argList.addArg(newArg, confirmThisArg = true)
            if (partialArgList?.isFull == true) {
                viewModel.completeToolMode()
            }
        }
    }

    context(viewModel: EditorViewModel)
    fun completeCircleByCenterAndRadius() {
        val argList = partialArgList ?: return
        val centerArg = argList.args[0]
        val pointArg = argList.args[1]
        require(centerArg is Arg.CircleIndex || centerArg is Arg.LineIndex || centerArg is Arg.PointIndex || centerArg is Arg.PointXY)
        require(pointArg is Arg.CircleIndex || pointArg is Arg.PointIndex || pointArg is Arg.PointXY)
        if (!Settings.ALWAYS_CREATE_ADDITIONAL_POINTS && centerArg is Arg.PointXY && pointArg is Arg.PointXY) {
            val newCircle = computeConcentricCircle(
                samePencilObject = centerArg.toPoint().downscale(),
                point = pointArg.toPoint().downscale(),
            )?.upscale()
            viewModel.createNewGCircle(newCircle)
            expressions.addFree()
        } else {
            val realizedCenterArg = when (centerArg) {
                is Arg.Index -> centerArg.index
                is Arg.PointXY -> viewModel.createNewFreePoint(centerArg.toPoint())
                else -> never(centerArg)
            }
            val realizedPointArg = when (pointArg) {
                is Arg.Index -> pointArg.index
                is Arg.PointXY -> viewModel.createNewFreePoint(pointArg.toPoint())
                else -> never(pointArg)
            }
            val newCircle = expressions.addSoloExpr(
                Expr.CircleByCenterAndRadius(
                    center = realizedCenterArg,
                    radiusPoint = realizedPointArg,
                ),
            ) as? CircleOrLine
            viewModel.createNewGCircle(newCircle?.upscale())
        }
        partialArgList = argList.copyEmpty()
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    fun completeCircleBy3Points() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.CLIP }
        // i think circle by 3 implies we want to move these points later
        val realized = args.map {
            when (it) {
                is Arg.Index -> it.index
                is Arg.FixedPoint -> viewModel.createNewFreePoint(it.toPoint())
            }
        }
        val newGCircle = expressions.addSoloExpr(
            Expr.CircleBy3Points(
                object1 = realized[0],
                object2 = realized[1],
                object3 = realized[2],
            ),
        ) as? GCircle
        viewModel.createNewGCircle(newGCircle?.upscale())
        if (newGCircle is ImaginaryCircle) {
            viewModel.showSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
        }
        partialArgList = argList.copyEmpty()
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    fun completeCircleByPencilAndPoint() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.CLIP }
        val realized = args.map {
            when (it) {
                is Arg.Index -> it.index
                is Arg.FixedPoint -> viewModel.createNewFreePoint(it.toPoint())
            }
        }
        val newGCircle = expressions.addSoloExpr(
            Expr.CircleByPencilAndPoint(
                pencilObject1 = realized[0],
                pencilObject2 = realized[1],
                perpendicularObject = realized[2],
            ),
        ) as? GCircle
        viewModel.createNewGCircle(newGCircle?.upscale())
        if (newGCircle is ImaginaryCircle) {
            viewModel.showSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
        }
        partialArgList = argList.copyEmpty()
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    fun completeLineBy2Points() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.CLIP }
        val realized = args.map {
            when (it) {
                is Arg.Index -> it.index
                is Arg.FixedPoint -> viewModel.createNewFreePoint(it.toPoint())
            }
        }
        val infinityIndex = objectModel.getInfinityIndex()
            ?: viewModel.createNewFreePoint(Point.CONFORMAL_INFINITY)
        val newGCircle = expressions.addSoloExpr(
            Expr.CircleBy3Points(
                object1 = realized[0],
                object2 = realized[1],
                object3 = infinityIndex,
            ),
        ) as? GCircle
        viewModel.createNewGCircle(newGCircle?.upscale())
        partialArgList = argList.copyEmpty()
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    fun completePolarityByCircleAndLineOrPoint() {
        val argList = partialArgList ?: return
        val circleArg = argList.args[0] as Arg.CircleIndex
        val lineOrPointArg = argList.args[1] as Arg.LP
        val newExpr = when (lineOrPointArg) {
            is Arg.LineIndex -> {
                Expr.PoleByCircleAndLine(
                    circle = circleArg.index,
                    line = lineOrPointArg.index,
                )
            }
            is Arg.Point -> {
                val realizedPointIndex = when (lineOrPointArg) {
                    is Arg.PointIndex -> lineOrPointArg.index
                    is Arg.FixedPoint -> viewModel.createNewFreePoint(lineOrPointArg.toPoint())
                }
                Expr.PolarLineByCircleAndPoint(
                    circle = circleArg.index,
                    point = realizedPointIndex,
                )
            }
        }
        val newGCircle = expressions.addSoloExpr(newExpr) as? GCircle
        viewModel.createNewGCircle(newGCircle?.upscale())
        partialArgList = argList.copyEmpty()
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    fun completeCircleInversion() {
        val argList = partialArgList ?: return
        val sources = expressions.sortedByTier(
            (argList.args[0] as Arg.Indices).indices
        )
        val gCircleSources = sources.filter { objects[it] is GCircle }
        val arcPathSources = sources.filter { objects[it] is ConcreteArcPath }
        val invertingCircleIndex = (argList.args[1] as Arg.CLI).index
        val oldSize = objects.size
        for (sourceIndex in gCircleSources) {
            val newGCircle = expressions.addSoloExpr(
                Expr.CircleInversion(sourceIndex, invertingCircleIndex),
            ) as? GCircle
            val newIndex = objectModel.addDownscaledObject(newGCircle)
            viewModel.copyStyle(sourceIndex, newIndex)
        }
        val newIndices1 = oldSize until objects.size
        for (ix in arcPathSources) {
            copyArcPath(ix) { pointIndex ->
                Expr.CircleInversion(pointIndex, invertingCircleIndex)
            }
        }
        val newIndices = oldSize until objects.size
        viewModel.copyRegions(
            gCircleSources, newIndices1.toList(),
            flipInAndOut = true
        )
        selection = Selection(
            gCircles = newIndices.filter { objects[it] is GCircle },
            arcPaths = newIndices.filter { objects[it] is ConcreteArcPath },
        )
        partialArgList = argList.copyEmpty()
        val ix2o = newIndices.mapNotNull { ix ->
            objects[ix]?.let { ix to it }
        }.toMap()
        viewModel.viewModelScope.launch {
            viewModel.animations.emit(AppearanceAnimation.Entrance(ix2o))
        }
        objectModel.invalidate()
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    private inline fun copyArcPath(
        sourceArcPathIndex: Ix,
        crossinline mkExpr: (pointIndex: Ix) -> Expr.Conformal.OneToOne,
    ) {
        require(objectModel.getArcPath(sourceArcPathIndex) is ArcPath)
        val sourceArcPath = viewModel.realizeArcPathMidpoints(sourceArcPathIndex)
        val copiedVertices = sourceArcPath.vertices.map { vertexIndex ->
            val expr = mkExpr(vertexIndex)
            val result = expressions.addSoloExpr(expr) as? Point
            val newIndex = objectModel.addDownscaledObject(result)
            viewModel.copyStyle(vertexIndex, newIndex)
            newIndex
        }
        val copiedArcs = sourceArcPath.arcs.map { arc ->
            when (arc) {
                is ArcPath.Arc.By3Points -> {
                    val sourceIndex = arc.middlePointIndex
                    val expr = mkExpr(sourceIndex)
                    val result = expressions.addSoloExpr(expr) as? Point
                    val newIndex = objectModel.addDownscaledObject(result)
                    viewModel.copyStyle(sourceIndex, newIndex)
                    ArcPath.Arc.By3Points(middlePointIndex = newIndex)
                }
                is ArcPath.Arc.By2Points ->
                    never("arc-path $sourceArcPath should have no 2-point arcs after realizeArcPathMidpoints")
            }
        }
        val concreteArcPath = expressions.addSoloExpr(
            when (sourceArcPath) {
                is ArcPath.Closed -> ArcPath.Closed(vertices = copiedVertices, arcs = copiedArcs)
                is ArcPath.Open -> ArcPath.Open(vertices = copiedVertices, arcs = copiedArcs)
            }
        )
        val copiedArcPathIndex = objectModel.addDownscaledObject(concreteArcPath)
        viewModel.copyStyle(sourceArcPathIndex, copiedArcPathIndex)
    }

    context(viewModel: EditorViewModel)
    fun completeCircleExtrapolation(
        params: ExtrapolationParameters,
    ) {
        viewModel.updateUiState { it.copy(
            openedDialog = null
        ) }
        val argList = partialArgList ?: return
        val startCircleIx = (argList.args[0] as Arg.CLI).index
        val endCircleIx = (argList.args[1] as Arg.CLI).index
        val newGCircles = expressions.addMultiExpr(
            Expr.CircleExtrapolation(params, startCircleIx, endCircleIx),
        ).map { (it as? GCircle)?.upscale() }
        viewModel.createNewGCircles(newGCircles)
        partialArgList = argList.copyEmpty()
        viewModel.defaultExtrapolationParameters = DefaultExtrapolationParameters(params)
        objectModel.invalidate()
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    fun completePoint() {
        val argList = partialArgList ?: return
        val args = argList.args.map { it as Arg.Point }
        val arg0 = args[0]
        if (arg0 is Arg.PointXY) {
            val newPoint = arg0.toPoint()
            val ix = viewModel.createNewFreePoint(newPoint)
            selection = Selection(gCircles = listOf(ix))
           viewModel. recordHistory()
        } // it could have already done it with realized PSR.Eq, which results in Arg.Point.Index
        partialArgList = argList.copyEmpty()
    }

}