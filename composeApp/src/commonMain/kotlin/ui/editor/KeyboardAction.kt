package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class KeyboardAction {
    SELECT_ALL, // Ctrl-A
    DELETE,
    // + Ctrl-C: copy (what? we already have duplicate)
    PASTE, // Ctrl-V
    ZOOM_IN, ZOOM_OUT,
    UNDO, REDO, // Ctrl-Z, Ctrl-Y
    CONFIRM, // Enter
    /** Cancel ongoing action (partial constructions, etc) */
    CANCEL, // Esc
    MOVE, SELECT, REGION, // M: drag, L: multiselect, R: region
    PALETTE, // P
    TRANSFORM, CREATE, // T, C
    OPEN, // Ctrl-O | O in browser
    SAVE, // Ctrl-S | S in browser
    NEW_DOCUMENT, // Ctrl-N | N in browser
    HELP, // ? = Shift-/
    // TODO: `?` for shortcut cheatsheet / help
    // TODO: arrow keys for finer movement
}

fun Modifier.handleKeyboardActions(
    onAction: (KeyboardAction) -> Unit,
): Modifier {
    val keyEventHandler = KeyboardActionMapping.Default.keyEventHandler(onAction)
    return Modifier.onPreviewKeyEvent(keyEventHandler)
}