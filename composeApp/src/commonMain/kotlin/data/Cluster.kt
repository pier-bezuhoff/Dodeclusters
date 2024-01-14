package data

import androidx.compose.ui.graphics.Color

data class Cluster(
    val circles: List<Circle>,
    /** union of part comprised of circle intersections */
    val parts: List<Part>,
    /** fill regions inside / wireframe */
    val fill: Boolean,
    val fillColor: Color,
    val borderColor: Color,
) {
    /** intersection of insides and outside of circles of a cluster */
    data class Part(
        /** indices of interior circles */
        val insides: Set<Int>,
        /** indices of bounding complementary circles */
        val outsides: Set<Int>
    )
}