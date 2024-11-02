package domain.expressions

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import domain.expressions.Expr.OneToMany
import domain.expressions.Expr.OneToOne
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ExprResult = List<GCircle?>

interface ExprLike {
    val parameters: Parameters
    // each arg can in turn be computed as an expression, making up Forest-like data structure
    val args: List<Indexed>
}

// workaround for kotlinx.serialization bug https://github.com/Kotlin/kotlinx.serialization/issues/2785
private data class E(
    override val parameters: Parameters,
    override val args: List<Indexed>
) : ExprLike

/**
 * Raw expression that can have several outputs:
 * either [OneToOne] or [OneToMany]
 *
 * Whenever the order of [args] doesn't matter, enforce index-increasing order
 * @param[parameters] Static parameters used in the expression, generally
 * a collection of numbers
 * @param[args] Indexed links to dynamic point/line/circle arguments
 * */
@Serializable
@Immutable
sealed interface Expr : ExprLike {

    @Serializable
    sealed interface OneToOne : Expr
    @Serializable
    sealed interface OneToMany : Expr

    // NOTE: proper handling of dependent carrier requires computation of inverse function for any expr
    //  p' = f(Δ(f⁻¹(p)), where point p on dependent carrier f(<free>) moves to p' when <free> is affected by Δ
    @Serializable
    @SerialName("IncidentPoint")
    data class Incidence(
        override val parameters: IncidenceParameters,
        val carrier: Indexed.Circle,
    ) : OneToOne, ExprLike by E(parameters, listOf(carrier))
    @Serializable
    @SerialName("CircleByCenterAndRadius")
    data class CircleByCenterAndRadius(
        val center: Indexed.Point,
        val radiusPoint: Indexed.Point
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(center, radiusPoint))
    // MAYBE: allow ImaginaryCircle or Point to be output for further use
    @Serializable
    @SerialName("CircleBy3PerpendicularObjects")
    data class CircleBy3Points( // order-less
        val object1: Indexed,
        val object2: Indexed,
        val object3: Indexed,
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(object1, object2, object3))
    @Serializable
    @SerialName("CircleBy2ObjectsFromItsPencilAndPerpendicularObject")
    data class CircleByPencilAndPoint(
        val pencilObject1: Indexed,
        val pencilObject2: Indexed,
        val perpendicularObject: Indexed,
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(pencilObject1, pencilObject2, perpendicularObject))
    @Serializable
    @SerialName("LineBy2PerpendicularObjects")
    data class LineBy2Points( // order-less
        val object1: Indexed,
        val object2: Indexed,
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(object1, object2))
    @Serializable
    @SerialName("CircleInversion")
    data class CircleInversion(
        val target: Indexed,
        val engine: Indexed.Circle,
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(target, engine))

    @Serializable
    @SerialName("Intersection")
    data class Intersection( // order-less
        val circle1: Indexed.Circle,
        val circle2: Indexed.Circle,
    ) : OneToMany, ExprLike by E(Parameters.None, listOf(circle1, circle2))
    // TODO: point-point line interpolation
    // FIX: 1/2 of 90deg angle flickers, use orientation in tandem with inBetween parameter
    @Serializable
    @SerialName("CircleInterpolation")
    data class CircleInterpolation(
        override val parameters: InterpolationParameters,
        val startCircle: Indexed.Circle,
        val endCircle: Indexed.Circle,
    ) : OneToMany, ExprLike by E(parameters, listOf(startCircle, endCircle))
    @Serializable
    @SerialName("CircleExtrapolation")
    data class CircleExtrapolation(
        override val parameters: ExtrapolationParameters,
        val startCircle: Indexed.Circle,
        val endCircle: Indexed.Circle,
    ) : OneToMany, ExprLike by E(parameters, listOf(startCircle, endCircle))
    @Serializable
    @SerialName("LoxodromicMotion")
    data class LoxodromicMotion( // TODO: add backwards steps
        override val parameters: LoxodromicMotionParameters,
        val divergencePoint: Indexed.Point,
        val convergencePoint: Indexed.Point,
        val target: Indexed,
    ) : OneToMany, ExprLike by E(parameters, listOf(divergencePoint, convergencePoint, target))

    // MAYBE: inline
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
                            g(object1),
                            g(object2),
                            g(object3)
                        )
                        is CircleByPencilAndPoint -> computeCircleByPencilAndPoint(
                            g(pencilObject1),
                            g(pencilObject2),
                            g(perpendicularObject),
                        )
                        is LineBy2Points -> computeLineBy2Points(
                            g(object1),
                            g(object2)
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

    // MAYBE: inline
    @Throws(ClassCastException::class)
    fun mapArgs(
        reIndexer: (Indexed) -> Indexed,
    ): Expr =
        when (this) {
            is Incidence -> copy(
                carrier = reIndexer(carrier) as Indexed.Circle
            )
            is CircleByCenterAndRadius -> copy(
                center = reIndexer(center) as Indexed.Point,
                radiusPoint = reIndexer(radiusPoint) as Indexed.Point,
            )
            is CircleBy3Points -> copy(
                object1 = reIndexer(object1),
                object2 = reIndexer(object2),
                object3 = reIndexer(object3),
            )
            is CircleByPencilAndPoint -> copy(
                pencilObject1 = reIndexer(pencilObject1),
                pencilObject2 = reIndexer(pencilObject2),
                perpendicularObject = reIndexer(perpendicularObject),
            )
            is LineBy2Points -> copy(
                object1 = reIndexer(object1),
                object2 = reIndexer(object2),
            )
            is CircleInversion -> copy(
                target = reIndexer(target),
                engine = reIndexer(engine) as Indexed.Circle
            )
            is Intersection -> copy(
                circle1 = reIndexer(circle1) as Indexed.Circle,
                circle2 = reIndexer(circle2) as Indexed.Circle,
            )
            is CircleInterpolation -> copy(
                startCircle = reIndexer(startCircle) as Indexed.Circle,
                endCircle = reIndexer(endCircle) as Indexed.Circle,
            )
            is CircleExtrapolation -> copy(
                startCircle = reIndexer(startCircle) as Indexed.Circle,
                endCircle = reIndexer(endCircle) as Indexed.Circle,
            )
            is LoxodromicMotion -> copy(
                divergencePoint = reIndexer(divergencePoint) as Indexed.Point,
                convergencePoint = reIndexer(convergencePoint) as Indexed.Point,
                target = reIndexer(target)
            )
        }
}
