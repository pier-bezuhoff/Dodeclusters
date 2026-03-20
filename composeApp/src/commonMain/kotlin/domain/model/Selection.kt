package domain.model

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.Serializable

/** Indices of selected thingies */
@Immutable
@Serializable
data class Selection(
    val objects: List<Ix> = emptyList(),
    val arcPaths: List<Int> = emptyList(),
) {
    val size: Int get() = objects.size + arcPaths.size

    fun isEmpty(): Boolean =
        objects.isEmpty() && arcPaths.isEmpty()

    fun isNotEmpty(): Boolean =
        objects.isNotEmpty() || arcPaths.isNotEmpty()
}