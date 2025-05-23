package data.geometry

import androidx.compose.runtime.Immutable

// ideally [directed] Circle should not inherit from UndirectedCircle, but it should be
// separate data class
@Immutable
sealed interface UndirectedCircle : CircleOrLine {
    val x: Double
    val y: Double
    val radius: Double

    /** @return angle in degrees `[-180°; 180°]` measured from East up to the [point] along
     * `this` circle counterclockwise (irrespective of its direction) */
    fun point2angle(point: Point): Float
}