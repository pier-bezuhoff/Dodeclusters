package data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.io.Ddc
import kotlinx.serialization.Serializable
import ui.edit_cluster.Ix

@Serializable
@Immutable
data class Cluster(
    val circles: List<CircleOrLine>,
    /** union of parts comprised of circle intersections */
    val parts: List<Part> = emptyList(),
    /** fill regions inside / wireframe */
    val filled: Boolean = Ddc.DEFAULT_CLUSTER_FILLED,
) {
    /** intersection of insides and outside of circles of a cluster */
    @Serializable
    @Immutable
    data class Part(
        /** indices of interior circles */
        val insides: Set<Int>,
        /** indices of bounding complementary circles */
        val outsides: Set<Int>,
        @Serializable(ColorCssSerializer::class)
        val fillColor: Color = Ddc.DEFAULT_CLUSTER_FILL_COLOR,
        @Serializable(ColorCssSerializer::class)
        val borderColor: Color? = Ddc.DEFAULT_CLUSTER_BORDER_COLOR,
    ) {
        override fun toString(): String =
            "Cluster.Part(in = [${insides.joinToString()}],\nout = [${outsides.joinToString()}],\ncolor = $fillColor)"

        /** ruff semiorder ⊆ on delimited regions; only goes off indices */
        infix fun isObviouslyInside(otherPart: Part): Boolean =
            // the more intersections the smaller the delimited region is
            insides.containsAll(otherPart.insides) &&
            outsides.containsAll(otherPart.outsides)
    }

    companion object {
        val SAMPLE = Cluster(
            circles = listOf(Circle(200.0, 100.0, 50.0)),
            parts = emptyList(),
            filled = true
        )
    }
}

fun compressPartToEssentials(
    ins: List<CircleOrLine>,
    outs: List<CircleOrLine>,
    pointInside: Offset
): Pair<List<Ix>, List<Ix>> {
    // compute all intersections with circles they are on
    // filter half-in's
    // filter arcs between them
    // arc => full circle is essential
    TODO()
}