package domain.cluster

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import domain.expressions.CircleConstruct
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
            points = emptyList(),
            circles = circles.map { CircleConstruct.Concrete(it) },
            parts = parts
        )
}
