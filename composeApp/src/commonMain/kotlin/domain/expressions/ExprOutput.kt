package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ConformalExprOutput = ExprOutput<Expr.Conformal>

/** Single-value expression result of [expr]. ([Expr] in general has multi-value results) */
@Serializable
@Immutable
sealed interface ExprOutput<out E> where E : Expr {
    val expr: E

    @Serializable
    @SerialName("Just")
    data class Just<out E>(override val expr: E) : ExprOutput<E> where E : Expr.OneToOne

    @Serializable
    @SerialName("OneOf")
    data class OneOf<out E>(
        override val expr: E,
        val outputIndex: Ix
    ) : ExprOutput<E> where E : Expr.OneToMany
}

@Suppress("UNCHECKED_CAST")
inline fun <reified E : Expr> ExprOutput<E>.reIndex(
    crossinline reIndexer: (Ix) -> Ix,
): ExprOutput<E> =
    when (this) {
        is ExprOutput.Just -> copy(
            expr.reIndex { reIndexer(it) } as E
        )
        is ExprOutput.OneOf -> copy(
            expr.reIndex { reIndexer(it) } as E
        )
    }
