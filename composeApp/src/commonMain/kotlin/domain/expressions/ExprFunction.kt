package domain.expressions

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// not sure it is of use
@Serializable
@Immutable
sealed interface ExprFunction {
    @Serializable
    enum class OneToOne : ExprFunction {
        CIRCLE_BY_CENTER_AND_RADIUS,
        CIRCLE_BY_3_POINTS,
        CIRCLE_BY_PENCIL_AND_POINT,
        LINE_BY_2_POINTS,
        CIRCLE_INVERSION,
        INCIDENCE, // from point-circle snapping, saved as obj + perp line thru the point
    }
    @Serializable
    enum class OneToMany : ExprFunction {
        CIRCLE_INTERPOLATION,
        CIRCLE_EXTRAPOLATION,
        LOXODROMIC_MOTION,
        INTERSECTION,
    }
}

fun expr2fun(expr: Expr): ExprFunction =
    when (expr) {
        is Expr.Incidence -> ExprFunction.OneToOne.INCIDENCE
        is Expr.CircleByCenterAndRadius -> ExprFunction.OneToOne.CIRCLE_BY_CENTER_AND_RADIUS
        is Expr.LineBy2Points -> ExprFunction.OneToOne.LINE_BY_2_POINTS
        is Expr.CircleBy3Points -> ExprFunction.OneToOne.CIRCLE_BY_3_POINTS
        is Expr.CircleByPencilAndPoint -> ExprFunction.OneToOne.CIRCLE_BY_PENCIL_AND_POINT
        is Expr.CircleInversion -> ExprFunction.OneToOne.CIRCLE_INVERSION
        is Expr.Intersection -> ExprFunction.OneToMany.INTERSECTION
        is Expr.CircleInterpolation -> ExprFunction.OneToMany.CIRCLE_INTERPOLATION
        is Expr.CircleExtrapolation -> ExprFunction.OneToMany.CIRCLE_EXTRAPOLATION
        is Expr.LoxodromicMotion -> ExprFunction.OneToMany.LOXODROMIC_MOTION
    }

// hmm, i think Expr.mapArgs is enough for the most part
fun fun2expr(
    exprFunction: ExprFunction,
    parameters: Parameters,
    args: List<Indexed>
): Expr =
    when (exprFunction) {
        ExprFunction.OneToOne.INCIDENCE ->
            Expr.Incidence(parameters as IncidenceParameters, args.first() as Indexed.Circle)
        ExprFunction.OneToOne.CIRCLE_BY_CENTER_AND_RADIUS -> TODO()
        ExprFunction.OneToOne.CIRCLE_BY_3_POINTS -> TODO()
        ExprFunction.OneToOne.CIRCLE_BY_PENCIL_AND_POINT -> TODO()
        ExprFunction.OneToOne.LINE_BY_2_POINTS -> TODO()
        ExprFunction.OneToOne.CIRCLE_INVERSION -> TODO()
        ExprFunction.OneToMany.INTERSECTION -> TODO()
        ExprFunction.OneToMany.CIRCLE_INTERPOLATION -> TODO()
        ExprFunction.OneToMany.CIRCLE_EXTRAPOLATION -> TODO()
        ExprFunction.OneToMany.LOXODROMIC_MOTION -> TODO()
    }