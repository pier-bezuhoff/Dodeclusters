package data

data class Circle(
    val x: Double,
    val y: Double,
    val radius: Double,
//    /** Circle direction, inside/outside ~ counterclockwise/clockwise */
//    val inside: Boolean,
) {
}

data class DirectedCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
    /** Circle direction, inside/outside ~ counterclockwise/clockwise */
    val inside: Boolean,
) {
}
