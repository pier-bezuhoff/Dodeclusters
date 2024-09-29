package domain.expressions

import domain.Ix

// circles: [CircleOrLine?]
// points: [Point?]
// circleExpressions: [Ix] -> Expression
// pointExpressions: [Ix] -> Expression

// associated with ToolMode and signature
sealed interface Function {
    enum class OneToOne : Function {
        CIRCLE_BY_CENTER_AND_RADIUS,
        CIRCLE_BY_3_POINTS,
        LINE_BY_2_POINTS,
        CIRCLE_INVERSION,
        INCIDENCE, // from point-circle snapping, saved as obj + perp line thru the point
    }
    enum class OneToMany : Function {
        CIRCLE_INTERPOLATION,
        CIRCLE_EXTRAPOLATION,
        LOXODROMIC_MOTION,
        INTERSECTION,
    }
}

// numeric values used, from the dialog or somewhere else
interface Parameters {
    data object None : Parameters
}

sealed interface Arg {
    // potential optimization: represent point indices as
    // -(i+1) while circle indices are +i
    sealed interface Indexed : Arg {
        val index: Ix

        data class CircleOrLine(override val index: Ix) : Indexed
        data class Point(override val index: Ix) : Indexed
    }
}

sealed interface Expression {
    val expr: Expr

    data class Just(override val expr: Expr.OneToOne) : Expression
    data class OneOf(
        override val expr: Expr.OneToMany,
        val outputIndex: Ix
    ) : Expression
}

