package ui.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import domain.Ix
import domain.hug
import domain.model.ConformalObjectModel
import domain.model.Selection

class SelectionManager(
    private val objectModel: ConformalObjectModel,
    selectionState: MutableState<Selection>,
) {
    var selection: Selection by selectionState
    val selectedIndices: List<Ix> get() =
        selection.gCircles.plus(
            selection.arcPaths.flatMap {
                objectModel.getArcPath(it)?.dependencies ?: emptySet()
            }
        ).distinct()

    fun isSelectionLocked(): Boolean {
        hug(objectModel.invalidations)
        return selectedIndices.all { ix ->
            objectModel.displayObjects[ix] == null ||
            objectModel.expressions[ix] != null
        }
    }

    fun clear() {
        selection = Selection()
    }

    // add actions like duplicate/delete/etc

}