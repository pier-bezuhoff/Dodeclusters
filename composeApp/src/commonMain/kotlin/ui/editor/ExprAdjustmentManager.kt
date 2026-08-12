package ui.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import core.geometry.CircleOrLineOrImaginaryCircle
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteArcPath
import core.geometry.ImaginaryCircle
import core.geometry.Point
import core.geometry.conformal.GeneralizedCircle
import domain.Ix
import domain.expressions.ArcPath
import domain.expressions.ArcPathArcMidpointParameters
import domain.expressions.BiInversionParameters
import domain.expressions.ConformalExpressions
import domain.expressions.Expr
import domain.expressions.Expr.TransformLike
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
import domain.model.PartialArgList
import domain.model.Selection
import domain.never
import domain.transpose
import domain.updated
import domain.withoutElementsAt
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

    private inline fun exprOf(index: Ix): Expr.Conformal? =
        expressions[index]?.expr as? Expr.Conformal

    context(viewModel: EditorViewModel)
    fun startCircleOrPointInterpolationParameterAdjustment() {
        val argList = partialArgList ?: return
        val (startArg, endArg) = argList.args.map { it as Arg.CLIP }
        if (startArg is Arg.CLI && endArg is Arg.CLI) {
            viewModel.interpolateCircles = true
            val scalarProduct =
                GeneralizedCircle.fromGCircle(objects[startArg.index] as CircleOrLineOrImaginaryCircle) scalarProduct
                GeneralizedCircle.fromGCircle(objects[endArg.index] as CircleOrLineOrImaginaryCircle)
            viewModel.circlesAreCoDirected = scalarProduct >= 0.0
            val expr = Expr.CircleInterpolation(
                viewModel.defaultInterpolationParameters.params.let {
                    it.copy(
                        complementary =
                            if (viewModel.circlesAreCoDirected) !it.inBetween
                            else it.inBetween
                    )
                },
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
            val (startPointIndex, endPointIndex) = listOf(startArg, endArg).map { pointArg ->
                when (pointArg) {
                    is Arg.PointIndex -> pointArg.index
                    is Arg.FixedPoint -> viewModel.createNewFreePoint(pointArg.toPoint())
                }
            }
            val expr = Expr.PointInterpolation(
                viewModel.defaultInterpolationParameters.params,
                startPointIndex, endPointIndex
            )
            viewModel.interpolateCircles = false
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
            val (adjustables1, arcPathAdjustables1, regions1) =
                populateExprAdjustmentSubmode(objArg.indices) { ix ->
                    Expr.LoxodromicMotion(parameters,
                        divergencePointIndex, convergencePointIndex,
                        target = ix,
                    )
                }
            val (adjustables2, arcPathAdjustables2, regions2) =
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
                regions = regions1 + regions2,
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
        val gCircleSources = inputIndices.filter { objects[it] is GCircle }
        val arcPathSources = inputIndices.filter { objects[it] is ConcreteArcPath }
        val adjustables = mutableListOf<AdjustableExpr<EXPR>>()
        /** trajectories used to transfer regions */
        val source2trajectory = mutableListOf<Pair<Ix, List<Ix>>>()
        for (sourceIndex in gCircleSources) {
            // row/trajectory - column/simul-slice order
            val expr = mkExpr(sourceIndex)
            val result = expressions.addMultiExpr(expr) // multi expr creates a whole trajectory at a time
            val outputIndices = objectModel.addDownscaledObjects(result).toList()
            for (outputIndex in outputIndices) {
                objectModel.copyStyle(sourceIndex, outputIndex)
            }
            adjustables.add(AdjustableExpr(expr, sourceIndex, outputIndices, outputIndices))
            source2trajectory.add(sourceIndex to outputIndices)
        }
        val arcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
        for (sourceArcPathIndex in arcPathSources) {
            val (arcPathAdjustable, arcPathPointsAdjustables) =
                copyArcPathToMany(sourceArcPathIndex, mkExpr)
            val startIndex = adjustables.size
            adjustables.addAll(arcPathPointsAdjustables)
            val blueprintArcPath = arcPathAdjustable.expr
            arcPathAdjustables.add(
                arcPathAdjustable.copy(
                    expr = blueprintArcPath.reIndex { startIndex + it },
                )
            )
            source2trajectory.add(sourceArcPathIndex to arcPathAdjustable.occupiedIndices)
        }
        val copiedRegions = copySourceRegionsOntoTrajectories(source2trajectory)
        return Submode.ExprAdjustment(
            adjustables = adjustables,
            arcPathAdjustables = arcPathAdjustables,
            regions = copiedRegions,
        )
    }

    /**
     * @return (adjustable trajectory of copied arc-paths, point adjustables)
     */
    context(viewModel: EditorViewModel)
    private inline fun <reified EXPR : Expr.Conformal.OneToMany> copyArcPathToMany(
        sourceArcPathIndex: Ix,
        crossinline mkExpr: (pointIndex: Ix) -> EXPR,
    ): Pair<AdjustableExpr<ArcPath>, List<AdjustableExpr<EXPR>>> {
        require(objectModel.getArcPath(sourceArcPathIndex) is ArcPath)
        val sourceArcPath = viewModel.realizeArcPathMidpoints(sourceArcPathIndex)
        val adjustables = mutableListOf<AdjustableExpr<EXPR>>()
        /** trajectory stage index -> arc-path vertices on this stage */
        val trajectoryOfVertices = sourceArcPath.vertices.map { vertexIndex ->
            // vertexIndex -> trajectory of vertices
            val expr = mkExpr(vertexIndex)
            val result = expressions.addMultiExpr(expr)
            val newIndices = objectModel.addDownscaledObjects(result).toList()
            for (newIndex in newIndices) {
                objectModel.copyStyle(vertexIndex, newIndex)
            }
            adjustables.add(AdjustableExpr(expr,
                vertexIndex,
                newIndices, newIndices
            ))
            newIndices
        }.transpose()
        /** trajectory stage index -> arc-path arcs on this stage */
        val trajectoryOfArcs = sourceArcPath.arcs.map { arc ->
            // arcIndex -> trajectory of arcs
            when (arc) {
                is ArcPath.Arc.By3Points -> {
                    val sourceIndex = arc.middlePointIndex
                    val expr = mkExpr(sourceIndex)
                    val result = expressions.addMultiExpr(expr)
                    val newIndices = objectModel.addDownscaledObjects(result).toList()
                    for (newIndex in newIndices) {
                        objectModel.copyStyle(sourceIndex, newIndex)
                    }
                    adjustables.add(AdjustableExpr(expr,
                        sourceIndex,
                        newIndices, newIndices
                    ))
                    newIndices.map { newIndex ->
                        ArcPath.Arc.By3Points(middlePointIndex = newIndex)
                    }
                }
                is ArcPath.Arc.By2Points ->
                    never("arc-path $sourceArcPath should have no 2-point arcs after realizeArcPathMidpoints")
            }
        }.transpose()
        // trajectory of arc-paths
        val copiedArcPathIndices = trajectoryOfVertices.zip(trajectoryOfArcs) { nullableVertices, nullableArcs ->
            val vertices = nullableVertices.map { it as Ix }
            val arcs = nullableArcs.map { it as ArcPath.Arc }
            val concreteArcPath = expressions.addSoloExpr(
                sourceArcPath.copy(vertices = vertices, arcs = arcs)
            )
            val copiedArcPathIndex = objectModel.addDownscaledObject(concreteArcPath)
            objectModel.copyStyle(sourceArcPathIndex, copiedArcPathIndex)
            copiedArcPathIndex
        }
        val arcPathAdjustable = AdjustableExpr(
            sourceArcPath.copy( // blueprint arc-path
                vertices = sourceArcPath.vertices.indices.toList(),
                arcs = List(sourceArcPath.arcs.size) { arcIndex ->
                    ArcPath.Arc.By3Points(sourceArcPath.vertices.size + arcIndex)
                }
            ),
            sourceArcPathIndex,
            copiedArcPathIndices, copiedArcPathIndices,
        )
        return Pair(arcPathAdjustable, adjustables)
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

    // TODO: add adjustable regions
    context(viewModel: EditorViewModel)
    fun startExprAdjustmentOfSelection() {
        val exprs = getAdjustableExprs()
        if (exprs.isEmpty())
            return
        val expr0 = exprs.first()
        val transformTargets = exprs.mapNotNull { (it as? TransformLike)?.target }
        val tool: Tool.MultiArg = exprAdjustable2Tool(expr0)
        var args: List<Arg> = getExprAdjustmentArgs(expr0, transformTargets)
        setParametersAsDefault(expr0.parameters, bidirectional = false)
        val adjustables = mutableListOf<AdjustableExpr<*>>()
        val arcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
        val adjustableRegions = mutableListOf<Int>()
        if (expr0 is TransformLike) {
            val complementaryAdjustables = mutableListOf<AdjustableExpr<Expr.LoxodromicMotion>>()
            for (expr in exprs) {
                val sourceIndex = (expr as TransformLike).target
                val outputIndices = expressions.findExpr(expr as Expr.Conformal)
                    .sortedBy { (expressions[it] as ExprOutput.OneOf).outputIndex }
                adjustables.add(AdjustableExpr(
                    expr, sourceIndex, outputIndices, outputIndices
                ))
                if (expr is Expr.LoxodromicMotion && expr.otherHalfStart != null) {
                    val complementaryExpr = exprOf(expr.otherHalfStart)
                    if (complementaryExpr is Expr.LoxodromicMotion) {
                        val complementaryOutputIndices = expressions.findExpr(complementaryExpr)
                            .sortedBy { (expressions[it] as ExprOutput.OneOf).outputIndex }
                        complementaryAdjustables.add(AdjustableExpr(
                            complementaryExpr,
                            sourceIndex,
                            complementaryOutputIndices, complementaryOutputIndices
                        ))
                        viewModel.defaultLoxodromicMotionParameters = viewModel.defaultLoxodromicMotionParameters.copy(
                            bidirectional = true,
                        )
                    }
                }
            }
            adjustables.addAll(complementaryAdjustables)
            val complementaryArcPathAdjustables = mutableListOf<AdjustableExpr<ArcPath>>()
            val protoArcPaths = mutableListOf<Ix>()
            loop@ for (protoArcPathIndex in objectModel.arcPathIndices) {
                val protoArcPath = objectModel.getArcPath(protoArcPathIndex) ?: continue
                val protoMidpoints: List<Ix> = protoArcPath.midpoints.mapIndexed { arcIndex, midpointIndex ->
                    if (midpointIndex == null) {
                        val gluedMidpoints = expressions.findExpr(
                            Expr.ArcPathArcMidpoint(
                                ArcPathArcMidpointParameters(arcIndex),
                                protoArcPathIndex
                            )
                        )
                        if (gluedMidpoints.isEmpty())
                            continue@loop
                        gluedMidpoints.first()
                    } else midpointIndex
                }
                if (transformTargets.containsAll(protoArcPath.vertices) &&
                    transformTargets.containsAll(protoMidpoints)
                ) {
                    val ix2e0 = findTransformTrajectoryOfArcPath(protoArcPathIndex)
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
                        val firstVertexExpr0 = ix2e0[0].second.expr
                        if (firstVertexExpr0 is Expr.LoxodromicMotion &&
                            firstVertexExpr0.otherHalfStart != null
                        ) {
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
                        } else {
                            val trajectory = ix2e0
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
            arcPathAdjustables.addAll(complementaryArcPathAdjustables)
            args = args.updated(0) { arg0 ->
                Arg.Indices((arg0 as Arg.Indices).indices + protoArcPaths)
            }
            // TODO: add adjustable regions
        } else {
            val sourceIndex = when (expr0) {
                is Expr.PointInterpolation -> expr0.startPoint
                is Expr.CircleInterpolation -> expr0.startCircle
            }
            val outputIndices = expressions.findExpr(expr0)
            adjustables.add(AdjustableExpr(
                expr0, sourceIndex, outputIndices, outputIndices
            ))
        }
        partialArgList = PartialArgList(tool.signature, tool.nonEqualityConditions, args)
        submode = Submode.ExprAdjustment(adjustables, arcPathAdjustables, adjustableRegions)
        viewModel.selection = Selection() // clear selection to hide selection HUD
        println("args: $args")
        println("submode: $submode")
    }

    /**
     * @param[indices] we test all of these, whether they originate from the same [Expr] or
     * from similar [TransformLike] exprs but with differing targets/preimages
     * @return preimages
     */
    context(viewModel: EditorViewModel)
    fun getAdjustableExprs(
        indices: List<Ix> = viewModel.selection.indices,
    ): List<Expr.Adjustable> {
        val exprs: MutableSet<Expr.Adjustable> = mutableSetOf()
        if (indices.isEmpty())
            return emptyList()
        val expr0 = when (val expr = exprOf(indices.first())) {
            is ArcPath -> exprOf(expr.vertices.first())
            else -> expr
        }
        if (expr0 !is Expr.Adjustable)
            return emptyList()
        val areAdjustable = when (expr0) {
            is TransformLike -> {
                indices.all { ix ->
                    when (val expr = exprOf(ix)) {
                        is ArcPath -> {
                            expr.arcs.all { it is ArcPath.Arc.By3Points } &&
                            expr.dependencies.let { deps ->
                                val e1 = expressions[deps.first()] as? ExprOutput.OneOf
                                val expr1 = e1?.expr
                                val outputIndex = e1?.outputIndex
                                expr1 is TransformLike &&
                                Expr.areCompatibleTransforms(expr0, expr1) &&
                                deps.all {
                                    val e = expressions[it] as? ExprOutput.OneOf
                                        ?: return@all false
                                    if (e.outputIndex != outputIndex)
                                        return@all false
                                    val depExpr = e.expr as? TransformLike
                                        ?: return@all false
                                    exprs.add(depExpr as Expr.Adjustable)
                                    ExprOutput.areSameStageTransforms(e1, e)
                                }
                            }
                        }
                        else -> if (expr is TransformLike) {
                            exprs.add(expr as Expr.Adjustable)
                            Expr.areCompatibleTransforms(expr0, expr)
                        } else false
                    }
                }
            }
            is Expr.PointInterpolation, is Expr.CircleInterpolation -> {
                // point or circle interpolations
                exprs.add(expr0 as Expr.Adjustable)
                indices.all { ix ->
                    val expr = exprOf(ix)
                    expr == expr0
                }
            }
            else -> false
        }
        return if (areAdjustable) exprs.toList() else emptyList()
    }

    private fun exprAdjustable2Tool(expr: Expr.Adjustable): Tool.MultiArg =
        when (expr) {
            is Expr.CircleInterpolation -> Tool.CircleOrPointInterpolation
            is Expr.PointInterpolation -> Tool.CircleOrPointInterpolation
            is Expr.Rotation -> Tool.Rotation
            is Expr.BiInversion -> Tool.BiInversion
            is Expr.LoxodromicMotion -> Tool.LoxodromicMotion
        }

    private fun getExprAdjustmentArgs(
        expr0: Expr.Adjustable,
        transformTargets: List<Ix>,
    ): List<Arg> =
        when (expr0) {
            is Expr.CircleInterpolation -> listOf(
                Arg.IndexOf(expr0.startCircle, objects[expr0.startCircle] as GCircle),
                Arg.IndexOf(expr0.endCircle, objects[expr0.endCircle] as GCircle),
            )
            is Expr.PointInterpolation -> listOf(
                Arg.PointIndex(expr0.startPoint),
                Arg.PointIndex(expr0.endPoint),
            )
            is Expr.Rotation -> listOf(
                Arg.Indices(transformTargets),
                Arg.PointIndex(expr0.pivot),
            )
            is Expr.BiInversion -> listOf(
                Arg.Indices(transformTargets),
                Arg.IndexOf(expr0.engine1, objects[expr0.engine1] as GCircle),
                Arg.IndexOf(expr0.engine2, objects[expr0.engine2] as GCircle),
            )
            is Expr.LoxodromicMotion -> listOf(
                Arg.Indices(transformTargets),
                Arg.PointIndex(expr0.divergencePoint),
                Arg.PointIndex(expr0.convergencePoint),
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

    /** finds all arc-path that are transforms of the proto arc-path at [protoArcPathIndex],
     * returning their index and [ExprOutput] of the 1st vertex (sorted by index) */
    private fun findTransformTrajectoryOfArcPath(
        protoArcPathIndex: Ix
    ): List<Pair<Ix, ExprOutput.OneOf>> {
        val protoArcPath = objectModel.getArcPath(protoArcPathIndex) ?: return emptyList()
        val protoMidpoints: List<Ix> = protoArcPath.midpoints.mapIndexed { arcIndex, midpointIndex ->
            if (midpointIndex == null) {
                val gluedMidpoints = expressions.findExpr(
                    Expr.ArcPathArcMidpoint(
                        ArcPathArcMidpointParameters(arcIndex),
                        protoArcPathIndex
                    )
                )
                if (gluedMidpoints.isEmpty())
                    return emptyList()
                gluedMidpoints.first()
            } else midpointIndex
        }
        return objectModel.arcPathIndices.mapNotNull { arcPathIndex ->
            val arcPath = objectModel.getArcPath(arcPathIndex) ?: return@mapNotNull null
            val e0 = expressions[arcPath.vertices.first()]
            val expr0 = e0?.expr
            if (arcPath.vertices.size == protoArcPath.vertices.size &&
                arcPath.arcs.size == protoArcPath.arcs.size &&
                e0 is ExprOutput.OneOf &&
                expr0 is TransformLike && expr0 is Expr.Adjustable &&
                expr0.target == protoArcPath.vertices.first()
            ) {
                arcPath.vertices.zip(protoArcPath.vertices) { vertex, protoVertex ->
                    val e = expressions[vertex]
                    val expr = e?.expr
                    val isImage =
                        e is ExprOutput.OneOf &&
                        ExprOutput.areSameStageTransforms(e0, e) &&
                        expr is TransformLike &&
                        expr.target == protoVertex
                    if (!isImage)
                        return@mapNotNull null
                }
                arcPath.midpoints.zip(protoMidpoints) { midpoint, protoMidpoint ->
                    if (midpoint == null)
                        return@mapNotNull null
                    val e = expressions[midpoint]
                    val expr = e?.expr
                    val isImage =
                        e is ExprOutput.OneOf &&
                        ExprOutput.areSameStageTransforms(e0, e) &&
                        expr is TransformLike &&
                        expr.target == protoMidpoint
                    if (!isImage)
                        return@mapNotNull null
                }
                arcPathIndex to e0
            } else null
        }
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
                    viewModel.regions = viewModel.regions.withoutElementsAt(sm.regions.toSet())
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
            setParametersAsDefault(parameters) // upd defaults for dialog, not sure it's sensible
            // NOTE: nearly-continuous invalidations from slider, not ideal for recompositions
            objectModel.invalidate()
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

    // FIX: problems when shortening pre-existing traj
    context(viewModel: EditorViewModel)
    private fun adjustTransformationParameters(
        sm: Submode.ExprAdjustment<Expr.Conformal.OneToMany>,
        parameters: Parameters,
    ): Submode.ExprAdjustment<Expr.Conformal.OneToMany> {
        viewModel.regions = viewModel.regions.withoutElementsAt(sm.regions.toSet())
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
        val affectedRegions: List<Int> = copySourceRegionsOntoTrajectories(source2trajectory)
        return Submode.ExprAdjustment(
            adjustables = newAdjustables,
            arcPathAdjustables = newArcPathAdjustables,
            regions = affectedRegions,
        )
            .also { println("submode = $it") }
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
