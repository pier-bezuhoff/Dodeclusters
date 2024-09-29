package domain.expressions

import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import ui.edit_cluster.ExtrapolationParameters
import ui.edit_cluster.InterpolationParameters
import ui.edit_cluster.LoxodromicMotionParameters

typealias ExprResult = List<GCircle?>

sealed class Expr(
    val function: Function,
    open val parameters: Parameters,
    // Arg can also be computed as an expression, making up Forest-like data structure
    val args: List<Arg.Indexed>,
) {
    sealed class OneToOne(
        function: Function.OneToOne,
        parameters: Parameters,
        args: List<Arg.Indexed>,
    ) : Expr(function, parameters, args)
    sealed class OneToMany(
        function: Function.OneToMany,
        parameters: Parameters,
        args: List<Arg.Indexed>,
    ) : Expr(function, parameters, args)

    // NOTE: proper handling of dependent carrier requires computation of inverse function for any expr
    //  p' = f(Δ(f⁻¹(p)), where point p on dependent carrier f(<free>) moves to p' when <free> is affected by Δ
    data class Incidence(
        override val parameters: IncidenceParameters,
        val point: Arg.Indexed.Point,
        val carrier: Arg.Indexed.CircleOrLine,
    ) : OneToOne(Function.OneToOne.INCIDENCE, parameters, listOf(point, carrier))
    data class CircleByCenterAndRadius(
        val center: Arg.Indexed.Point,
        val radiusPoint: Arg.Indexed.Point
    ) : OneToOne(
        Function.OneToOne.CIRCLE_BY_CENTER_AND_RADIUS,
        Parameters.None, listOf(center, radiusPoint))
    data class CircleBy3Points(
        val point1: Arg.Indexed,
        val point2: Arg.Indexed,
        val point3: Arg.Indexed,
    ) : OneToOne(
        Function.OneToOne.CIRCLE_BY_3_POINTS,
        Parameters.None, listOf(point1, point2, point3))
    data class LineBy2Points(
        val point1: Arg.Indexed,
        val point2: Arg.Indexed,
    ) : OneToOne(Function.OneToOne.LINE_BY_2_POINTS, Parameters.None, listOf(point1, point2))
    data class CircleInversion(
        val target: Arg.Indexed,
        val engine: Arg.Indexed.CircleOrLine,
    ) : OneToOne(Function.OneToOne.CIRCLE_INVERSION, Parameters.None, listOf(target, engine))

    data class Intersection(
        val circle1: Arg.Indexed.CircleOrLine,
        val circle2: Arg.Indexed.CircleOrLine,
    ) : OneToMany(Function.OneToMany.INTERSECTION, Parameters.None, listOf(circle1, circle2))
    // TODO: point-point line interpolation
    data class CircleInterpolation(
        override val parameters: InterpolationParameters,
        val startCircle: Arg.Indexed.CircleOrLine,
        val endCircle: Arg.Indexed.CircleOrLine,
    ) : OneToMany(Function.OneToMany.CIRCLE_INTERPOLATION, parameters, listOf(startCircle, endCircle))
    data class CircleExtrapolation(
        override val parameters: ExtrapolationParameters,
        val startCircle: Arg.Indexed.CircleOrLine,
        val endCircle: Arg.Indexed.CircleOrLine,
    ) : OneToMany(Function.OneToMany.CIRCLE_EXTRAPOLATION, parameters, listOf(startCircle, endCircle))
    data class LoxodromicMotion(
        override val parameters: LoxodromicMotionParameters,
        val divergencePoint: Arg.Indexed.Point,
        val convergencePoint: Arg.Indexed.Point,
        val target: Arg.Indexed,
    ) : OneToMany(
        Function.OneToMany.LOXODROMIC_MOTION, parameters,
        listOf(divergencePoint, convergencePoint, target)
    )

    fun eval(
        get: (Arg.Indexed) -> GCircle?,
    ): ExprResult {
        val g = { ix: Arg.Indexed ->
            get(ix) ?: throw NullPointerException() // i miss MonadError
        }
        val c = { ix: Arg.Indexed.CircleOrLine ->
            get(ix) as? CircleOrLine ?: throw NullPointerException()
        }
        val p = { ix: Arg.Indexed.Point ->
            get(ix) as? Point ?: throw NullPointerException()
        }
        try {
            // idt it's worth to polymorphism eval
            return when (this) {
                is OneToOne -> {
                    val result = when (this) {
                        is Incidence -> computeIncidence(
                            parameters,
                            p(point),
                            c(carrier)
                        )
                        is CircleByCenterAndRadius -> computeCircleByCenterAndRadius(
                            p(center),
                            p(radiusPoint)
                        )
                        is CircleBy3Points -> computeCircleBy3Points(
                            g(point1),
                            g(point2),
                            g(point3)
                        )
                        is LineBy2Points -> computeLineBy2Points(
                            g(point1),
                            g(point2)
                        )
                        is CircleInversion -> computeCircleInversion(
                            g(target),
                            g(engine)
                        )
                    }
                    listOf(result)
                }
                is Intersection -> computeIntersection(
                    c(circle1),
                    c(circle2)
                )
                is CircleInterpolation -> computeCircleInterpolation(
                    parameters,
                    c(startCircle),
                    c(endCircle)
                )
                is CircleExtrapolation -> computeCircleExtrapolation(
                    parameters,
                    c(startCircle),
                    c(endCircle)
                )
                is LoxodromicMotion -> computeLoxodromicMotion(
                    parameters,
                    p(divergencePoint),
                    p(convergencePoint),
                    g(target)
                )
            }
        } catch (e: NullPointerException) {
            return emptyList()
        }
    }
}

data class IncidenceParameters(
    val order: Double
) : Parameters

