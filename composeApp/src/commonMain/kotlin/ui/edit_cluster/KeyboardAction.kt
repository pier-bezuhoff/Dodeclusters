package ui.edit_cluster

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
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
    CANCEL
}

// NOTE: doesn't work in browser (yet?)
//  related? https://github.com/JetBrains/compose-multiplatform/issues/4673
fun Modifier.handleKeyboardActions(
    onAction: (KeyboardAction) -> Unit, // MAYBE: use Flow or smth instead of callback
): Modifier =
    onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyUp && !it.isAltPressed && !it.isMetaPressed) {
            when (it.key) {
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
                else -> if (it.isCtrlPressed) {
                    when (it.key) {
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