package domain.cluster

import androidx.compose.runtime.Immutable
import domain.expressions.CircleConstruct
import domain.expressions.Expression
import domain.expressions.Indexed
import domain.expressions.PointConstruct
import kotlinx.serialization.Serializable

// aka ClusterV3
/** Evolution of [Cluster] that contains both concretes and expressions */
@Serializable
@Immutable
data class Constellation(
    val points: List<PointConstruct>,
    val circles: List<CircleConstruct>,
    val parts: List<ClusterPart>, // TODO: transition to ArcPath's
) {

    fun toExpressionMap(): Map<Indexed, Expression?> =
        points.mapIndexed { i, p ->
            val ix = Indexed.Point(i)
            when (p) {
                is PointConstruct.Concrete -> ix to null
                is PointConstruct.Dynamic -> ix to p.expression
            }
        }.toMap() +
        circles.mapIndexed { i, p ->
            val ix = Indexed.Circle(i)
            when (p) {
                is CircleConstruct.Concrete -> ix to null
                is CircleConstruct.Dynamic -> ix to p.expression
            }
        }.toMap()
}