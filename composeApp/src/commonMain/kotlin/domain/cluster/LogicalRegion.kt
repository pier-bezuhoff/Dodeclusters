package domain.cluster

import androidx.compose.runtime.Immutable
import domain.ColorAsCss
import domain.io.DdcV2
import domain.io.DdcV4
import kotlinx.serialization.Serializable

// MAYBE: we can alternatively use 1 BooleanArray[circles.size] to specify region bounds
//  out of the circles, and another BooleanArray[insides.size + outsides.size] to specify
//  which are in and which are out
/** Intersection of insides and outside of circles of a cluster
 * @param[insides] Indices of circles inside which we are
 * @param[outsides] Indices of circles outside of which we are
 * */
@Serializable
@Immutable
data class LogicalRegion(
    /** indices of interior circles */
    val insides: Set<Int>,
    /** indices of bounding complementary circles */
    val outsides: Set<Int>,
    val fillColor: ColorAsCss = DdcV2.DEFAULT_CLUSTER_FILL_COLOR,
    // its use is debatable
    val borderColor: ColorAsCss? = DdcV2.DEFAULT_CLUSTER_BORDER_COLOR,
) {
    override fun toString(): String =
        """LogicalRegion(
in = [${insides.joinToString()}],
out = [${outsides.joinToString()}],
fillColor = $fillColor, borderColor = $borderColor)"""

    /** ruff semiorder ⊆ on delimited regions; only goes off indices */
    infix fun isObviouslyInside(otherRegion: LogicalRegion): Boolean =
        // the more intersections the smaller the delimited region is
        insides.containsAll(otherRegion.insides) &&
        outsides.containsAll(otherRegion.outsides)
}