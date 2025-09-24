package domain.cluster

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.Line
import domain.ColorAsCss
import domain.Ix
import domain.expressions.ObjectConstruct
import domain.expressions.deprecated._CircleConstruct
import domain.expressions.deprecated._Expression
import domain.expressions.deprecated._Indexed
import domain.expressions.deprecated._PointConstruct
import kotlinx.serialization.Serializable

// aka ClusterV3.1, deprecate for Constellation (that does not employ _Indexed)
/** Evolution of [Cluster] that contains both concretes and expressions, deprecated in
 * favor of Constellation */
@Serializable
@Immutable
data class _Constellation(
    val points: List<_PointConstruct>,
    val circles: List<_CircleConstruct>,
    val parts: List<LogicalRegion>,
    // purely decorative, the "real" border color can be specified
    // using unfilled single-circle ClusterPart with borderColor
    val circleColors: Map<Ix, ColorAsCss> = emptyMap(),
) {

    fun toExpressionMap(): Map<_Indexed, _Expression?> =
        points.mapIndexed { i, p ->
            val ix = _Indexed.Point(i)
            when (p) {
                is _PointConstruct.Concrete -> ix to null
                is _PointConstruct.Dynamic -> ix to p.expression
            }
        }.toMap() +
                circles.mapIndexed { i, p ->
                    val ix = _Indexed.Circle(i)
                    when (p) {
                        is _CircleConstruct.Concrete -> ix to null
                        is _CircleConstruct.Dynamic -> ix to p.expression
                    }
                }.toMap()

    fun toConstellation(): Constellation {
        // indices are transformed -> circles; points
        val nCircles = circles.size
        val circleConstructs = circles.map { c ->
            when (c) {
                is _CircleConstruct.Concrete ->
                    when (val circleOrLine = c.circleOrLine) {
                        is Line -> ObjectConstruct.ConcreteLine(circleOrLine)
                        is Circle -> ObjectConstruct.ConcreteCircle(circleOrLine)
                    }
                is _CircleConstruct.Dynamic -> ObjectConstruct.Dynamic(
                    c.expression.toExpression { ixed ->
                        when (ixed) {
                            is _Indexed.Circle -> ixed.index
                            is _Indexed.Point -> nCircles + ixed.index
                        }
                    }
                )
            }
        }
        val pointConstructs = points.map { c ->
            when (c) {
                is _PointConstruct.Concrete -> ObjectConstruct.ConcretePoint(c.point)
                is _PointConstruct.Dynamic -> ObjectConstruct.Dynamic(
                    c.expression.toExpression { ixed ->
                        when (ixed) {
                            is _Indexed.Circle -> ixed.index
                            is _Indexed.Point -> nCircles + ixed.index
                        }
                    }
                )
            }
        }
        return Constellation(
            objects = circleConstructs + pointConstructs,
            parts = parts,
            objectColors = circleColors
        )
    }

    companion object {
        val SAMPLE = _Constellation(
            points = emptyList(),
            circles = listOf(
                Circle(0.0, 0.0, 200.0),
            ).map { _CircleConstruct.Concrete(it) },
            parts = emptyList()
        )
    }
}