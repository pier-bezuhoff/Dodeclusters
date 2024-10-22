package domain.expressions

import androidx.compose.runtime.Immutable
import domain.Ix
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// circles: [CircleOrLine?]
// points: [Point?]
// circleExpressions: [Ix] -> Expression
// pointExpressions: [Ix] -> Expression

// not sure it is of use
@Serializable
@Immutable
sealed interface Function {
    @Serializable
    enum class OneToOne : Function {
        CIRCLE_BY_CENTER_AND_RADIUS,
        CIRCLE_BY_3_POINTS,
        LINE_BY_2_POINTS,
        CIRCLE_INVERSION,
        INCIDENCE, // from point-circle snapping, saved as obj + perp line thru the point
    }
    @Serializable
    enum class OneToMany : Function {
        CIRCLE_INTERPOLATION,
        CIRCLE_EXTRAPOLATION,
        LOXODROMIC_MOTION,
        INTERSECTION,
    }
}

// potential optimization: represent point indices as
// -(i+1) while circle indices are +i
@Serializable
@Immutable
sealed interface Indexed {
    val index: Ix

    /** index for a circle or a line */
    @Serializable
    data class Circle(override val index: Ix) : Indexed
    @Serializable
    data class Point(override val index: Ix) : Indexed
}

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

