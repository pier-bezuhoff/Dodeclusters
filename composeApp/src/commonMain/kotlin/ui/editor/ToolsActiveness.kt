package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import domain.model.ChessboardPattern
import ui.tools.Tool

// i want this to supplant toolPredicate
/**
 */
@Immutable
data class ToolsActiveness(
    val activeTool: Tool,
    val everythingIsSelected: Boolean,
    val chessboardPattern: ChessboardPattern,
    val restrictRegionToSelection: Boolean,
    val showCircles: Boolean,
    val showPhantoms: Boolean,
    val showDirectionArrows: Boolean,
) {
    @Stable
    fun isOn(tool: Tool): Boolean =
        when (tool) {
            Tool.Drag, Tool.Multiselect, Tool.RectangularSelect, Tool.FlowSelect,
            Tool.RegionFill, Tool.FlowFill,
            Tool.StereographicRotation,
            is Tool.MultiArg ->
                tool == activeTool
            Tool.ToggleSelectAll -> everythingIsSelected
            Tool.FillChessboardPattern -> chessboardPattern != ChessboardPattern.NONE
            Tool.RestrictRegionToSelection -> restrictRegionToSelection
            Tool.ToggleObjects -> showCircles
            Tool.TogglePhantoms -> showPhantoms
            Tool.ToggleDirectionArrows -> showDirectionArrows
            else -> false
        }

    @Stable
    fun isAlternativeOn(tool: Tool): Boolean =
        when (tool) {
            Tool.FillChessboardPattern ->
                chessboardPattern == ChessboardPattern.STARTS_TRANSPARENT
            else -> false
        }

    companion object {
        val MOCK = ToolsActiveness(
            activeTool = Tool.Drag,
            everythingIsSelected = false,
            chessboardPattern = ChessboardPattern.NONE,
            restrictRegionToSelection = false,
            showCircles = true,
            showPhantoms = false,
            showDirectionArrows = false,
        )
    }
}
