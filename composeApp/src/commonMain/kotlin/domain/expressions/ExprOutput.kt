package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ConformalExprOutput = ExprOutput<Expr.Conformal>
typealias ProjectiveExprOutput = ExprOutput<Expr.Projective>

/** Single-value expression result of [expr]. ([Expr] in general has multi-value results) */
@Immutable
@Serializable
sealed interface ExprOutput<out EXPR> where EXPR : Expr {
    // all one-to-one expr

    @Immutable
    @Serializable
    @SerialName("OneOf")
    data class OneOf<out EXPR>(
        val expr: EXPR,
        val outputIndex: Ix
    ) : ExprOutput<EXPR> where EXPR : Expr.OneToMany
}

@Suppress("UNCHECKED_CAST")
inline fun <reified EXPR : Expr> ExprOutput<EXPR>.reIndex(
    crossinline reIndexer: (Ix) -> Ix,
): ExprOutput<EXPR> =
    when (this) {
        is Expr.OneToOne ->
            (this as Expr).reIndex { reIndexer(it) } as ExprOutput<EXPR>
        is ExprOutput.OneOf -> copy(
            expr = expr.reIndex { reIndexer(it) }
        )
    }
