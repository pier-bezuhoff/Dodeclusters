package domain.expressions.deprecated

import androidx.compose.runtime.Immutable
import domain.Ix
import domain.expressions.Expr
import domain.expressions.Expression
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

    fun toExpression(
        foldIndexed: (_Indexed) -> Ix,
    ): Expression =
        when (val e = this) {
            is Just -> Expression.Just(when (val expr = e.expr) {
                is _Expr.CircleBy3Points -> Expr.CircleBy3Points(foldIndexed(expr.object1), foldIndexed(expr.object2), foldIndexed(expr.object3))
                is _Expr.CircleByCenterAndRadius -> Expr.CircleByCenterAndRadius(foldIndexed(expr.center), foldIndexed(expr.radiusPoint))
                is _Expr.CircleByPencilAndPoint -> Expr.CircleByPencilAndPoint(foldIndexed(expr.pencilObject1), foldIndexed(expr.pencilObject2), foldIndexed(expr.perpendicularObject))
                is _Expr.CircleInversion -> Expr.CircleInversion(foldIndexed(expr.target), foldIndexed(expr.engine))
                is _Expr.Incidence -> Expr.Incidence(expr.parameters, foldIndexed(expr.carrier))
                is _Expr.LineBy2Points -> Expr.LineBy2Points(foldIndexed(expr.object1), foldIndexed(expr.object2))
            }
            )
            is OneOf -> Expression.OneOf(when (val expr = e.expr) {
                is _Expr.CircleExtrapolation -> Expr.CircleExtrapolation(expr.parameters, foldIndexed(expr.startCircle), foldIndexed(expr.endCircle))
                is _Expr.CircleInterpolation -> Expr.CircleInterpolation(expr.parameters, foldIndexed(expr.startCircle), foldIndexed(expr.endCircle))
                is _Expr.Intersection -> Expr.Intersection(foldIndexed(expr.circle1), foldIndexed(expr.circle2))
                is _Expr.LoxodromicMotion -> Expr.LoxodromicMotion(expr.parameters, foldIndexed(expr.divergencePoint), foldIndexed(expr.convergencePoint), foldIndexed(expr.target))
            }, e.outputIndex)
        }
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
