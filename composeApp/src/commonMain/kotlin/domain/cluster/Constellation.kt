package domain.cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import domain.ColorAsCss
import domain.ColorCssSerializer
import domain.Ix
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
    // purely decorative, the "real" border color can be specified
    // using unfilled single-circle ClusterPart with borderColor
    val circleColors: Map<Ix, ColorAsCss> = emptyMap(),
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