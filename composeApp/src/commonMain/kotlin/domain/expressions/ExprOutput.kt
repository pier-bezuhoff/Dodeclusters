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
        /** whether `e1.expr` and `e2.expr` are [Expr.TransformLike], of the
         * same [Expr] type (possibly with differing targets) and
         * [e1] & [e2] have the same `outputIndex` */
        fun areSameStageTransforms(e1: OneOf, e2: OneOf): Boolean =
            e1.outputIndex == e2.outputIndex &&
            when (val expr1 = e1.expr) {
                is Expr.LoxodromicMotion ->
                    when (val expr2 = e2.expr) {
                        is Expr.LoxodromicMotion ->
                            when (expr1) {
                                expr2.copy(
                                    target = expr1.target,
                                    otherHalfStart = expr1.otherHalfStart,
                                ) -> true
                                else -> false
                            }
                        else -> false
                    }
                is Expr.TransformLike ->
                    when (val expr2 = e2.expr) {
                        is Expr.TransformLike ->
                            expr1 == expr2.changeTarget(expr1.target)
                        else -> false
                    }
                else -> false
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
