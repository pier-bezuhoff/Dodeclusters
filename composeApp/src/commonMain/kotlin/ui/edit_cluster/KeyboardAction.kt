package ui.edit_cluster

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

// MAYBE: add shortcut help on ?
enum class KeyboardAction {
    SELECT_ALL,
    DELETE,
    // + Ctrl-C: copy (what? we already have duplicate)
    // + Enter: finish/confirm cluster creation & go to multi-cluster editor
    PASTE,
    ZOOM_IN, ZOOM_OUT,
    UNDO, REDO,
//    SAVE, OPEN,
    /** Cancel ongoing action (partial constructions, etc) */
    CANCEL,
    // MAYBE: mode shortcuts: D/M = drag, S = multiselect, R = region, V = visibility, P = palette, T = transform, C = create
    // TODO: arrow keys for finer movement
}

/** Iffy in browser, since the target composable often randomly loses focus */
fun Modifier.handleKeyboardActions(
    onAction: (KeyboardAction) -> Unit,
): Modifier {
    val callback = keyboardActionsHandler(onAction)
    return Modifier.onPreviewKeyEvent(callback)
}

// NOTE: buggy in browser: https://github.com/JetBrains/compose-multiplatform/issues/4673
fun keyboardActionsHandler(
    onAction: (KeyboardAction) -> Unit,
): (KeyEvent) -> Boolean = { event ->
    if (event.type == KeyEventType.KeyUp && !event.isAltPressed && !event.isMetaPressed) {
        when (event.key) {
            Key.Delete, Key.Backspace -> {
                onAction(KeyboardAction.DELETE)
                true
            }
            Key.Paste -> {
                onAction(KeyboardAction.PASTE)
                true
            }
            Key.ZoomIn -> {
                onAction(KeyboardAction.ZOOM_IN)
                true
            }
            Key.ZoomOut -> {
                onAction(KeyboardAction.ZOOM_OUT)
                true
            }
            Key.Escape -> {
                onAction(KeyboardAction.CANCEL)
                true
            }
            else -> if (event.isCtrlPressed) {
                when (event.key) {
                    Key.V -> {
                        onAction(KeyboardAction.PASTE)
                        true
                    }
                    Key.A -> {
                        onAction(KeyboardAction.SELECT_ALL)
                        true
                    }
                    Key.Plus, Key.Equals -> {
                        onAction(KeyboardAction.ZOOM_IN)
                        true
                    }
                    Key.Minus -> {
                        onAction(KeyboardAction.ZOOM_OUT)
                        true
                    }
                    Key.Z -> {
                        onAction(KeyboardAction.UNDO)
                        true
                    }
                    Key.Y -> {
                        onAction(KeyboardAction.REDO)
                        true
                    }
//                        Key.S -> {
//                            onAction(KeyboardAction.SAVE)
//                            true
//                        }
//                        Key.O -> {
//                            onAction(KeyboardAction.OPEN)
//                            true
//                        }
                    else -> false
                }
            } else false
        }
    } else false
}