package data

import androidx.compose.ui.geometry.Offset

data class Circle(
    val x: Double,
    val y: Double,
    val radius: Double,
) {
    val offset: Offset
        get() = Offset(x.toFloat(), y.toFloat())

    constructor(center: Offset, radius: Double) :
        this(center.x.toDouble(), center.y.toDouble(), radius)
}

data class DirectedCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
    /** Circle direction, inside/outside ~ counterclockwise/clockwise */
    val inside: Boolean,
) {
}
