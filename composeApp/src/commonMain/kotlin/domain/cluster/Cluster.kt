package domain.cluster

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.Line
import domain.expressions.ObjectConstruct
import kotlinx.serialization.Serializable

// ClusterV2
@Serializable
@Immutable
data class Cluster(
    val circles: List<CircleOrLine>,
    /** union of parts comprised of circle intersections */
    val parts: List<LogicalRegion> = emptyList(),
) {
    fun toConstellation(): Constellation =
        Constellation(
            objects = circles.map { c ->
                when (c) {
                    is Circle -> ObjectConstruct.ConcreteCircle(c)
                    is Line -> ObjectConstruct.ConcreteLine(c)
                }
            },
            parts = parts
        )
}
