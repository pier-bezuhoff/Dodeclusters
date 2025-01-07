package domain.expressions.deprecated

import androidx.compose.runtime.Immutable
import domain.Ix
import domain.never
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Single-output expression from [expr] */
@Serializable
@Immutable
sealed interface _Expression {
    val expr: _Expr

    @Serializable
    @SerialName("Just")
    data class Just(override val expr: _Expr.OneToOne) : _Expression
    @Serializable
    @SerialName("OneOf")
    data class OneOf(
        override val expr: _Expr.OneToMany,
        val outputIndex: Ix
    ) : _Expression
}

@Throws(ClassCastException::class)
inline fun _reIndexExpression(
    expression: _Expression,
    crossinline pointReIndexer: (Ix) -> Ix,
    crossinline circleReIndexer: (Ix) -> Ix,
): _Expression =
    when (expression) {
        is _Expression.Just -> expression.copy(expression.expr.mapArgs { ix ->
            when (ix) {
                is _Indexed.Point -> _Indexed.Point(pointReIndexer(ix.index))
                is _Indexed.Circle -> _Indexed.Circle(circleReIndexer(ix.index))
                else -> never()
            }
        } as _Expr.OneToOne)
        is _Expression.OneOf -> expression.copy(expression.expr.mapArgs { ix ->
            when (ix) {
                is _Indexed.Point -> _Indexed.Point(pointReIndexer(ix.index))
                is _Indexed.Circle -> _Indexed.Circle(circleReIndexer(ix.index))
                else -> never()
            }
        } as _Expr.OneToMany)
        else -> never()
    }
