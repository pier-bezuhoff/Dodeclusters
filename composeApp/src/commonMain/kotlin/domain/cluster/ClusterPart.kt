package domain.cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import domain.ColorCssSerializer
import domain.io.Ddc
import kotlinx.serialization.Serializable

// TODO: replace parts Cluster.Part (which can be disjunctive regions) with
//  arc-paths, based on ordered lists of intersection points and directed circles
// MAYBE: we can alternatively use 1 BooleanArray[circles.size] to specify part bounds
//  out of the circles, and another BooleanArray[insides.size + outsides.size] to specify
//  which are in and which are out
/** Intersection of insides and outside of circles of a cluster
 * @param[insides] Indices of circles inside which we are
 * @param[outsides] Indices of circles outside of which we are
 * */
@Serializable
@Immutable
data class ClusterPart(
    /** indices of interior circles */
    val insides: Set<Int>,
    /** indices of bounding complementary circles */
    val outsides: Set<Int>,
    @Serializable(ColorCssSerializer::class)
    val fillColor: Color = Ddc.DEFAULT_CLUSTER_FILL_COLOR,
    // its use is debatable
    @Serializable(ColorCssSerializer::class)
    val borderColor: Color? = Ddc.DEFAULT_CLUSTER_BORDER_COLOR,
) {
    override fun toString(): String =
        """ClusterPart(
in = [${insides.joinToString()}],
out = [${outsides.joinToString()}],
fillColor = $fillColor, borderColor = $borderColor)"""

    /** ruff semiorder ⊆ on delimited regions; only goes off indices */
    infix fun isObviouslyInside(otherPart: ClusterPart): Boolean =
        // the more intersections the smaller the delimited region is
        insides.containsAll(otherPart.insides) &&
        outsides.containsAll(otherPart.outsides)
}