package domain.expressions

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ExprResult = List<GCircle?>

/** Whenever the order of [args] doesn't matter, enforce index-increasing order */
@Serializable
@Immutable
sealed class Expr(
    @SerialName("parameters_0")
    open val parameters: Parameters,
    // each arg can in turn be computed as an expression, making up Forest-like data structure
    @SerialName("args_0")
    open val args: List<Indexed>,
) {
    @Serializable
    sealed class OneToOne(
        @SerialName("params")
        override val parameters: Parameters,
        override val args: List<Indexed>,
    ) : Expr(parameters, args)
    @Serializable
    sealed class OneToMany(
        @SerialName("params")
        override val parameters: Parameters,
        override val args: List<Indexed>,
    ) : Expr(parameters, args)

    // NOTE: proper handling of dependent carrier requires computation of inverse function for any expr
    //  p' = f(Δ(f⁻¹(p)), where point p on dependent carrier f(<free>) moves to p' when <free> is affected by Δ
    @Serializable
    data class Incidence(
        override val parameters: IncidenceParameters,
        val carrier: Indexed.Circle,
    ) : OneToOne(parameters, listOf(carrier))
    @Serializable
    data class CircleByCenterAndRadius(
        val center: Indexed.Point,
        val radiusPoint: Indexed.Point
    ) : OneToOne(Parameters.None, listOf(center, radiusPoint))
    @Serializable
    data class CircleBy3Points( // order-less
        val point1: Indexed,
        val point2: Indexed,
        val point3: Indexed,
    ) : OneToOne(Parameters.None, listOf(point1, point2, point3))
    @Serializable
    data class CircleByPencilAndPoint(
        val circle1: Indexed,
        val circle2: Indexed,
        val point: Indexed,
    ) : OneToOne(Parameters.None, listOf(circle1, circle2, point))
    @Serializable
    data class LineBy2Points( // order-less
        val point1: Indexed,
        val point2: Indexed,
    ) : OneToOne(Parameters.None, listOf(point1, point2))
    @Serializable
    data class CircleInversion(
        val target: Indexed,
        val engine: Indexed.Circle,
    ) : OneToOne(Parameters.None, listOf(target, engine))

    @Serializable
    data class Intersection( // order-less
        val circle1: Indexed.Circle,
        val circle2: Indexed.Circle,
    ) : OneToMany(Parameters.None, listOf(circle1, circle2))
    // TODO: point-point line interpolation
    @Serializable
    data class CircleInterpolation(
        override val parameters: InterpolationParameters,
        val startCircle: Indexed.Circle,
        val endCircle: Indexed.Circle,
    ) : OneToMany(parameters, listOf(startCircle, endCircle))
    @Serializable
    data class CircleExtrapolation(
        override val parameters: ExtrapolationParameters,
        val startCircle: Indexed.Circle,
        val endCircle: Indexed.Circle,
    ) : OneToMany(parameters, listOf(startCircle, endCircle))
    @Serializable
    data class LoxodromicMotion(
        override val parameters: LoxodromicMotionParameters,
        val divergencePoint: Indexed.Point,
        val convergencePoint: Indexed.Point,
        val target: Indexed,
    ) : OneToMany(
        parameters,
        listOf(divergencePoint, convergencePoint, target)
    )

    fun eval(
        get: (Indexed) -> GCircle?,
    ): ExprResult {
        val g = { ix: Indexed ->
            get(ix) ?: throw NullPointerException() // i miss MonadError
        }
        val c = { ix: Indexed.Circle ->
            get(ix) as? CircleOrLine ?: throw NullPointerException()
        }
        val p = { ix: Indexed.Point ->
            get(ix) as? Point ?: throw NullPointerException()
        }
        try {
            // idt it's worth to polymorphism eval
            return when (this) {
                is OneToOne -> {
                    val result = when (this) {
                        is Incidence -> computeIncidence(
                            parameters,
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
                        is CircleByPencilAndPoint -> computeCircleByPencilAndPoint(
                            g(circle1),
                            g(circle2),
                            g(point),
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

@Serializable
data class IncidenceParameters(
    val order: Double
) : Parameters

