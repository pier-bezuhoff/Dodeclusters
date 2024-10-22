package ui.edit_cluster

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import kotlinx.serialization.Serializable

/** params for create/copy/delete animations */
@Serializable
@Immutable
sealed interface CircleAnimation {
    val circles: List<CircleOrLine>

    /** Animation for creating new circles */
    data class Entrance(override val circles: List<CircleOrLine>) : CircleAnimation
    /** Animation for duplicating circles */
    data class ReEntrance(override val circles: List<CircleOrLine>) : CircleAnimation
    /** Animation for deleting circles */
    data class Exit(override val circles: List<CircleOrLine>) : CircleAnimation
}