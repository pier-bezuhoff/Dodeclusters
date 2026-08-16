package ui.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import core.geometry.CircleOrLine
import core.geometry.CircleOrLineOrImaginaryCircle
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteArcPath
import core.geometry.ImaginaryCircle
import core.geometry.Point
import core.geometry.Region
import core.geometry.conformal.GeneralizedCircle
import domain.Ix
import domain.expressions.ArcPath
import domain.expressions.ArcPathArcMidpointParameters
import domain.expressions.BiInversionParameters
import domain.expressions.ConformalExpressions
import domain.expressions.Expr
import domain.expressions.Expr.TransformLike
import domain.expressions.Expr.Adjustable
import domain.expressions.ExprOutput
import domain.expressions.InterpolationParameters
import domain.expressions.LoxodromicMotionParameters
import domain.expressions.Parameters
import domain.expressions.RotationParameters
import domain.expressions.areCompatibleTransforms
import domain.expressions.changeTarget
import domain.expressions.copy
import domain.expressions.copyWithNewParameters
import domain.expressions.reIndex
import domain.model.Arg
import domain.model.ConformalObjectModel
import domain.model.LogicalRegion
import domain.model.PartialArgList
import domain.model.Selection
import domain.never
import domain.transpose
import domain.updated
import ui.editor.EditorViewModel.Companion.upscale
import ui.editor.dialogs.DefaultBiInversionParameters
import ui.editor.dialogs.DefaultInterpolationParameters
import ui.editor.dialogs.DefaultLoxodromicMotionParameters
import ui.editor.dialogs.DefaultRotationParameters
import ui.tools.Tool

// im abusing context(EditorViewModel) here, which is kinda lazy and NG
class ExprAdjustmentManager(
    private val objectModel: ConformalObjectModel,
    submodeState: MutableState<Submode?>,
    partialArgListState: MutableState<PartialArgList?>,
) {
    private var submode: Submode? by submodeState
    private var partialArgList: PartialArgList? by partialArgListState

    private val objects: List<GCircleOrConcreteArcPath?> = objectModel.displayObjects
    private inline val expressions: ConformalExpressions get() =
        objectModel.expressions

    context(viewModel: EditorViewModel)
    fun startCircleOrPointInterpolationParameterAdjustment() {
        val argList = partialArgList ?: return
        val params = viewModel.defaultInterpolationParameters.params
        val (startArg, endArg) = argList.args.map { it as Arg.CLIP }
        if (startArg is Arg.CLI && endArg is Arg.CLI) {
            viewModel.interpolateCircles = true
            val c1 = objects[startArg.index] as CircleOrLineOrImaginaryCircle
            val c2 = objects[endArg.index] as CircleOrLineOrImaginaryCircle
            val scalarProduct =
                GeneralizedCircle.fromGCircle(c1) scalarProduct
                GeneralizedCircle.fromGCircle(c2)
            val coDirected = scalarProduct >= 0.0
            val onlyInBetweenExists =
                c1 is CircleOrLine && c2 is CircleOrLine &&
                c1.getRegionLocation(c2) != Region.RegionLocation.OVERLAPS
            val inBetween = onlyInBetweenExists || params.inBetween
            viewModel.circlesAreCoDirected = coDirected
            viewModel.defaultInterpolationParameters = viewModel.defaultInterpolationParameters.copy(
                inBetween = inBetween
            )
            val expr = Expr.CircleInterpolation(
                params.copy(
                    complementary =
                        if (coDirected)
                            !inBetween
                        else inBetween
                ),
                startArg.index, endArg.index
            )
            val oldSize = objects.size
            val newGCircles = expressions.addMultiExpr(expr)
            val newCircles = newGCircles.map { (it as? GCircle)?.upscale() }
            objectModel.addDisplayObjects(newCircles)
            val outputRange = (oldSize until objects.size).toList()
            submode = Submode.ExprAdjustment(listOf(
                // here sourceIndex is less meaningful
                AdjustableExpr(expr,
                    sourceIndex = startArg.index,
                    outputRange, outputRange
                )
            ))
            if (newGCircles.any { it is ImaginaryCircle }) {
                viewModel.showSnackbarMessage(SnackbarMessage.IMAGINARY_CIRCLE_NOTICE)
            }
        } else if (startArg is Arg.Point && endArg is Arg.Point) {
            viewModel.interpolateCircles = false
            val (startPointIndex, endPointIndex) = listOf(startArg, endArg).map { pointArg ->
                when (pointArg) {
                    is Arg.PointIndex -> pointArg.index
                    is Arg.FixedPoint -> viewModel.createNewFreePoint(pointArg.toPoint())
                }
            }
            val expr = Expr.PointInterpolation(params, startPointIndex, endPointIndex)
            val oldSize = objects.size
            val newGCircles = expressions.addMultiExpr(expr)
            val newPoints = newGCircles.map { (it as? Point)?.upscale() }
            objectModel.addDisplayObjects(newPoints)
            val outputRange = (oldSize until objects.size).toList()
            submode = Submode.ExprAdjustment(listOf(
                AdjustableExpr(expr,
                    sourceIndex = startPointIndex,
                    outputRange, outputRange
                )
            ))
        } else {
            println("startCircleOrPointInterpolationAdjustment: invalid args $argList")
        }
        objectModel.invalidate()
    }

    // NOTE: witnessed abnormal index skipping whe rotating lines, observing closely...
    context(viewModel: EditorViewModel)
    fun startRotationParameterAdjustment() {
        val argList = partialArgList ?: return
        val objArg = argList.args[0] as Arg.Indices
        val pointArg = argList.args[1] as Arg.Point
        val pivotPointIndex = when (pointArg) {
            is Arg.PointIndex -> pointArg.index
            is Arg.FixedPoint -> viewModel.createNewFreePoint(pointArg.toPoint())
        }
        val parameters = viewModel.defaultRotationParameters.params
        submode = populateExprAdjustmentSubmode(objArg.indices) { ix ->
            Expr.Rotation(parameters, pivotPointIndex, ix)
        }
        objectModel.invalidate()
    }

    context(viewModel: EditorViewModel)
    fun startBiInversionParameterAdjustment() {
        val argList = partialArgList ?: return
        val args = argList.args
        val objArg = args[0] as Arg.Indices
        val engine1 = (args[1] as Arg.CLI).index
        val engine2 = (args[2] as Arg.CLI).index
        val engine1GC = GeneralizedCircle.fromGCircle(objects[engine1] as CircleOrLineOrImaginaryCircle)
        val engine2GC0 = GeneralizedCircle.fromGCircle(objects[engine2] as CircleOrLineOrImaginaryCircle)
        val reverseSecondEngine = engine1GC scalarProduct engine2GC0 < 0 // anti-parallel
        viewModel.defaultBiInversionParameters = viewModel.defaultBiInversionParameters.copy(
            reverseSecondEngine = reverseSecondEngine
        )
        val parameters = viewModel.defaultBiInversionParameters.params
        submode = populateExprAdjustmentSubmode(objArg.indices) { ix ->
            Expr.BiInversion(parameters, engine1, engine2, ix)
        }
        objectModel.invalidate()
    }

    context(viewModel: EditorViewModel)
    fun startLoxodromicMotionParameterAdjustment() {
        setupLoxodromicSpiral(bidirectional =
            viewModel.defaultLoxodromicMotionParameters.bidirectional
        )
    }

    context(viewModel: EditorViewModel)
    fun setupLoxodromicSpiral(bidirectional: Boolean) {
        val argList = partialArgList ?: return
        val args = argList.args
        val objArg = args[0] as Arg.Indices
        val divergenceArg = args[1] as Arg.Point
        val convergenceArg = args[2] as Arg.Point
        val (divergencePointIndex, convergencePointIndex) = listOf(divergenceArg, convergenceArg)
            .map { when (it) {
                is Arg.PointIndex -> it.index
                is Arg.FixedPoint -> viewModel.createNewFreePoint(it.toPoint())
            } }
        partialArgList = argList.copy(
            // we dont want to spam create the same Point.XY each time
            args = listOf(
                objArg, Arg.PointIndex(divergencePointIndex), Arg.PointIndex(convergencePointIndex)
            ),
        )
        val parameters = viewModel.defaultLoxodromicMotionParameters.params
        if (bidirectional) { // 2 interdependent spiral halves
            val (adjustables1, arcPathAdjustables1) =
                populateExprAdjustmentSubmode(objArg.indices) { ix ->
                    Expr.LoxodromicMotion(parameters,
                        divergencePointIndex, convergencePointIndex,
                        target = ix,
                    )
                }
            val (adjustables2, arcPathAdjustables2) =
                populateExprAdjustmentSubmode(objArg.indices) { ix ->
                    Expr.LoxodromicMotion(parameters,
                        convergencePointIndex, divergencePointIndex,
                        target = ix,
                    )
                }
            // we forcefully interlink forward & backward trajectories to each other
            val forwardAdjustables = mutableListOf<AdjustableExpr<Expr.LoxodromicMotion>>()
            val backwardAdjustables = mutableListOf<AdjustableExpr<Expr.LoxodromicMotion>>()
            for (i in adjustables1.indices) {
                val adjustable1 = adjustables1[i]
                val adjustable2 = adjustables2[i]
                val forwardHalfStart = adjustable1.occupiedIndices.first()
                val backwardHalfStart = adjustable2.occupiedIndices.first()
                val expr1 = adjustable1.expr.copy(otherHalfStart = backwardHalfStart)
                val expr2 = adjustable2.expr.copy(otherHalfStart = forwardHalfStart)
                forwardAdjustables.add(adjustable1.copy(expr = expr1))
                backwardAdjustables.add(adjustable2.copy(expr = expr2))
                for (ix in adjustable1.occupiedIndices) {
                    val expression = expressions[ix] as ExprOutput.OneOf
                    expressions.expressions[ix] = expression.copy(expr = expr1)
                }
                for (ix in adjustable2.occupiedIndices) {
                    val expression = expressions[ix] as ExprOutput.OneOf
                    expressions.expressions[ix] = expression.copy(expr = expr2)
                }
            }
            val halfSize = forwardAdjustables.size
            submode = Submode.ExprAdjustment(
                adjustables = forwardAdjustables + backwardAdjustables,
                arcPathAdjustables = arcPathAdjustables1 + arcPathAdjustables2.map { arcPathAdjustable ->
                    // we need to shift arc-path blueprint point indices, cuz adjustables are doubled
                    arcPathAdjustable.copy(
                        expr = arcPathAdjustable.expr.reIndex { it + halfSize }
                    )
                },
            )
            objectModel.invalidate()
        } else { // half-spiral
            submode = populateExprAdjustmentSubmode(objArg.indices) { ix ->
                Expr.LoxodromicMotion(parameters,
                    divergencePointIndex, convergencePointIndex, target = ix,
                )
            }
            objectModel.invalidate()
        }
    }

    context(viewModel: EditorViewModel)
    private inline fun <reified EXPR : Expr.Conformal.OneToMany> populateExprAdjustmentSubmode(
        inputIndices: List<Ix>,
        crossinline mkExpr: (gCircleIndex: Ix) -> EXPR,
    ): Submode.ExprAdjustment<EXPR> {
        val gCircleSources = inputIndices.filter {
            objects[it] is GCircle
        }.toMutableSet()
        val arcPathSources = inputIndices.filter {
            objects[it] is ConcreteArcPath && objectModel.getArcPath(it) is ArcPath
        }.toSet()
        arcPathSources.flatMapTo(gCircleSources) { arcPathSource ->
            val arcPath = viewModel.arcPathWithRealizedMidpoints(arcPathSource)
            arcPath.dependencies
        }
        val adjustables = mutableListOf<AdjustableExpr<EXPR>>()
        /** trajectories used to transfer regions */
        val source2trajectory = mutableListOf<Pair<Ix, List<Ix>>>()
        for (sourceIndex in gCircleSources) {
            // row/trajectory - column/simul-slice order
            val expr = mkExpr(sourceIndex)
            val result = expressions.addMultiExpr(expr) // multi expr creates a whole trajectory at a time
            val trajectory = objectModel.addDownscaledObjects(result).toList()
            for (outputIndex in trajectory) {
                objectModel.copyStyle(sourceIndex, outputIndex)
            }
            adjustables.add(AdjustableExpr(expr, sourceIndex, trajectory, trajectory))
            source2trajectory.add(sourceIndex to trajectory)
        }
        val arcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
        for (arcPathSource in arcPathSources) {
            val arcPath = viewModel.arcPathWithRealizedMidpoints(arcPathSource)
            val arcPathAdjustable = copyArcPathToTrajectory(arcPathSource, arcPath, source2trajectory)
            arcPathAdjustables.add(arcPathAdjustable)
            source2trajectory.add(arcPathSource to arcPathAdjustable.occupiedIndices)
        }
        copySourceRegionsOntoTrajectories(source2trajectory)
        return Submode.ExprAdjustment(
            adjustables = adjustables,
            arcPathAdjustables = arcPathAdjustables,
        )
    }

    /**
     * @param[source2trajectory] assumption: indices here are exactly the same as in adjustables
     * @return (adjustable trajectory of copied arc-paths, point adjustables)
     */
    private fun copyArcPathToTrajectory(
        sourceArcPathIndex: Ix,
        sourceArcPath: ArcPath,
        source2trajectory: List<Pair<Ix, List<Ix>>>,
    ): AdjustableExpr<ArcPath> {
        /** trajectory stage index -> arc-path vertices on this stage */
        val trajectoryOfVertices = sourceArcPath.vertices.map { vertexIndex ->
            source2trajectory.first { it.first == vertexIndex }.second
        }.transpose()
        /** trajectory stage index -> arc-path arcs on this stage */
        val trajectoryOfArcs = sourceArcPath.arcs.map { arc ->
            // arcIndex -> trajectory of arcs
            when (arc) {
                is ArcPath.Arc.By3Points -> {
                    source2trajectory.first { it.first == arc.middlePointIndex }.second
                        .map { newIndex ->
                            ArcPath.Arc.By3Points(middlePointIndex = newIndex)
                        }
                }
                is ArcPath.Arc.By2Points ->
                    never("arc-path $sourceArcPath should have no 2-point arcs after realizeArcPathMidpoints")
            }
        }.transpose()
        // trajectory of arc-paths
        val arcPathTrajectory = trajectoryOfVertices.zip(trajectoryOfArcs) { nullableVertices, nullableArcs ->
            val vertices = nullableVertices.map { it as Ix }
            val arcs = nullableArcs.map { it as ArcPath.Arc }
            val concreteArcPath = expressions.addSoloExpr(
                sourceArcPath.copy(vertices = vertices, arcs = arcs)
            )
            val copiedArcPathIndex = objectModel.addDownscaledObject(concreteArcPath)
            objectModel.copyStyle(sourceArcPathIndex, copiedArcPathIndex)
            copiedArcPathIndex
        }
        return AdjustableExpr(
            sourceArcPath.copy( // blueprint arc-path
                vertices = sourceArcPath.vertices.map { vertexSourceIndex ->
                    source2trajectory.indexOfFirst { it.first == vertexSourceIndex }
                },
                arcs = sourceArcPath.arcs.map { arc ->
                    val middleSourceIndex = (arc as ArcPath.Arc.By3Points).middlePointIndex
                    ArcPath.Arc.By3Points(middlePointIndex =
                        source2trajectory.indexOfFirst { it.first == middleSourceIndex }
                    )
                }
            ),
            sourceArcPathIndex,
            arcPathTrajectory, arcPathTrajectory,
        )
    }

    /**
     * Copy `regions` from source indices onto trajectories specified
     * by [source2trajectory].
     * @param[source2trajectory] `[(original index ~ style source, [trajectory of indices of objects])]`,
     * note that original indices CAN repeat (tho its regions will be copied only once even for the repeats).
     * @return indices of copied regions within `regions`, flattened trajectory of regions
     */
    context(viewModel: EditorViewModel)
    private fun copySourceRegionsOntoTrajectories(
        source2trajectory: List<Pair<Ix, List<Ix>>>,
    ): List<Int> {
        val newRegionIndices = source2trajectory
            .map { (sourceIndex, trajectory) ->
                trajectory.map { outputIndex ->
                    sourceIndex to outputIndex
                } // Column<Row<(OG Ix, new Ix)?>>
            }.transpose()
            .flatMap { trajectoryStageSlice ->
                // Column<(OG Ix, new Ix)>
                val nonNullSlice = trajectoryStageSlice.filterNotNull()
                // for each stage in the trajectory we try to copy regions
                if (nonNullSlice.isNotEmpty()) {
                    viewModel.copyRegions(
                        oldIndices = nonNullSlice.map { it.first },
                        newIndices = nonNullSlice.map { it.second },
                        flipInAndOut = false,
                    )
                } else emptyList()
            }
        return newRegionIndices
    }

    private fun List<LogicalRegion>.withoutOccupiedRegions(
        sm: Submode.ExprAdjustment<*>,
    ): List<LogicalRegion> {
        val constraints = mutableSetOf<Ix>()
        sm.adjustables.flatMapTo(constraints) { it.occupiedIndices }
        sm.arcPathAdjustables.flatMapTo(constraints) { it.occupiedIndices }
        return filterNot { region ->
            constraints.containsAll(region.constraints)
        }
    }

    // FIX: problems when shortening
    // TODO: last stage calc & fill for arc-paths trajectories with deleted interim stages
    context(viewModel: EditorViewModel)
    fun startExprAdjustmentOfSelection() {
        val exprs = getAdjustable(viewModel.selection.indices)
        if (exprs.isEmpty()) {
            println("startExprAdjustmentOfSelection: nothing to adjust")
            return
        }
        val expr0 = exprs.first()
        val transformTargets = exprs.mapNotNull { (it as? TransformLike)?.target }
        val tool: Tool.MultiArg = expr2Tool(expr0)
        var args: List<Arg> = expr2Args(expr0, transformTargets)
        setParametersAsDefault(expr0.parameters, bidirectional = false)
        val adjustables = mutableListOf<AdjustableExpr<*>>()
        val arcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
        val lastStage: Int = exprs.mapNotNull { expr ->
            expressions.findExpr(expr as Expr.Conformal).maxOfOrNull { ix ->
                (expressions[ix] as? ExprOutput.OneOf)?.outputIndex ?: 0
            }
        }.maxOrNull() ?: 0
        if (expr0 is TransformLike) {
            val complementaryAdjustables = mutableListOf<AdjustableExpr<Expr.LoxodromicMotion>>()
            for (expr in exprs) {
                addExprAsAdjustable(
                    expr = expr,
                    lastStage = lastStage,
                    adjustables = adjustables,
                    complementaryAdjustables = complementaryAdjustables,
                )
            }
            if (complementaryAdjustables.isNotEmpty()) {
                viewModel.defaultLoxodromicMotionParameters = viewModel.defaultLoxodromicMotionParameters.copy(
                    bidirectional = true,
                )
            }
            adjustables.addAll(complementaryAdjustables)
            val complementaryArcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
            val protoArcPaths = mutableListOf<Ix>()
            for (protoArcPathIndex in objectModel.arcPathIndices) {
                // TODO: fill missing stages up until lastStage
                //  and include arc paths into last-stage calc
                tryAddingProtoArcPathAsAdjustable(
                    protoArcPathIndex = protoArcPathIndex,
                    expr0 = expr0,
                    transformTargets = transformTargets,
//                    lastStage = lastStage,
                    protoArcPaths = protoArcPaths,
                    adjustables = adjustables,
                    arcPathAdjustables = arcPathAdjustables,
                    complementaryArcPathAdjustables = complementaryArcPathAdjustables,
                )
            }
            arcPathAdjustables.addAll(complementaryArcPathAdjustables)
            args = args.updated(0) { arg0 ->
                Arg.Indices((arg0 as Arg.Indices).indices + protoArcPaths)
            }
        } else if (expr0 is Expr.Interpolation) {
            val sourceIndex = expr0.start
            val indices = expressions.findExpr(expr0 as? Expr.Conformal)
            val outputIndices = fillMissingStages(indices)
            adjustables.add(AdjustableExpr(
                expr0, sourceIndex, outputIndices, outputIndices
            ))
        }
        partialArgList = PartialArgList(tool.signature, tool.nonEqualityConditions, args)
        submode = Submode.ExprAdjustment(adjustables, arcPathAdjustables)
        viewModel.selection = Selection() // clear selection to hide selection HUD
        objectModel.invalidate()
//        println("args: $args")
//        println("submode: $submode")
    }

    /**
     * @param[indices] we test all of these, whether they originate from the same [Expr] or
     * from similar [TransformLike] exprs but with differing targets/preimages
     * @return if [indices] contain only similar adjustable exprs (possibly with their preimages),
     * return those adjustable exprs; empty list otherwise
     */
    fun getAdjustable(indices: List<Ix>): List<Adjustable> {
        if (indices.isEmpty())
            return emptyList()
        // NOTE: ideally it should be order-independent, but we can stumble
        //  upon adjustable preimage of others, which implies ambiguous intent
        val expr0 = indices
            .mapNotNull { ix ->
                when (val expr = objectModel.getExpr(ix)) {
                    is ArcPath -> objectModel.getExpr(expr.vertices.first())
                    else -> expr
                }
            }
            .firstOrNull { expr -> expr is Adjustable }
            ?: return emptyList()
        val adjustableExprs: MutableSet<Adjustable> = mutableSetOf()
        val ng: MutableSet<Ix> = mutableSetOf()
        when (expr0) {
            is TransformLike -> {
                for (ix in indices) {
                    when (val expr = objectModel.getExpr(ix)) {
                        is ArcPath -> {
                            val depExpressions = expr.dependencies.map { expressions[it] }
                            val good =
                                expr.arcs.all { it is ArcPath.Arc.By3Points } &&
                                depExpressions.first().let { e1 ->
                                    e1 is ExprOutput.OneOf &&
                                    e1.expr is TransformLike && e1.expr is Adjustable &&
                                    Expr.areCompatibleTransforms(expr0, e1.expr) &&
                                    depExpressions.all { e ->
                                        e is ExprOutput.OneOf &&
                                        e.outputIndex == e1.outputIndex &&
                                        e.expr is TransformLike && e.expr is Adjustable &&
                                        ExprOutput.areSameStageTransforms(e1, e)
                                    }
                                }
                            if (good) {
                                depExpressions.mapNotNullTo(adjustableExprs) {
                                    (it as? ExprOutput.OneOf)?.expr as? Adjustable
                                }
                            } else {
                                ng.addAll(expr.dependencies)
                            }
                        }
                        else -> if (
                            expr is TransformLike && expr is Adjustable &&
                            Expr.areCompatibleTransforms(expr0, expr)
                        ) {
                            adjustableExprs.add(expr)
                        } else {
                            ng.add(ix)
                        }
                    }
                }
            }
            is Expr.PointInterpolation, is Expr.CircleInterpolation -> {
                adjustableExprs.add(expr0)
                indices.filterTo(ng) { ix ->
                    objectModel.getExpr(ix) != expr0
                }
            }
            else -> {
                ng.addAll(indices)
            }
        }
        val ngsAreArgs = ng.all { ix ->
            adjustableExprs.any { ix in it.args }
        }
        return if (ngsAreArgs && adjustableExprs.isNotEmpty())
            adjustableExprs.toList()
        else emptyList()
    }

    private fun expr2Tool(expr: Adjustable): Tool.MultiArg =
        when (expr) {
            is Expr.CircleInterpolation -> Tool.CircleOrPointInterpolation
            is Expr.PointInterpolation -> Tool.CircleOrPointInterpolation
            is Expr.Rotation -> Tool.Rotation
            is Expr.BiInversion -> Tool.BiInversion
            is Expr.LoxodromicMotion -> Tool.LoxodromicMotion
        }

    /**
     * @param[transformTargets] if [expr] is [TransformLike],
     * we use it as a skeleton with targets from [transformTargets]
     */
    private fun expr2Args(
        expr: Adjustable,
        transformTargets: List<Ix> =
            if (expr is TransformLike)
                listOf(expr.target)
            else emptyList(),
    ): List<Arg> =
        when (expr) {
            is Expr.CircleInterpolation -> listOf(
                Arg.IndexOf(expr.startCircle, objects[expr.startCircle] as GCircle),
                Arg.IndexOf(expr.endCircle, objects[expr.endCircle] as GCircle),
            )
            is Expr.PointInterpolation -> listOf(
                Arg.PointIndex(expr.startPoint),
                Arg.PointIndex(expr.endPoint),
            )
            is Expr.Rotation -> listOf(
                Arg.Indices(transformTargets),
                Arg.PointIndex(expr.pivot),
            )
            is Expr.BiInversion -> listOf(
                Arg.Indices(transformTargets),
                Arg.IndexOf(expr.engine1, objects[expr.engine1] as GCircle),
                Arg.IndexOf(expr.engine2, objects[expr.engine2] as GCircle),
            )
            is Expr.LoxodromicMotion -> listOf(
                Arg.Indices(transformTargets),
                Arg.PointIndex(expr.divergencePoint),
                Arg.PointIndex(expr.convergencePoint),
            )
        }

    context(viewModel: EditorViewModel)
    private fun setParametersAsDefault(
        parameters: Parameters?,
        bidirectional: Boolean = viewModel.defaultLoxodromicMotionParameters.bidirectional
    ) {
        when (parameters) {
            is InterpolationParameters ->
                viewModel.defaultInterpolationParameters = DefaultInterpolationParameters(parameters)
            is RotationParameters ->
                viewModel.defaultRotationParameters = DefaultRotationParameters(parameters)
            is BiInversionParameters ->
                viewModel.defaultBiInversionParameters = DefaultBiInversionParameters(parameters)
            is LoxodromicMotionParameters ->
                // bidirectionality might be overridden further down
                viewModel.defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(
                    parameters,
                    bidirectional = bidirectional
                )
            else -> {}
        }
    }

    /**
     * @param[lastStage] of the resulting trajectory =
     * max outputIndex of ExprOutput.OneOf's
     */
    private fun addExprAsAdjustable(
        expr: Adjustable,
        lastStage: Int? = null,
        adjustables: MutableList<AdjustableExpr<*>>,
        complementaryAdjustables: MutableList<AdjustableExpr<Expr.LoxodromicMotion>>,
    ) {
        val sourceIndex = (expr as TransformLike).target
        val indices = expressions.findExpr(expr as Expr.Conformal)
        val outputIndices = fillMissingStages(indices, lastStage)
        adjustables.add(AdjustableExpr(
            expr, sourceIndex, outputIndices, outputIndices
        ))
        if (expr is Expr.LoxodromicMotion && expr.otherHalfStart != null) {
            val complementaryExpr = objectModel.getExpr(expr.otherHalfStart)
            if (complementaryExpr is Expr.LoxodromicMotion) {
                val complementaryIndices = expressions.findExpr(complementaryExpr)
                val complementaryOutputIndices = fillMissingStages(complementaryIndices, lastStage)
                complementaryAdjustables.add(AdjustableExpr(
                    complementaryExpr,
                    sourceIndex,
                    complementaryOutputIndices, complementaryOutputIndices
                ))
            }
        }
    }

    /**
     * For arc path @[protoArcPathIndex]: if it's made of [transformTargets] try finding its
     * transform trajectory, then add it to [protoArcPaths] and to [arcPathAdjustables] as
     * a blueprint with the trajectory. For loxodromic spiral additionally add its other half.
     * @param[lastStage] of the resulting trajectory =
     * max outputIndex of ExprOutput.OneOf's
     */
    private fun tryAddingProtoArcPathAsAdjustable(
        protoArcPathIndex: Ix,
        expr0: TransformLike,
        transformTargets: List<Ix>,
        lastStage: Int? = null,
        adjustables: List<AdjustableExpr<*>>,
        protoArcPaths: MutableList<Ix>,
        arcPathAdjustables: MutableList<AdjustableExpr<ArcPath>>,
        complementaryArcPathAdjustables: MutableList<AdjustableExpr<ArcPath>>,
    ) {
        val protoArcPath = objectModel.getArcPath(protoArcPathIndex) ?: return
        val protoMidpoints: List<Ix> = getArcPathMidpoints(protoArcPathIndex) ?: return
        if (transformTargets.containsAll(protoArcPath.vertices) &&
            transformTargets.containsAll(protoMidpoints)
        ) {
            // TODO: fill missing stages up until lastStage
            val ix2e0: List<Pair<Ix, ExprOutput.OneOf>> = findTransformTrajectoryOfArcPath(protoArcPathIndex)
            if (ix2e0.isNotEmpty()) {
                protoArcPaths.add(protoArcPathIndex)
                val blueprintArcPath = protoArcPath.copy(
                    vertices = protoArcPath.vertices.map { vertexIndex ->
                        adjustables.indexOfFirst { it.sourceIndex == vertexIndex }
                    },
                    arcs = protoMidpoints.map { midpointIndex ->
                        val i = adjustables.indexOfFirst { it.sourceIndex == midpointIndex }
                        ArcPath.Arc.By3Points(i)
                    },
                )
                val expr00 = ix2e0[0].second.expr
                val isBipartite =
                    expr00 is Expr.LoxodromicMotion && expr00.otherHalfStart != null
                if (isBipartite) {
                    val forward = mutableListOf<Pair<Ix, ExprOutput.OneOf>>()
                    val backward = mutableListOf<Pair<Ix, ExprOutput.OneOf>>()
                    val firstProtoVertex = protoArcPath.vertices.first()
                    for ((ix, e0) in ix2e0) {
                        if (e0.expr == expr0.changeTarget(firstProtoVertex)) {
                            forward.add(ix to e0)
                        } else {
                            backward.add(ix to e0)
                        }
                    }
                    forward.distinctBy { (_, e0) -> e0.outputIndex }
                    backward.distinctBy { (_, e0) -> e0.outputIndex }
                    forward.sortBy { (_, e0) -> e0.outputIndex }
                    backward.sortBy { (_, e0) -> e0.outputIndex }
                    val forwardTrajectory = forward.map { (ix, _) -> ix }
                    arcPathAdjustables.add(AdjustableExpr(
                        blueprintArcPath,
                        protoArcPathIndex,
                        forwardTrajectory, forwardTrajectory
                    ))
                    val backwardTrajectory = backward.map { (ix, _) -> ix }
                    val blueprintArcPath2 = protoArcPath.copy(
                        vertices = protoArcPath.vertices.map { vertexIndex ->
                            adjustables.indexOfLast { it.sourceIndex == vertexIndex }
                        },
                        arcs = protoMidpoints.map { midpointIndex ->
                            val i = adjustables.indexOfLast { it.sourceIndex == midpointIndex }
                            ArcPath.Arc.By3Points(i)
                        },
                    )
                    complementaryArcPathAdjustables.add(AdjustableExpr(
                        blueprintArcPath2,
                        protoArcPathIndex,
                        backwardTrajectory, backwardTrajectory
                    ))
                } else { // unipartite case
                    // NOTE: how some trajectory stage could have been deleted atp
                    val trajectory = ix2e0
                        .distinctBy { (_, e0) -> e0.outputIndex }
                        .sortedBy { (_, e0) -> e0.outputIndex }
                        .map { (ix, _) -> ix }
                    arcPathAdjustables.add(AdjustableExpr(
                        blueprintArcPath,
                        protoArcPathIndex,
                        trajectory, trajectory
                    ))
                }
            }
        }
    }

    /**
     * Finds all arc-paths that are transforms of the proto arc-path at [protoArcPathIndex]
     * @return their indices and [ExprOutput] of the 1st vertex (sorted by index)
     */
    private fun findTransformTrajectoryOfArcPath(
        protoArcPathIndex: Ix
    ): List<Pair<Ix, ExprOutput.OneOf>> {
        val protoArcPath = objectModel.getArcPath(protoArcPathIndex) ?: return emptyList()
        val protoMidpoints: List<Ix> = getArcPathMidpoints(protoArcPathIndex) ?: return emptyList()
        return objectModel.arcPathIndices.mapNotNull { arcPathIndex ->
            val arcPath = objectModel.getArcPath(arcPathIndex) ?: return@mapNotNull null
            val e0 = expressions[arcPath.vertices.first()]
            if (arcPath.vertices.size == protoArcPath.vertices.size &&
                arcPath.arcs.size == protoArcPath.arcs.size &&
                e0 is ExprOutput.OneOf &&
                e0.expr is TransformLike && e0.expr is Adjustable &&
                e0.expr.target == protoArcPath.vertices.first()
            ) {
                arcPath.vertices.zip(protoArcPath.vertices) { vertex, protoVertex ->
                    val e = expressions[vertex]
                    val isImage =
                        e is ExprOutput.OneOf &&
                        ExprOutput.areSameStageTransforms(e0, e) &&
                        e.expr is TransformLike &&
                        e.expr.target == protoVertex
                    if (!isImage)
                        return@mapNotNull null
                }
                arcPath.midpoints.zip(protoMidpoints) { midpoint, protoMidpoint ->
                    if (midpoint == null)
                        return@mapNotNull null
                    val e = expressions[midpoint]
                    val isImage =
                        e is ExprOutput.OneOf &&
                        ExprOutput.areSameStageTransforms(e0, e) &&
                        e.expr is TransformLike &&
                        e.expr.target == protoMidpoint
                    if (!isImage)
                        return@mapNotNull null
                }
                arcPathIndex to e0
            } else null
        }
    }

    @Suppress("IfThenToElvis")
    private fun getArcPathMidpoints(
        arcPathIndex: Ix
    ): List<Ix>? {
        val arcPath = objectModel.getArcPath(arcPathIndex) ?: return null
        return arcPath.midpoints.mapIndexed { arcIndex, midpointIndex ->
            if (midpointIndex == null) {
                expressions.findExpr(
                    Expr.ArcPathArcMidpoint(
                        ArcPathArcMidpointParameters(arcIndex),
                        arcPathIndex
                    )
                ).firstOrNull() ?: return null
            } else midpointIndex
        }
    }

    /**
     * Apply to the output of `expressions.findExpr()`
     * @param[indices] indices with `ExprOutput.OneOf` of the same `Expr`, can be in any order
     * and with repeating `outputIndex`
     * @param[lastStage] of the resulting trajectory, `null` means maxOf(outputIndex)
     * @return trajectory with no missing stages (OneOf.outputIndex); out of the duplicate
     * expressions @ [indices] the first one is chosen
     */
    private fun fillMissingStages(
        indices: List<Ix>,
        lastStage: Int? = null,
    ): List<Ix> {
        require(indices.isNotEmpty())
        val expr = objectModel.getExpr(indices[0])
        require(expr is Expr.Conformal.OneToMany)
        val trajectory = mutableListOf<Ix>()
        // ExprOutput.OneOf.outputIndex -> index within objects
        val i2index = indices.asReversed().associateBy { ix ->
            (expressions[ix] as ExprOutput.OneOf).outputIndex
        }
        val max = lastStage ?: when (expr) {
            is Expr.Interpolation -> expr.parameters.nInterjacents - 1
            else -> i2index.keys.maxOrNull() ?: 0
        }
        for (i in 0 .. max) {
            val index = i2index[i]
            val ix =
                if (index == null) {
                    val o = expressions.addMultiExpression(
                        ExprOutput.OneOf(expr, i)
                    ) as GCircle?
                    objectModel.addDownscaledObject(o)
//                    expressions.addFree()
//                    objectModel.addDisplayObject(null)
                } else {
                    index
                }
            trajectory.add(ix)
        }
        return trajectory
    }

    context(viewModel: EditorViewModel)
    fun updateLoxodromicBidirectionality(bidirectional: Boolean) {
        val sm = submode
        if (sm is Submode.ExprAdjustment<*>) {
            when (sm.parameters) {
                is LoxodromicMotionParameters -> {
                    viewModel.defaultLoxodromicMotionParameters = viewModel.defaultLoxodromicMotionParameters.copy(
                        bidirectional = bidirectional,
                    )
                    viewModel.regions = viewModel.regions.withoutOccupiedRegions(sm)
                    viewModel.deleteObjectsWithDependenciesColorsAndRegions(
                        indicesToDelete =
                            sm.adjustables.flatMap { it.occupiedIndices } +
                            sm.arcPathAdjustables.flatMap { it.occupiedIndices }
                        ,
                        animationInit = { null },
                    )
                    // NOTE: this leaves a LOT of unused nulls
                    setupLoxodromicSpiral(bidirectional)
                }
                else -> {}
            }
        }
    }

    // MAYBE: also adj regions constrained solely by interpolating circles
    /** When in [Submode.ExprAdjustment], changes [submode]'s [Expr]s' parameters to
     * [parameters] and updates corresponding [objects] */
    @Suppress("UNCHECKED_CAST")
    context(viewModel: EditorViewModel)
    fun adjustExprParameters(parameters: Parameters) {
        val sm = submode
        if (sm is Submode.ExprAdjustment<*> && parameters != sm.parameters) {
            submode = when (parameters) {
                is InterpolationParameters -> // single adjustable expr case
                    adjustInterpolationParameters(
                        sm as Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
                        parameters
                    )
                // multiple adjustable exprs
                is RotationParameters,
                is BiInversionParameters,
                is LoxodromicMotionParameters ->
                    adjustTransformationParameters(
                        sm as Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
                        parameters
                    )
                else -> sm
            }
            setParametersAsDefault(parameters) // upd defaults for dialogs
            // NOTE: nearly-continuous invalidations from slider, not ideal for recompositions
            objectModel.invalidate() // using invalidatePositions() leads to visual glitches
        }
    }

    private fun adjustInterpolationParameters(
        sm: Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
        parameters: InterpolationParameters,
    ): Submode.ExprAdjustment<Expr.Conformal.OneToMany> {
        val (expr, sourceIndex, occupiedIndices, reservedIndices) = sm.adjustables[0]
        val newExpr = expr.copyWithNewParameters(parameters)
        val (newIndices, newReservedIndices, newObjects, deleted, changed) = expressions.adjustMultiExpr(
            newExpr = newExpr,
            occupiedIndices = occupiedIndices,
            reservedIndices = reservedIndices,
        )
        objectModel.removeObjectsAt(deleted)
        for (ix in newReservedIndices) { // we have to cleanup abandoned but reserved indices
            if (ix < objects.size) {
                objectModel.removeObjectAt(ix)
            } else { // padding
                objectModel.addDownscaledObject(null)
            }
        }
        newIndices.zip(newObjects) { ix, o ->
            objectModel.setDownscaledObject(ix, o)
            objectModel.copyStyle(sourceIndex, ix)
        }
        objectModel.update(newIndices.toSet())
        objectModel.forceUpdate(changed)
        return Submode.ExprAdjustment(listOf(
            AdjustableExpr(newExpr, sourceIndex, newIndices, newReservedIndices)
        ))
    }

    context(viewModel: EditorViewModel)
    private fun adjustTransformationParameters(
        sm: Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
        parameters: Parameters,
    ): Submode.ExprAdjustment<Expr.Conformal.OneToMany> {
        viewModel.regions = viewModel.regions.withoutOccupiedRegions(sm)
        for (arcPathAdjustable in sm.arcPathAdjustables) {
            objectModel.removeObjectsAt(arcPathAdjustable.occupiedIndices)
        }
        val newAdjustables = mutableListOf<AdjustableExpr<Expr.Conformal.OneToMany>>()
        /** object trajectories used to transfer regions */
        val source2trajectory1 = mutableListOf<Pair<Ix, List<Ix>>>()
        for ((expr, sourceIndex, occupiedIndices, reservedIndices) in sm.adjustables) {
            val newExpr = expr.copyWithNewParameters(parameters)
            val (newIndices, newReservedIndices, newObjects, deleted, changed) = expressions.adjustMultiExpr(
                newExpr = newExpr,
                occupiedIndices = occupiedIndices,
                reservedIndices = reservedIndices,
            )
            // NOTE: reserved indices will be generally non-contiguous
            // we have to cleanup abandoned indices
            val abandonedIndices = occupiedIndices.toSet() - newIndices.toSet()
            objectModel.removeObjectsAt(abandonedIndices + deleted)
            for (ix in newReservedIndices) {
                if (ix >= objects.size) { // pad with nulls
                    objectModel.addDownscaledObject(null)
                }
            }
            for (i in newIndices.indices) {
                val ix = newIndices[i]
                objectModel.setDownscaledObject(ix, newObjects[i])
                objectModel.copyStyle(sourceIndex, ix)
            }
            newAdjustables.add(AdjustableExpr(newExpr,
                sourceIndex,
                newIndices, newReservedIndices
            ))
            source2trajectory1.add(sourceIndex to newIndices)
            objectModel.update(newIndices.toSet())
            objectModel.forceUpdate(changed)
        }
        val newTrajectorySize = newAdjustables.first().size
        /** arc-path trajectories used to transfer regions */
        val source2trajectory2 = mutableListOf<Pair<Ix, List<Ix>>>()
        // NOTE: children of the source arc-path are handled properly still, they become
        //  dependent on source children, not on children of the trajectory arc-paths
        val newArcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
        for ((arcPathBlueprint, sourceArcPathIndex, occupiedIndices, reservedIndices) in sm.arcPathAdjustables) {
            val newArcPaths = List(newTrajectorySize) { trajectoryStage ->
                arcPathBlueprint.reIndex { adjustableIndex ->
                    newAdjustables[adjustableIndex].occupiedIndices[trajectoryStage]
                }
            }
            val (newIndices, newReservedIndices, newObjects, deleted, changed) =
                expressions.adjustArcPathBlueprint(newArcPaths,
                    occupiedIndices, reservedIndices
                )
            val abandonedIndices = occupiedIndices.toSet() - newIndices.toSet()
            objectModel.removeObjectsAt(abandonedIndices + deleted)
            for (ix in newReservedIndices) {
                if (ix >= objects.size) { // pad with nulls
                    objectModel.addDownscaledObject(null)
                }
            }
            newIndices.zip(newObjects) { ix, concreteArcPath ->
                objectModel.setDownscaledObject(ix, concreteArcPath)
                objectModel.copyStyle(sourceArcPathIndex, ix)
            }
            newArcPathAdjustables.add(AdjustableExpr(arcPathBlueprint,
                sourceArcPathIndex,
                newIndices, newReservedIndices
            ))
            source2trajectory2.add(sourceArcPathIndex to newIndices)
            objectModel.update(newIndices.toSet())
            objectModel.forceUpdate(changed)
        }
        val source2trajectory: List<Pair<Ix, List<Ix>>> = if (
            parameters is LoxodromicMotionParameters &&
            viewModel.defaultLoxodromicMotionParameters.bidirectional &&
            source2trajectory1.size.mod(2) == 0 &&
            source2trajectory2.size.mod(2) == 0
        ) {
            // NOTE: assumption: bidirectional spiral adjustables must be laid out as {t^i}; {t^-i}
            // s2t structure is
            // t1^+1 .. t1^+n; t2^+1 .. t2^+n; ... tm^+1 .. tm^+n;
            // t1^-1 .. t1^-n; t2^-1 .. t2^-n; ... tm^-1 .. tm^-n;
            // or alternatively,
            // adjustables = [[forward trajectories], [backward trajectories]]
            val halfSize1 = source2trajectory1.size.div(2)
            val halfSize2 = source2trajectory2.size.div(2)
            //  we have to do this to copy regions properly both forward and backward
            val forwardSource2trajectory =
                source2trajectory1.take(halfSize1) + source2trajectory2.take(halfSize2)
            val backwardSource2trajectory =
                source2trajectory1.drop(halfSize1) + source2trajectory2.drop(halfSize2)
            val source2fullTrajectory = forwardSource2trajectory.zip(
                backwardSource2trajectory
            ) { (sourceIndex1, forwardTrajectory), (sourceIndex2, backwardTrajectory) ->
                require(sourceIndex1 == sourceIndex2)
                // the order of indices within full trajectory doesn't matter,
                // only that it is consistent across all of them
                sourceIndex1 to (backwardTrajectory + forwardTrajectory)
            }
            source2fullTrajectory
        } else {
            source2trajectory1 + source2trajectory2
        }
        copySourceRegionsOntoTrajectories(source2trajectory)
        return Submode.ExprAdjustment(
            adjustables = newAdjustables,
            arcPathAdjustables = newArcPathAdjustables,
        )
//            .also { println("submode = $it") }
    }

    // completes tool modes with adjustable parameters
    context(viewModel: EditorViewModel)
    fun confirmAdjustedParameters() {
        partialArgList = if (viewModel.mode is ToolMode) {
            partialArgList?.copyEmpty()
        } else { // when adjusting in drag/multiselect
            null
        }
        when (val sm = submode) {
            is Submode.ExprAdjustment<*> ->
                setParametersAsDefault(sm.parameters)
            else -> {}
        }
        submode = null
        viewModel.recordHistory()
    }

    context(viewModel: EditorViewModel)
    fun cancelExprAdjustment() {
        when (val sm = submode) {
            is Submode.ExprAdjustment<*> -> {
                val outputs =
                    sm.adjustables.flatMap { it.occupiedIndices } +
                    sm.arcPathAdjustables.flatMap { it.occupiedIndices }
                viewModel.deleteObjectsWithDependenciesColorsAndRegions(
                    outputs,
                    animationInit = { null },
                )
            }
            else -> {}
        }
        submode = null
    }

}
