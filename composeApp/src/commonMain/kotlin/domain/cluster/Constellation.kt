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
    init {
        val allObjectIndices = objects.indices.toSet()
        require(
            objects
                .filterIsInstance<ObjectConstruct.Dynamic>()
                .all {
                    it.expression.expr.args.all { arg ->
                        arg in allObjectIndices
                    }
                }
        ) { "Invalid expressions in Constellation.objects of $this" }
        require(parts.all { region ->
            region.insides.all { it in allObjectIndices } &&
            region.outsides.all { it in allObjectIndices }
        }) { "Invalid Constellation.parts of $this" }
        require(objectColors.keys.all { it in allObjectIndices }) {
            "Invalid Constellation.objectColors of $this"
        }
    }

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