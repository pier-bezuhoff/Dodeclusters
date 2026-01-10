package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Single-output expression from [expr] */
@Serializable
@Immutable
sealed interface ExprOutput {
    val expr: Expr

    @Serializable
    @SerialName("Just")
    data class Just(override val expr: Expr.OneToOne) : ExprOutput
    @Serializable
    @SerialName("OneOf")
    data class OneOf(
        override val expr: Expr.OneToMany,
        val outputIndex: Ix
    ) : ExprOutput
}

inline fun ExprOutput.reIndex(
    crossinline reIndexer: (Ix) -> Ix,
): ExprOutput =
    when (this) {
        is ExprOutput.Just -> copy(
            expr.reIndex { reIndexer(it) } as Expr.OneToOne
        )
        is ExprOutput.OneOf -> copy(
            expr.reIndex { reIndexer(it) } as Expr.OneToMany
        )
    }
