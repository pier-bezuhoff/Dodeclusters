package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.Serializable

/** Single-output expression from [expr] */
@Serializable
@Immutable
sealed interface Expression {
    val expr: Expr

    @Serializable
    data class Just(override val expr: Expr.OneToOne) : Expression
    @Serializable
    data class OneOf(
        override val expr: Expr.OneToMany,
        val outputIndex: Ix
    ) : Expression
}

@Throws(ClassCastException::class)
inline fun reIndexExpression(
    expression: Expression,
    crossinline pointReIndexer: (Ix) -> Ix,
    crossinline circleReIndexer: (Ix) -> Ix,
): Expression =
    when (expression) {
        is Expression.Just -> expression.copy(expression.expr.mapArgs { ix ->
            when (ix) {
                is Indexed.Point -> Indexed.Point(pointReIndexer(ix.index))
                is Indexed.Circle -> Indexed.Circle(circleReIndexer(ix.index))
            }
        } as Expr.OneToOne)
        is Expression.OneOf -> expression.copy(expression.expr.mapArgs { ix ->
            when (ix) {
                is Indexed.Point -> Indexed.Point(pointReIndexer(ix.index))
                is Indexed.Circle -> Indexed.Circle(circleReIndexer(ix.index))
            }
        } as Expr.OneToMany)
    }
