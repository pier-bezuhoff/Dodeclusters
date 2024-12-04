package domain.cluster

import androidx.compose.runtime.Immutable
import data.geometry.Circle
import domain.ColorAsCss
import domain.Ix
import domain.expressions.Expression
import domain.expressions.ObjectConstruct
import kotlinx.serialization.Serializable

// aka ClusterV3.2
/** Evolution of [Cluster] that contains both concretes and expressions */
@Serializable
@Immutable
data class Constellation(
    val objects: List<ObjectConstruct>,
    val parts: List<LogicalRegion>, // TODO: transition to ArcPath's
    // purely decorative, the "real" border color can be specified
    // using unfilled single-circle ClusterPart with borderColor
    val objectColors: Map<Ix, ColorAsCss> = emptyMap(),
) {
    fun toExpressionMap(): Map<Ix, Expression?> =
        objects.mapIndexed { ix, o ->
            when (o) {
                is ObjectConstruct.Dynamic -> ix to o.expression
                else -> ix to null
            }
        }.toMap()

    companion object {
        val SAMPLE = Constellation(
            objects = listOf(
                Circle(0.0, 0.0, 200.0),
            ).map { ObjectConstruct.ConcreteCircle(it) },
            parts = emptyList()
        )
    }
}