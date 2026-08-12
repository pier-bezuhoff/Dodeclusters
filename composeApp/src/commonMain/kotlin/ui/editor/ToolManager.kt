package ui.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteArcPath
import domain.Ix
import domain.expressions.ArcPath
import domain.expressions.ArcPathArcMidpointParameters
import domain.expressions.ConformalExpressions
import domain.expressions.Expr
import domain.expressions.ExprOutput
import domain.expressions.areCompatibleTransforms
import domain.model.Arg
import domain.model.ConformalObjectModel
import domain.model.PartialArcPath
import domain.model.PartialArgList
import domain.model.Selection
import domain.model.Styling
import kotlinx.coroutines.flow.MutableSharedFlow
import ui.editor.dialogs.DefaultBiInversionParameters
import ui.editor.dialogs.DefaultExtrapolationParameters
import ui.editor.dialogs.DefaultInterpolationParameters
import ui.editor.dialogs.DefaultLoxodromicMotionParameters
import ui.editor.dialogs.DefaultRotationParameters
import ui.tools.Tool

@Stable
class ToolManager(
    val objectModel: ConformalObjectModel,
    modeState: MutableState<Mode>,
    submodeState: MutableState<Submode?>,
    selectionState: MutableState<Selection>,
) {
    val mode: Mode by modeState
    var submode: Submode? by submodeState
    var selection: Selection by selectionState
    /** Distinct selected [GCircle]? indices +
     * indices of all vertices/midpoints of selected arc-paths */
    val selectedIndices: List<Ix> get() =
        selection.gCircles.plus(
            selection.arcPaths.flatMap {
                objectModel.getArcPath(it)?.dependencies ?: emptySet()
            }
        ).distinct()

    val objects: List<GCircleOrConcreteArcPath?> = objectModel.displayObjects
    val styling: Map<Ix, Styling> = objectModel.styling
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

    private fun exprOf(index: Ix): Expr? =
        expressions.expressions[index]?.expr

    private fun clearSelection() {
        selection = Selection()
    }

    fun resetToolMode(toolMode: ToolMode) {
        partialArgList = PartialArgList(toolMode.signature, toolMode.nonEqualityConditions)
    }

    fun addIndicesArg(indices: List<Ix>) {
        partialArgList = partialArgList?.addArg(Arg.Indices(indices), confirmThisArg = true)
    }

}