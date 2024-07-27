package ui.edit_cluster


// TODO: create tests
/**
 * c_i := [pastCommands][[i]], s_i := [past][[i]],
 *
 * s_k+1 := current state (saveState())
 *
 * c_k+i := futureCommands[i-1], s_k+i := future[i-2]
 *
 * command c_i modifies previous state s_i into a new state s_i+1
 * */
//   c0  c1  c2 ...  c_k |       | c_k+1     c_k+2     c_k+3
// s0  s1  s2 ... s_k    | s_k+1 |      s_k+2     s_k+3
// ^ undo's = past       |  ^^^current state  \  ^^ redo's = future
class History<S>(
    val saveState: () -> S,
    val loadState: (S) -> Unit,
) {
    // tagged & grouped gap buffer with 'attention'/recency
    private val pastCommands = ArrayDeque<Command>(HISTORY_SIZE)
    private val recentPastCommands = ArrayDeque<Command>(RECENT_HISTORY_SIZE)
    private val futureCommands = ArrayDeque<Command>(HISTORY_SIZE)
    // we group history by commands and record it only when the new command differs from the previous one
    // NOTE: history doesn't survive background app kill
    // past -> recentPast -> now -> future
    private val past = ArrayDeque<S>(HISTORY_SIZE)
    private val recentPast = ArrayDeque<S>(RECENT_HISTORY_SIZE)
    private val future = ArrayDeque<S>(HISTORY_SIZE + RECENT_HISTORY_SIZE)
    val undoIsEnabled: Boolean get() =
        recentPast.isNotEmpty()
    val redoIsEnabled: Boolean get() =
        future.isNotEmpty()

    // past           | recentPast | now | future
    // og p1 p2       | rp1 rp2    | n   | f1 f2 f3
    // after record:
    // og ?p1 p2 ?rp1 | rp2 n      | _   |              |
    /** Use BEFORE modifying the state by the [command]!
     * let s_i := history[[i]], c_i := commands[[i]]
     * s0 (aka original) -> c0 -> s1 -> c1 -> s2 ... */
    fun recordCommand(command: Command) {
        // og <- past <- recentPast <- now
        if (recentPast.size == RECENT_HISTORY_SIZE) {
            val noLongerRecentCommand = recentPastCommands.removeFirst()
            val noLongerRecent = recentPast.removeFirst()
            // conflation check between past and recentPast
            if (noLongerRecentCommand != pastCommands.lastOrNull()) {
                if (past.size == HISTORY_SIZE) {
                    past.removeAt(1) // preserve the original
                    pastCommands.removeFirst()
                }
                past.addLast(noLongerRecent)
                pastCommands.addLast(noLongerRecentCommand)
            }
        }
        recentPast.addLast(saveState())
        recentPastCommands.addLast(command)
        futureCommands.clear()
        future.clear()
    }

    // past     | recentPast | now | future
    // og p1 p2 | rp1 rp2    | n   | f1 f2 f3
    // after undo:
    //    og p1 | p2  rp1    | rp2 | n f1 f2 f3
    fun undo() {
        if (recentPast.isNotEmpty()) {
            val currentState = saveState()
            val previousState = recentPast.removeLast()
            // previousState -> previousCommand ^n -> currentState
            val previousCommand = recentPastCommands.removeLast()
            if (past.isNotEmpty()) {
                recentPast.addFirst(past.removeLast())
                recentPastCommands.addFirst(pastCommands.removeLast())
            }
            // NOTE: future cannot overflow since its size is the same as that of past
            futureCommands.addFirst(previousCommand)
            future.addFirst(currentState)
            loadState(previousState)
        }
    }

    // past          | recentPast | now  | future
    // og p1 p2      | rp1 rp2    | n    | f1 f2 f3
    // after redo:
    // og p1 p2 rp1  | rp2 n      | f1   | f2 f3
    fun redo() {
        if (future.isNotEmpty()) {
            val currentState = saveState()
            val nextCommand = futureCommands.removeFirst()
            val nextState = future.removeFirst()
            // NOTE: past cannot overflow since its size is less than that of future
            //  and conflation cannot happen at this stage too (structurally)
            if (recentPast.size == RECENT_HISTORY_SIZE) {
                val noLongerRecentCommand = recentPastCommands.removeFirst()
                val noLongerRecent = recentPast.removeFirst()
                pastCommands.addLast(noLongerRecentCommand)
                past.addLast(noLongerRecent)
            }
            recentPastCommands.addLast(nextCommand)
            recentPast.addLast(currentState)
            loadState(nextState)
        }

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
        past.clear()
        pastCommands.clear()
        recentPast.clear()
        recentPastCommands.clear()
        recentPastCommands.clear()
        future.clear()
        futureCommands.clear()
    }

    companion object {
        const val HISTORY_SIZE = 10
        const val RECENT_HISTORY_SIZE = 3
    }
}