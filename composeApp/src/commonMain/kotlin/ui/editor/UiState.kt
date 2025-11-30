package ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import domain.Ix
import domain.PartialArcPath
import domain.PartialArgList
import domain.settings.BlendModeType
import domain.settings.ChessboardPattern
import ui.editor.EditorViewModel.Companion.DEFAULT_SHOW_DIRECTION_ARROWS_ON_SELECTED_CIRCLES
import ui.editor.dialogs.DialogType
import ui.theme.DodeclustersColors

// split into diff categories: ToolState, RegionState, Colors etc
data class UiState(
    // objectModel
    // regions
    // expressions
    // objectLabels

    /** currently selected color */
    val regionColor: Color = DodeclustersColors.deepAmethyst,
    /** `[0; 1]` transparency of non-chessboard regions */
    val regionsOpacity: Float = 1f,
    val regionsBlendModeType: BlendModeType = BlendModeType.SRC_OVER,
    val backgroundColor: Color? = null,

    val chessboardPattern: ChessboardPattern = ChessboardPattern.NONE,
    val chessboardColor: Color = regionColor,

    // tool state
    /** indices of selected circles/lines/points */
    val selection: List<Ix> = emptyList(),
    val mode: Mode = SelectionMode.Drag,
    val subMode: SubMode? = null,
    val partialArgList: PartialArgList? = null,
    val partialArcPath: PartialArcPath? = null,

    /** encapsulates all category- and tool-related info */
    val toolbarState: ToolbarState = ToolbarState(),
    val showPanel: Boolean = toolbarState.panelNeedsToBeShown,
    val showPromptToSetActiveSelectionAsToolArg: Boolean = false,
    val showUI: Boolean = true,
    val openedDialog: DialogType? = null,

    val showCircles: Boolean = true,
    val showPhantomObjects: Boolean = false,
    /** which style to use when drawing regions: true = stroke, false = fill */
    val showDirectionArrows: Boolean = DEFAULT_SHOW_DIRECTION_ARROWS_ON_SELECTED_CIRCLES,

    val regionManipulationStrategy: RegionManipulationStrategy = RegionManipulationStrategy.REPLACE,

    /** applies to [SelectionMode.Region]:
     * only use circles present in the [selection] to determine which regions to fill */
    val restrictRegionsToSelection: Boolean = false,
)
