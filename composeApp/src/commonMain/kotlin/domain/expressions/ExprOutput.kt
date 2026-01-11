package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ConformalExprOutput = ExprOutput<Expr.Conformal>

/** Single-value expression result of [expr]. ([Expr] in general has multi-value results) */
@Serializable
@Immutable
sealed interface ExprOutput<out EXPR> where EXPR : Expr {
    val expr: EXPR

    @Serializable
    @SerialName("Just")
    data class Just<out EXPR>(override val expr: EXPR) : ExprOutput<EXPR> where EXPR : Expr.OneToOne

    @Serializable
    @SerialName("OneOf")
    data class OneOf<out EXPR>(
        override val expr: EXPR,
        val outputIndex: Ix
    ) : ExprOutput<EXPR> where EXPR : Expr.OneToMany
}

@Suppress("UNCHECKED_CAST")
inline fun <reified EXPR : Expr> ExprOutput<EXPR>.reIndex(
    crossinline reIndexer: (Ix) -> Ix,
): ExprOutput<EXPR> =
    when (this) {
        is ExprOutput.Just -> copy(
            expr.reIndex { reIndexer(it) } as EXPR
        )
        is ExprOutput.OneOf -> copy(
            expr.reIndex { reIndexer(it) } as EXPR
        )
    }
