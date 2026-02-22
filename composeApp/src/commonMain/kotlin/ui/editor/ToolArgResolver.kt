package ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import domain.Arg
import domain.Ix
import domain.PartialArcPath
import domain.PartialArgList

// trying to extract tool-arg manip from VM
class ToolArgResolver(
    val modeState: State<Mode>,
    val submodeState: State<SubMode?>,
) {
    inline val toolMode: ToolMode? get() =
        modeState.value as? ToolMode
    inline val exprAdjustment: SubMode.ExprAdjustment<*>? get() =
        submodeState.value as? SubMode.ExprAdjustment<*>
    // NOTE: Arg.XYPoint & co use absolute positioning
    var partialArgList: PartialArgList? by mutableStateOf(null)
        private set
    var partialArcPath: PartialArcPath? by mutableStateOf(null)
        private set

    fun resetToolMode(toolMode: ToolMode) {
        partialArgList = PartialArgList(toolMode.signature, toolMode.nonEqualityConditions)
    }

    fun addIndicesArg(indices: List<Ix>) {
        partialArgList = partialArgList?.addArg(Arg.Indices(indices), confirmThisArg = true)
    }
}