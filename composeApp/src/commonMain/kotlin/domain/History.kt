package domain

// TODO: create tests
// MAYBE: reset lastCommandTag every VM.onUp
// s_i := [past][[i]],
// s_k+1 := current state (saveState())
// s_k+i := future[i-2]
// command c modifies previous state s_k into a new state s_k+1
//                     c |       | c_1
// s0  s1  s2 ... s_k    | s_k+1 |      s_k+2     s_k+3
// ^ undo's = past       |  ^^^current state  \  ^^ redo's = future
class History<S>(
    private val saveState: () -> S,
    private val loadState: (S) -> Unit,
    private val historySize: Int = 100,
) {
    // tagged & grouped gap buffer with 'attention'/recency
    private var lastCommand: Command? = null
    private var lastCommandTag: Command.Tag? = null
    // we group history by commands and record it only when the new command differs from the previous one
    // past -> now -> future
    private val past = ArrayDeque<S>(historySize)
    private val future = ArrayDeque<S>(historySize)
    val undoIsEnabled: Boolean get() =
        past.isNotEmpty()
    val redoIsEnabled: Boolean get() =
        future.isNotEmpty()

    // past         | now | future
    // og p1 p2     | n   | f1 f2 f3
    // og ?p1 p2 ?n | _   |              |
    /** Use BEFORE modifying the state by the [command]!
     * let s_i := history[[i]], c_i := commands[[i]]
     * s0 (aka original) -> c0 -> s1 -> c1 -> s2 ... */
    fun recordCommand(command: Command, tag: Command.Tag? = null) {
        if (lastCommand == null ||
            command != lastCommand ||
            tag != null && lastCommandTag != null && tag != lastCommandTag
        ) {
            if (past.size == historySize) {
                past.removeAt(1) // preserve the original @ 0
            }
            val state = saveState()
            past.addLast(state)
            lastCommand = command
            lastCommandTag = tag
        }
        future.clear()
    }

    // past     | now | future
    // og p1 p2 | n   | f1 f2 f3
    //    og p1 | p2  | n f1 f2 f3
    fun undo() {
        if (past.isNotEmpty()) {
            val currentState = saveState()
            val previousState = past.removeLast()
            // previousState -> previousCommand ^n -> currentState
            // NOTE: future cannot overflow since its size is the same as that of past
            future.addFirst(currentState)
            lastCommand = null
            lastCommandTag = null
            loadState(previousState)
        }
    }

    // past       | now | future
    // og p1 p2   | n   | f1 f2 f3
    // og p1 p2 n | f1  | f2 f3
    fun redo() {
        if (future.isNotEmpty()) {
            val currentState = saveState()
            val nextState = future.removeFirst()
            // NOTE: past cannot overflow since its size is less than that of future
            //  and conflation cannot happen at this stage too (structurally)
            past.addLast(currentState)
            lastCommand = null
            lastCommandTag = null
            loadState(nextState)
        }
    }

    fun clear() {
        past.clear()
        future.clear()
        lastCommand = null
        lastCommandTag = null
    }
}