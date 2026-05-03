package domain.model

import androidx.compose.runtime.Immutable
import domain.ColorAsCss
import domain.Ix
import domain.io.DdcV2
import domain.toCssString
import kotlinx.serialization.Serializable

// MAYBE: allow arc-paths to bound regions
// MAYBE: we can alternatively use 1 BooleanArray[circles.size] to specify region bounds
//  out of the circles, and another BooleanArray[insides.size + outsides.size] to specify
//  which are in and which are out
/** Intersection of insides and outside of circles of a cluster
 * @param[insides] Indices of circles inside which we are
 * @param[outsides] Indices of circles outside of which we are
 */
@Immutable
@Serializable
data class LogicalRegion(
    /** indices of interior circles */
    override val insides: Set<Ix>,
    /** indices of bounding complementary circles */
    override val outsides: Set<Ix>,
    val fillColor: ColorAsCss = DdcV2.DEFAULT_CLUSTER_FILL_COLOR,
    // its use is debatable
    val borderColor: ColorAsCss? = DdcV2.DEFAULT_CLUSTER_BORDER_COLOR,
) : Constrained {

    override fun toString(): String =
        """LogicalRegion(in = [${insides.joinToString()}], out = [${outsides.joinToString()}], fillColor = ${fillColor.toCssString()}, borderColor = ${borderColor?.toCssString()})"""

    inline fun reIndex(
        crossinline reIndexer: (Ix) -> Ix
    ): LogicalRegion =
        copy(
            insides = insides.map(reIndexer).toSet(),
            outsides = outsides.map(reIndexer).toSet(),
        )
}