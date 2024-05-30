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

enum class KeyboardShortcuts {
    SELECT_ALL,
    DELETE,
    // + Ctrl-C
    PASTE,
    ZOOM_IN, ZOOM_OUT,
    UNDO, REDO,
    SAVE, OPEN,
    CANCEL
}

fun Modifier.handleKeyboardActions(
    onAction: (KeyboardShortcuts) -> Unit,
): Modifier =
    onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyUp && !it.isAltPressed && !it.isMetaPressed) {
            when (it.key) {
                Key.Escape -> {
                    onAction(KeyboardShortcuts.CANCEL)
                    true
                }
                Key.Delete, Key.Backspace -> {
                    onAction(KeyboardShortcuts.DELETE)
                    true
                }
                Key.Paste -> {
                    onAction(KeyboardShortcuts.PASTE)
                    true
                }
                Key.ZoomIn -> {
                    onAction(KeyboardShortcuts.ZOOM_IN)
                    true
                }
                Key.ZoomOut -> {
                    onAction(KeyboardShortcuts.ZOOM_OUT)
                    true
                }
                else -> if (it.isCtrlPressed) {
                    when (it.key) {
                        Key.V -> {
                            onAction(KeyboardShortcuts.PASTE)
                            true
                        }
                        Key.A -> {
                            onAction(KeyboardShortcuts.SELECT_ALL)
                            true
                        }
                        Key.Plus, Key.Equals -> {
                            onAction(KeyboardShortcuts.ZOOM_IN)
                            true
                        }
                        Key.Minus -> {
                            onAction(KeyboardShortcuts.ZOOM_OUT)
                            true
                        }
                        Key.Z -> {
                            onAction(KeyboardShortcuts.UNDO)
                            true
                        }
                        Key.Y -> {
                            onAction(KeyboardShortcuts.REDO)
                            true
                        }
                        Key.S -> {
                            onAction(KeyboardShortcuts.SAVE)
                            true
                        }
                        Key.O -> {
                            onAction(KeyboardShortcuts.OPEN)
                            true
                        }
                        else -> false
                    }
                } else false
            }
        } else false
    }