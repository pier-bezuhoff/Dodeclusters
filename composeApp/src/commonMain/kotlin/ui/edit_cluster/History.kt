package ui.edit_cluster


// TODO: do not conflate last 5 commands for convenience

/**
 * c_i := [pastCommands][[i]], s_i := [past][[i]],
 *
 * s_k+1 := current state (saveState())
 *
 * c_k+i := futureCommands[i-1], s_k+i := future[i-2]
 *
 * command c_i modifies previous state s_i into a new state s_i+1
 *
 * ..c0  c1  c2 ...  c_k |       | c_k+1     c_k+2     c_k+3
 *
 * s0  s1  s2 ... s_k    | s_k+1 |      s_k+2     s_k+3
 *
 * ^ undo's = past       |  ^^^current state  \  ^^ redo's = future
 * */
class History<S>(
    val saveState: () -> S,
    val loadState: (S) -> Unit,
) {
    // tagged & grouped gap buffer
    private val pastCommands = ArrayDeque<Command>(HISTORY_SIZE)
    private val futureCommands = ArrayDeque<Command>(HISTORY_SIZE)
    // we group history by commands and record it only when the new command differs from the previous one
    // NOTE: history doesn't survive background app kill
    private val past = ArrayDeque<S>(HISTORY_SIZE)
    private val future = ArrayDeque<S>(HISTORY_SIZE)
    val undoIsEnabled: Boolean get() =
        past.isNotEmpty()
    val redoIsEnabled: Boolean get() =
        future.isNotEmpty()

    /** Use BEFORE modifying the state by the [command]!
     * let s_i := history[[i]], c_i := commands[[i]]
     * s0 (aka original) -> c0 -> s1 -> c1 -> s2 ... */
    fun recordCommand(command: Command) {
        if (pastCommands.lastOrNull() != command) {
            if (past.size == EditClusterViewModel.HISTORY_SIZE) {
                past.removeAt(1) // save th original
                pastCommands.removeFirst()
            }
            past.addLast(saveState()) // saving state before [command]
            pastCommands.addLast(command)
        }
        futureCommands.clear()
        future.clear()
    }

    fun undo() {
        if (past.isNotEmpty()) {
            val currentState = saveState()
            val previousState = past.removeLast()
            val previousCommand = pastCommands.removeLast() // previousState -> previousCommand ^n -> currentState
            loadState(previousState)
            futureCommands.addFirst(previousCommand)
            future.addFirst(currentState)
        }
    }

    fun redo() {
        if (future.isNotEmpty()) {
            val currentState = saveState()
            val nextCommand = futureCommands.removeFirst()
            val nextState = future.removeFirst()
            loadState(nextState)
            pastCommands.addLast(nextCommand)
            past.addLast(currentState)
        }
    }

    fun clear() {
        future.clear()
        futureCommands.clear()
        past.clear()
        pastCommands.clear()
    }

    companion object {
        const val HISTORY_SIZE = 100
        const val RECENT_HISTORY_SIZE = 5
    }
}