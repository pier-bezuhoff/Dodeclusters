package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ConformalExprOutput = ExprOutput
typealias ProjectiveExprOutput = ExprOutput

// NOTE: removed generic type arg, cuz it causes serialization bug https://github.com/Kotlin/kotlinx.serialization/issues/3177
/** Single-output of an [Expr.OneToOne] or [Expr.OneToMany]. */
@Immutable
@Serializable
sealed interface ExprOutput {
    @Serializable
    val expr: Expr

    @Serializable
    @SerialName("Just")
    data class Just(
        override val expr: Expr.OneToOne
    ) : ExprOutput

    @Serializable
    @SerialName("OneOf")
    data class OneOf(
        override val expr: Expr.OneToMany,
        val outputIndex: Ix
    ) : ExprOutput

    companion object {
        fun testIfTrajectoryStage(outputs: List<ExprOutput>): Boolean {
            val output0 = outputs.first()
            val expr0 = output0.expr
            if (expr0 !is Expr.TransformLike)
                return false
            when (output0) {
                is Just -> {
                    if (outputs.any { it is OneOf })
                        return false
                }
                is OneOf -> {
                    if (outputs.any {
                        it is Just || (it as OneOf).outputIndex != output0.outputIndex
                    })
                        return false
                }
            }
            // all are transforms with same output index and only differing targets
            return outputs.all {
                val expr = it.expr
                expr is Expr.TransformLike && expr.changeTarget(expr0.target) == expr0
            }
        }
    }
}

inline fun ExprOutput.reIndex(
    crossinline reIndexer: (Ix) -> Ix,
): ExprOutput =
    when (this) {
        is ExprOutput.Just -> copy(
            expr = expr.reIndex { reIndexer(it) }
        )
        is ExprOutput.OneOf -> copy(
            expr = expr.reIndex { reIndexer(it) }
        )
    }
