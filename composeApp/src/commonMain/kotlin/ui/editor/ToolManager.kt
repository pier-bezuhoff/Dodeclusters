package ui.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteAcPath
import domain.Ix
import domain.expressions.ConformalExpressions
import domain.expressions.Expr
import domain.model.Arg
import domain.model.ConformalObjectModel
import domain.model.PartialArcPath
import domain.model.PartialArgList
import domain.model.Selection
import kotlinx.coroutines.flow.MutableSharedFlow
import ui.editor.dialogs.DefaultBiInversionParameters
import ui.editor.dialogs.DefaultExtrapolationParameters
import ui.editor.dialogs.DefaultInterpolationParameters
import ui.editor.dialogs.DefaultLoxodromicMotionParameters
import ui.editor.dialogs.DefaultRotationParameters
import ui.tools.Tool
import kotlin.collections.plusAssign

class ToolManager(
    val objectModel: ConformalObjectModel,
    modeState: MutableState<Mode>,
    submodeState: MutableState<SubMode?>,
    selectionState: MutableState<Selection>,
    private val snackbarMessages: MutableSharedFlow<Pair<SnackbarMessage, Array<out Any>>>,
) {
    private var selection: Selection by selectionState

    private val mode: Mode by modeState
    private var submode: SubMode? by submodeState

    private val objects: List<GCircleOrConcreteAcPath?> = objectModel.displayObjects
    inline val expressions: ConformalExpressions get() =
        objectModel.expressions

    // NOTE: Arg.XYPoint & co use absolute positioning
    /** Partly filled [Tool] arg-list during [ToolMode] */
    var partialArgList: PartialArgList? by mutableStateOf(null)
        private set
    /** Under-construction arc-path during [ToolMode.ARC_PATH] */
    var partialArcPath: PartialArcPath? by mutableStateOf(null)
        private set

    // ahh.. to be set during startCircleOrPointInterpolationParameterAdjustment()
    var interpolateCircles: Boolean by mutableStateOf(true)
        private set
    var circlesAreCoDirected: Boolean by mutableStateOf(true)
        private set

    var defaultInterpolationParameters by mutableStateOf(DefaultInterpolationParameters())
        private set
    var defaultExtrapolationParameters by mutableStateOf(DefaultExtrapolationParameters())
        private set
    var defaultRotationParameters by mutableStateOf(DefaultRotationParameters())
        private set
    var defaultBiInversionParameters by mutableStateOf(DefaultBiInversionParameters())
        private set
    var defaultLoxodromicMotionParameters by mutableStateOf(DefaultLoxodromicMotionParameters())
        private set

    var showPromptToSetActiveSelectionAsToolArg: Boolean by mutableStateOf(false) // to be updated manually
        private set

    private fun clearSelection() {
        selection = Selection()
    }

    private fun exprOf(index: Ix): Expr? =
        expressions.expressions[index]?.expr

    fun startExprAdjustmentOfSelection() {
        // TODO: adjust expr of transformed arc-paths
        val firstSelected = selection.indices.firstOrNull() ?: return
        val expr = exprOf(firstSelected)
        val tool: Tool.MultiArg
        val sourceIndex: Ix
        val args: List<Arg>
        when (expr) {
            is Expr.CircleInterpolation -> {
                tool = Tool.CircleOrPointInterpolation
                sourceIndex = expr.startCircle
                args = listOf(
                    Arg.IndexOf(expr.startCircle, objects[expr.startCircle] as GCircle),
                    Arg.IndexOf(expr.endCircle, objects[expr.endCircle] as GCircle),
                )
                defaultInterpolationParameters = DefaultInterpolationParameters(expr.parameters)
            }
            is Expr.PointInterpolation -> {
                tool = Tool.CircleOrPointInterpolation
                sourceIndex = expr.startPoint
                args = listOf(Arg.PointIndex(expr.startPoint), Arg.PointIndex(expr.endPoint))
                defaultInterpolationParameters = DefaultInterpolationParameters(expr.parameters)
            }
            is Expr.Rotation -> {
                tool = Tool.Rotation
                sourceIndex = expr.target
                args = listOf(Arg.Indices(listOf(expr.target)), Arg.PointIndex(expr.pivot))
                defaultRotationParameters = DefaultRotationParameters(expr.parameters)
            }
            is Expr.BiInversion -> {
                tool = Tool.BiInversion
                sourceIndex = expr.target
                args = listOf(
                    Arg.Indices(listOf(expr.target)),
                    Arg.IndexOf(expr.engine1, objects[expr.engine1] as GCircle),
                    Arg.IndexOf(expr.engine2, objects[expr.engine2] as GCircle),
                )
                defaultBiInversionParameters = DefaultBiInversionParameters(expr.parameters)
            }
            is Expr.LoxodromicMotion -> {
                tool = Tool.LoxodromicMotion
                sourceIndex = expr.target
                args = listOf(
                    Arg.Indices(listOf(expr.target)),
                    Arg.PointIndex(expr.divergencePoint),
                    Arg.PointIndex(expr.convergencePoint),
                )
                // bidirectionality might be overridden further down
                defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(
                    expr.parameters,
                    bidirectional = false
                )
            }
            else -> return
        }
        val outputIndices = expressions.findExpr(expr)
        val adjustables = mutableListOf(AdjustableExpr(
            expr, sourceIndex, outputIndices, outputIndices
        ))
        if (expr is Expr.LoxodromicMotion && expr.otherHalfStart != null) {
            val complementaryExpr = exprOf(expr.otherHalfStart)
            if (complementaryExpr is Expr.LoxodromicMotion) {
                val complementaryOutputIndices = expressions.findExpr(complementaryExpr)
                adjustables += listOf(AdjustableExpr(
                    complementaryExpr,
                    sourceIndex,
                    complementaryOutputIndices, complementaryOutputIndices
                ))
                defaultLoxodromicMotionParameters = DefaultLoxodromicMotionParameters(
                    expr.parameters,
                    bidirectional = true,
                )
            }
        }
        partialArgList = PartialArgList(tool.signature, tool.nonEqualityConditions, args)
        submode = SubMode.ExprAdjustment(adjustables)
        clearSelection() // clear selection to hide selection HUD
    }

}