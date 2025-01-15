package domain.expressions

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import domain.Ix
import domain.expressions.Expr.OneToMany
import domain.expressions.Expr.OneToOne
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ExprResult = List<GCircle?>

interface ExprLike {
    val parameters: Parameters
    // each arg can in turn be computed as an expression, making up Forest-like data structure
    val args: List<Ix>
}

// workaround for kotlinx.serialization bug https://github.com/Kotlin/kotlinx.serialization/issues/2785
private data class E(
    override val parameters: Parameters,
    override val args: List<Ix>
) : ExprLike

/**
 * Raw expression that can have several outputs:
 * either [OneToOne] or [OneToMany]
 *
 * Whenever the order of [args] doesn't matter, enforce index-increasing order
 * @property[parameters] Static parameters used in the expression, generally
 * a collection of numbers
 * @property[args] Indexed links to dynamic point/line/circle arguments
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
        val carrier: Ix,
    ) : OneToOne, ExprLike by E(parameters, listOf(carrier))
    @Serializable
    @SerialName("CircleByCenterAndRadius")
    data class CircleByCenterAndRadius(
        val center: Ix,
        val radiusPoint: Ix
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(center, radiusPoint))
    // MAYBE: allow ImaginaryCircle or Point to be output for further use
    @Serializable
    @SerialName("CircleBy3PerpendicularObjects")
    data class CircleBy3Points( // order-less
        val object1: Ix,
        val object2: Ix,
        val object3: Ix,
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(object1, object2, object3))
    @Serializable
    @SerialName("CircleBy2ObjectsFromItsPencilAndPerpendicularObject")
    data class CircleByPencilAndPoint(
        val pencilObject1: Ix,
        val pencilObject2: Ix,
        val perpendicularObject: Ix,
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(pencilObject1, pencilObject2, perpendicularObject))
    @Serializable
    @SerialName("LineBy2PerpendicularObjects")
    data class LineBy2Points( // order-less
        val object1: Ix,
        val object2: Ix,
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(object1, object2))
    @Serializable
    @SerialName("CircleInversion")
    data class CircleInversion(
        val target: Ix,
        val engine: Ix,
    ) : OneToOne, ExprLike by E(Parameters.None, listOf(target, engine))
    @Serializable
    @SerialName("CircleBy2PointsAndSagittaRatio")
    data class CircleBy2PointsAndSagittaRatio(
        override val parameters: SagittaRatioParameters,
        val chordStartPoint: Ix,
        val chordEndPoint: Ix,
    ) : OneToOne, ExprLike by E(parameters, listOf(chordStartPoint, chordEndPoint))

    @Serializable
    @SerialName("Intersection")
    data class Intersection( // order-less
        val circle1: Ix,
        val circle2: Ix,
    ) : OneToMany, ExprLike by E(Parameters.None, listOf(circle1, circle2))
    // TODO: point-point line interpolation
    // FIX: 1/2 of 90deg angle flickers, use orientation in tandem with inBetween parameter
    @Serializable
    @SerialName("CircleInterpolation")
    data class CircleInterpolation(
        override val parameters: InterpolationParameters,
        val startCircle: Ix,
        val endCircle: Ix,
    ) : OneToMany, ExprLike by E(parameters, listOf(startCircle, endCircle))
    @Serializable
    @SerialName("CircleExtrapolation")
    data class CircleExtrapolation(
        override val parameters: ExtrapolationParameters,
        val startCircle: Ix,
        val endCircle: Ix,
    ) : OneToMany, ExprLike by E(parameters, listOf(startCircle, endCircle))
    @Serializable
    @SerialName("LoxodromicMotion")
    data class LoxodromicMotion( // TODO: add backwards steps
        override val parameters: LoxodromicMotionParameters,
        val divergencePoint: Ix,
        val convergencePoint: Ix,
        val target: Ix,
    ) : OneToMany, ExprLike by E(parameters, listOf(divergencePoint, convergencePoint, target))

    // MAYBE: inline
    fun eval(
        get: (Ix) -> GCircle?,
    ): ExprResult {
        val g = { ix: Ix ->
            get(ix) ?: throw NullPointerException() // i miss MonadError
        } // MAYBE: rework using Raise<Error>.doStuff(): T
        val c = { ix: Ix ->
            get(ix) as? CircleOrLine ?: throw NullPointerException()
        }
        val p = { ix: Ix ->
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
                        is CircleBy2PointsAndSagittaRatio -> computeCircleBy2PointsAndSagittaRatio(
                            parameters,
                            p(chordStartPoint),
                            p(chordEndPoint),
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
    fun reIndex(
        reIndexer: (Ix) -> Ix,
    ): Expr =
        when (this) {
            is Incidence -> copy(
                carrier = reIndexer(carrier)
            )
            is CircleByCenterAndRadius -> copy(
                center = reIndexer(center),
                radiusPoint = reIndexer(radiusPoint),
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
                engine = reIndexer(engine),
            )
            is CircleBy2PointsAndSagittaRatio -> copy(
                chordStartPoint = reIndexer(chordStartPoint),
                chordEndPoint = reIndexer(chordEndPoint),
            )
            is Intersection -> copy(
                circle1 = reIndexer(circle1),
                circle2 = reIndexer(circle2),
            )
            is CircleInterpolation -> copy(
                startCircle = reIndexer(startCircle),
                endCircle = reIndexer(endCircle),
            )
            is CircleExtrapolation -> copy(
                startCircle = reIndexer(startCircle),
                endCircle = reIndexer(endCircle),
            )
            is LoxodromicMotion -> copy(
                divergencePoint = reIndexer(divergencePoint),
                convergencePoint = reIndexer(convergencePoint),
                target = reIndexer(target)
            )
        }
}
