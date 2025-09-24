package domain.cluster

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.Point
import domain.ColorAsCss
import domain.Ix
import domain.expressions.Expr
import domain.expressions.Expression
import domain.expressions.ObjectConstruct
import domain.expressions.reIndex
import kotlinx.serialization.Serializable

// aka ClusterV3.2
/**
 * Evolution of [Cluster] (v3.2) that contains both concretes and expressions
 * @param[phantoms] indices of hidden [objects]
 */
@Immutable
@Serializable
data class Constellation(
    val objects: List<ObjectConstruct>,
    // TODO: rename parts to regions
    val parts: List<LogicalRegion>, // TODO: transition to ArcPath's
    // purely decorative, the "real" border color can be specified
    // using unfilled single-circle ClusterPart with borderColor
    val objectColors: Map<Ix, ColorAsCss> = emptyMap(),
    val objectLabels: Map<Ix, String> = emptyMap(),
    val backgroundColor: ColorAsCss? = null,
    val phantoms: List<Ix> = emptyList(),
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
        require(objectLabels.keys.all { it in allObjectIndices }) {
            "Invalid Constellation.objectLabels of $this"
        }
        require(phantoms.all { it in allObjectIndices }) {
            "Invalid Constellation.phantoms of $this"
        }
    }

    fun toExpressionMap(): Map<Ix, Expression?> =
        objects.mapIndexed { ix0, o ->
            val ix = ix0 - FIRST_INDEX
            when (o) {
                is ObjectConstruct.Dynamic -> ix to o.expression.reIndex { it - FIRST_INDEX }
                else -> ix to null
            }
        }.toMap()

    /**
     * Replace deprecated [Expression]s with proper substitutes.
     * So far it replaces:
     * - [Expr.LineBy2Points] with [Expr.CircleBy3Points], adding [Point.CONFORMAL_INFINITY] at
     * index `0` when necessary.
     */
    fun updated(): Constellation {
//        return this
        val hasLineBy2Points = objects.any { construct ->
            construct is ObjectConstruct.Dynamic &&
            construct.expression.expr is Expr.LineBy2Points
        }
        return if (hasLineBy2Points) {
            val infinityIndex = objects.indexOfFirst {
                it is ObjectConstruct.ConcretePoint &&
                it.point == Point.CONFORMAL_INFINITY
            }
            if (infinityIndex == -1) {
                // lets add infinity @index=0
                // and shift all indexes by +1
                val infinity = ObjectConstruct.ConcretePoint(Point.CONFORMAL_INFINITY)
                val newObjects = objects.map { construct ->
                    when (construct) {
                        is ObjectConstruct.Dynamic -> construct.copy(
                            expression = when (val expr = construct.expression.expr) {
                                is Expr.LineBy2Points ->
                                    Expression.Just(Expr.CircleBy3Points(
                                        expr.object1 + 1,
                                        expr.object2 + 1,
                                        0, // infinity index
                                    ))
                                else -> construct.expression.reIndex { it + 1 }
                            }
                        )
                        else -> construct
                    }
                }
                copy(
                    objects = listOf(infinity) + newObjects,
                    parts = parts.map { part -> part.reIndex { it + 1 } },
                    objectColors = objectColors.mapKeys { (ix, _) -> ix + 1 },
                    objectLabels = objectLabels.mapKeys { (ix, _) -> ix + 1 },
                    backgroundColor = backgroundColor,
                    phantoms = phantoms.map { it + 1 },
                )
            } else {
                copy(
                    objects = objects.map { construct ->
                        when (construct) {
                            is ObjectConstruct.Dynamic ->
                                ObjectConstruct.Dynamic(
                                    when (val expr = construct.expression.expr) {
                                        is Expr.LineBy2Points ->
                                            Expression.Just(Expr.CircleBy3Points(
                                                expr.object1, expr.object2, infinityIndex,
                                            ))
                                        else -> construct.expression
                                    }
                                )
                            else -> construct
                        }
                    }
                )
            }
        } else this
    }

    companion object {
        const val FIRST_INDEX: Int = 0
        val SAMPLE = Constellation(
            objects = listOf(
                Circle(0.0, 0.0, 200.0),
            ).map { ObjectConstruct.ConcreteCircle(it) },
            parts = emptyList()
        )
    }
}