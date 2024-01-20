package data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.minus
import kotlinx.serialization.Serializable

@Serializable
data class Circle(
    val x: Double,
    val y: Double,
    val radius: Double,
) {
    val offset: Offset
        get() = Offset(x.toFloat(), y.toFloat())

    constructor(center: Offset, radius: Double) :
        this(center.x.toDouble(), center.y.toDouble(), radius)

    /** semiorder ⊆ on circles' insides */
    infix fun isInside(otherCircle: Circle): Boolean =
        (offset - otherCircle.offset).getDistance() + radius <= otherCircle.radius
}

@Serializable
data class DirectedCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
    /** Circle direction, inside/outside ~ counterclockwise/clockwise */
    val inside: Boolean,
) {
}
