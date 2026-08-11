package ui.editor

import ui.editor.dialogs.DialogType

/**
 * @param[toolbarState] encapsulates all category- and tool-related info
 */
data class UiState(
    val toolbarState: ToolbarState = ToolbarState(),
    val showPanel: Boolean = toolbarState.panelNeedsToBeShown,
    val showUI: Boolean = true,
    val openedDialog: DialogType? = null,
)
