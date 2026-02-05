import org.w3c.dom.events.KeyboardEvent
import ui.editor.KeyboardAction
import ui.editor.KeyboardActionMapping

private val UNDER_MAC = WasmPlatform.underlyingPlatform == UnderlyingPlatform.MAC

object WebKeyboardActionMapping : KeyboardActionMapping<KeyboardEvent> {
    override fun event2action(event: KeyboardEvent): KeyboardAction? =
        if (!event.altKey && !event.shiftKey) {
            // meta key is apparently macos equiv of ctrl, BUT is also Win on Windows/Linux
            if (event.ctrlKey || UNDER_MAC && event.metaKey) {
                // using KeyEvent.code is language-invariant (not supported on mobile browsers)
                // reference: https://developer.mozilla.org/en-US/docs/Web/API/KeyboardEvent/code
                when (event.code) {
                    "KeyV" -> KeyboardAction.PASTE
                    "KeyA" -> KeyboardAction.SELECT_ALL
                    // these 2 don't work well with normal scaling
//                "Equal" -> KeyboardAction.ZOOM_IN
//                "Minus" -> KeyboardAction.ZOOM_OUT
                    "KeyZ" -> KeyboardAction.UNDO
                    "KeyY" -> KeyboardAction.REDO
                    // Ctrl-S, Ctrl-O, Ctrl-N are not available in browser
                    // so we use plain O/S/N
                    else -> null
                }
            } else {
                when (event.code) {
                    "Delete", "Backspace" -> KeyboardAction.DELETE
                    "Enter" -> KeyboardAction.CONFIRM
                    "Escape" -> KeyboardAction.CANCEL
                    "KeyM" -> KeyboardAction.MOVE
                    "KeyL" -> KeyboardAction.SELECT
                    "KeyR" -> KeyboardAction.REGION
                    "KeyP" -> KeyboardAction.PALETTE
                    "KeyT" -> KeyboardAction.TRANSFORM
                    "KeyC" -> KeyboardAction.CREATE
                    // instead of Ctrl-<X> versions
                    "KeyO" -> KeyboardAction.OPEN
                    "KeyS" -> KeyboardAction.SAVE
                    "KeyN" -> KeyboardAction.NEW_DOCUMENT
                    else -> null
                }
            }
        } else if (event.code == "Slash" && event.shiftKey)
            KeyboardAction.HELP
        else null
}