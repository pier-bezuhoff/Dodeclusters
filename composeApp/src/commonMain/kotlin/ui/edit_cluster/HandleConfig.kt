package ui.edit_cluster

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.Serializable

/** ixs = indices of circles to which the handle is attached */
@Serializable
@Immutable
sealed class HandleConfig(open val ixs: List<Ix>) {
    data class SingleCircle(val ix: Ix): HandleConfig(listOf(ix))
    data class SeveralCircles(override val ixs: List<Ix>): HandleConfig(ixs)
}