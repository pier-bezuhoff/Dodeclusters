package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.serialization.Serializable

// MAYBE: add shortcut help on ?
@Immutable
@Serializable
enum class KeyboardAction {
    SELECT_ALL, // Ctrl-A
    DELETE,
    // + Ctrl-C: copy (what? we already have duplicate)
    // + Enter: finish/confirm cluster creation & go to multi-cluster editor
    PASTE, // Ctrl-V
    ZOOM_IN, ZOOM_OUT,
    UNDO, REDO,
    // SAVE, OPEN,
    CONFIRM, // Enter
    /** Cancel ongoing action (partial constructions, etc) */
    CANCEL, // Esc
    OPEN,
    MOVE, SELECT, REGION, // M: drag, S: multiselect, R: region
    PALETTE, // P
    TRANSFORM, CREATE, // T, C
    HELP,
    // TODO: `?` for shortcut cheatsheet / help
    // TODO: arrow keys for finer movement
}

fun Modifier.handleKeyboardActions(
    onAction: (KeyboardAction) -> Unit,
): Modifier {
    val callback = keyboardActionsHandler(onAction)
    return Modifier.onPreviewKeyEvent(callback)
}

// corresponds to keyboardEventTranslator from js/main.kt
fun keyEventTranslator(event: KeyEvent): KeyboardAction? =
    if (event.type == KeyEventType.KeyDown && !event.isAltPressed && !event.isMetaPressed) {
        // Q: is Meta the Win key or macos's ctrl?
        if (event.isCtrlPressed) {
            when (event.key) {
                Key.V -> KeyboardAction.PASTE
                Key.A -> KeyboardAction.SELECT_ALL
                Key.Plus, Key.Equals -> KeyboardAction.ZOOM_IN
                Key.Minus -> KeyboardAction.ZOOM_OUT
                Key.Z -> KeyboardAction.UNDO
                Key.Y -> KeyboardAction.REDO
                else -> null
            }
        } else {
            when (event.key) {
                Key.Delete, Key.Backspace -> KeyboardAction.DELETE
                Key.Enter -> KeyboardAction.CONFIRM
                Key.Escape -> KeyboardAction.CANCEL
                Key.O -> KeyboardAction.OPEN
                Key.M -> KeyboardAction.MOVE
                Key.S -> KeyboardAction.SELECT
                Key.R -> KeyboardAction.REGION
                Key.P -> KeyboardAction.PALETTE
                Key.T -> KeyboardAction.TRANSFORM
                Key.C -> KeyboardAction.CREATE
                Key.Paste -> KeyboardAction.PASTE
                Key.ZoomIn -> KeyboardAction.ZOOM_IN
                Key.ZoomOut -> KeyboardAction.ZOOM_OUT
                Key.Slash -> // '?'
                    if (event.isShiftPressed)
                        KeyboardAction.HELP
                    else null
                else -> null
            }
        }
    } else null

// NOTE: buggy in browser: https://github.com/JetBrains/compose-multiplatform/issues/4673
//  i had to recreate separate js-based handler (see wasmJs/main.kt)
fun keyboardActionsHandler(
    onAction: (KeyboardAction) -> Unit,
): (KeyEvent) -> Boolean = { event ->
    val keyboardAction = keyEventTranslator(event)
    if (keyboardAction != null) {
        onAction(keyboardAction)
        true
    } else false
}