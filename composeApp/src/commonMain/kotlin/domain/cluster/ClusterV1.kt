package domain.cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import data.geometry.Circle
import domain.ColorCssSerializer
import domain.io.Ddc
import kotlinx.serialization.Serializable

/** old version of Cluster */
@Serializable
@Immutable
data class ClusterV1(
    val circles: List<Circle>,
    /** union of parts comprised of circle intersections */
    val parts: List<ClusterPartV1> = emptyList(),
    /** fill regions inside / wireframe */
    val filled: Boolean = Ddc.DEFAULT_CLUSTER_FILLED,
) {
    fun toCluster(): Cluster =
        Cluster(
            circles,
            parts.map {
                ClusterPart(
                    insides = it.insides,
                    outsides = it.outsides,
                    fillColor = it.fillColor,
                    borderColor = it.borderColor
                )
            }
        )
}

/** intersection of insides and outside of circles of a cluster */
@Serializable
@Immutable
data class ClusterPartV1(
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
        "ClusterPartV1(\nin = [${insides.joinToString()}],\nout = [${outsides.joinToString()}],\ncolor = $fillColor)"

    /** ruff semiorder ⊆ on delimited regions; only goes off indices */
    infix fun isObviouslyInside(otherPart: ClusterPartV1): Boolean =
        // the more intersections the smaller the delimited region is
        insides.containsAll(otherPart.insides) &&
                outsides.containsAll(otherPart.outsides)
}
