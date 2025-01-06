package ui.edit_cluster

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import data.geometry.GCircle
import kotlinx.serialization.Serializable

// MAYBE: couple durations & colors with animations
// MAYBE: replace with enum
@Immutable
@Serializable
sealed interface ObjectAnimation {
    val objects: List<GCircle>

    data class Highlight(override val objects: List<GCircle>) : ObjectAnimation
}

/** params for create/copy/delete animations */
@Immutable
@Serializable
sealed interface CircleAnimation : ObjectAnimation {
    override val objects: List<CircleOrLine>

    /** Animation for creating new circles */
    data class Entrance(override val objects: List<CircleOrLine>) : CircleAnimation
    /** Animation for duplicating circles */
    data class ReEntrance(override val objects: List<CircleOrLine>) : CircleAnimation
    /** Animation for deleting circles */
    data class Exit(override val objects: List<CircleOrLine>) : CircleAnimation
}
