package ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

interface KeyboardActionMapping<E> {
    fun event2action(event: E): KeyboardAction?

    // NOTE: buggy in browser: https://github.com/JetBrains/compose-multiplatform/issues/4673
    //  i had to recreate separate js-based handler (see wasmJs/BrowserKeyboardActionMapping.kt)
    object Default : KeyboardActionMapping<KeyEvent> {
        override fun event2action(event: KeyEvent): KeyboardAction? =
            if (event.type == KeyEventType.Companion.KeyDown && !event.isAltPressed && !event.isMetaPressed) {
                // Q: is Meta the Win key or macos's ctrl/command (or both as with js)?
                if (event.isCtrlPressed) {
                    when (event.key) {
                        Key.V -> KeyboardAction.PASTE
                        Key.A -> KeyboardAction.SELECT_ALL
                        Key.Plus, Key.Companion.Equals -> KeyboardAction.ZOOM_IN
                        Key.Minus -> KeyboardAction.ZOOM_OUT
                        Key.Z -> KeyboardAction.UNDO
                        Key.Y -> KeyboardAction.REDO
                        // O/S/N don't use Ctrl- modifier in the web version
                        Key.O -> KeyboardAction.OPEN
                        Key.S -> KeyboardAction.SAVE
                        Key.N -> KeyboardAction.NEW_DOCUMENT
                        else -> null
                    }
                } else {
                    when (event.key) {
                        Key.Delete, Key.Companion.Backspace -> KeyboardAction.DELETE
                        Key.Enter -> KeyboardAction.CONFIRM
                        Key.Escape -> KeyboardAction.CANCEL
                        Key.M -> KeyboardAction.MOVE
                        Key.L -> KeyboardAction.SELECT
                        Key.R -> KeyboardAction.REGION
                        Key.P -> KeyboardAction.PALETTE
                        Key.T -> KeyboardAction.TRANSFORM
                        Key.C -> KeyboardAction.CREATE
                        Key.Paste -> KeyboardAction.PASTE
                        Key.ZoomIn -> KeyboardAction.ZOOM_IN
                        Key.ZoomOut -> KeyboardAction.ZOOM_OUT
                        Key.Slash -> { // '?'
                            if (event.isShiftPressed)
                                KeyboardAction.HELP
                            else null
                        }
                        else -> null
                    }
                }
            } else null

        fun keyEventHandler(
            onAction: (KeyboardAction) -> Unit,
        ): (KeyEvent) -> Boolean = { event ->
            val keyboardAction = event2action(event)
            if (keyboardAction != null) {
                onAction(keyboardAction)
                // NOTE: idk, presently stuff like Delete is caught top-level and is not passed down to
                //  text fields (on Desktop), which is unacceptable [if we return `true` here].
                //  It used to work tho.
                false
    //            true
            } else false
        }
    }
}