package domain.model

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.Serializable

/** Indices of selected thingies */
@Immutable
@Serializable
data class Selection(
    val gCircles: List<Ix> = emptyList(),
    val arcPaths: List<Ix> = emptyList(),
    // MAYBE: separate CLI from points cuz it's used in context-action logic
) {
    val indices: List<Ix> get() = gCircles + arcPaths
    inline val size: Int get() = gCircles.size + arcPaths.size

    fun isEmpty(): Boolean =
        gCircles.isEmpty() && arcPaths.isEmpty()

    fun isNotEmpty(): Boolean =
        gCircles.isNotEmpty() || arcPaths.isNotEmpty()
}