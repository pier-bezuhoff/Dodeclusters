package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Single-output expression from [expr] */
@Serializable
@Immutable
sealed interface Expression {
    val expr: Expr

    @Serializable
    @SerialName("Just")
    data class Just(override val expr: Expr.OneToOne) : Expression
    @Serializable
    @SerialName("OneOf")
    data class OneOf(
        override val expr: Expr.OneToMany,
        val outputIndex: Ix
    ) : Expression
}

inline fun reIndexExpression(
    expression: Expression,
    crossinline reIndexer: (Ix) -> Ix,
): Expression =
    when (expression) {
        is Expression.Just -> expression.copy(
            expression.expr.mapArgs { reIndexer(it) } as Expr.OneToOne
        )
        is Expression.OneOf -> expression.copy(
            expression.expr.mapArgs { reIndexer(it) } as Expr.OneToMany
        )
    }
