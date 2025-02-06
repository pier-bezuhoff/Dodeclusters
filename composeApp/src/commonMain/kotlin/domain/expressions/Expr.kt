package domain.expressions

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import data.geometry.GCircle
import data.geometry.Point
import domain.Ix
import domain.expressions.Expr.CircleBy2PointsAndSagittaRatio
import domain.expressions.Expr.CircleBy3Points
import domain.expressions.Expr.CircleByCenterAndRadius
import domain.expressions.Expr.CircleByPencilAndPoint
import domain.expressions.Expr.CircleExtrapolation
import domain.expressions.Expr.CircleInterpolation
import domain.expressions.Expr.PointInterpolation
import domain.expressions.Expr.CircleInversion
import domain.expressions.Expr.Incidence
import domain.expressions.Expr.Intersection
import domain.expressions.Expr.LineBy2Points
import domain.expressions.Expr.BiInversion
import domain.expressions.Expr.LoxodromicMotion
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

// thinking about it, interface delegation might introduce some performance bloat
// MAYBE: accept the boilerplate and specify parameters and args as separate extension vals
//  e.g. Expr.parameters: Parameters = when (this) { ... }
//  and Expr.args: List<Ix> = when (this) { ... }
/**
 * Raw expression that can have several outputs:
 * either [OneToOne] or [OneToMany]
 *
 * Whenever the order of [args] doesn't matter, enforce index-increasing order
 * @property[parameters] Static parameters used in the expression, generally
 * a collection of numbers
 * @property[args] Indexed links to dynamic point/line/circle arguments
 * */
@Immutable
@Serializable
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
    @Serializable
    @SerialName("CircleBy3PerpendicularObjects")
    data class CircleBy3Points(
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
    data class LineBy2Points(
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
    data class Intersection(
        val circle1: Ix,
        val circle2: Ix,
    ) : OneToMany, ExprLike by E(Parameters.None, listOf(circle1, circle2))
    @Serializable
    @SerialName("CircleInterpolation")
    data class CircleInterpolation(
        override val parameters: InterpolationParameters,
        val startCircle: Ix,
        val endCircle: Ix,
    ) : OneToMany, ExprLike by E(parameters, listOf(startCircle, endCircle))
    @Serializable
    @SerialName("PointInterpolation")
    data class PointInterpolation(
        override val parameters: InterpolationParameters,
        val startPoint: Ix,
        val endPoint: Ix,
    ) : OneToMany, ExprLike by E(parameters, listOf(startPoint, endPoint))
    // MAYBE: completely replace CircleExtrapolation with BiInversion
    //  since it's more general
    @Serializable
    @SerialName("CircleExtrapolation")
    data class CircleExtrapolation(
        override val parameters: ExtrapolationParameters,
        val startCircle: Ix,
        val endCircle: Ix,
    ) : OneToMany, ExprLike by E(parameters, listOf(startCircle, endCircle))
    @Serializable
    @SerialName("BiInversion")
    data class BiInversion(
        override val parameters: BiInversionParameters,
        val engine1: Ix,
        val engine2: Ix,
        val target: Ix,
    ) : OneToMany, ExprLike by E(parameters, listOf(engine1, engine2, target))
    @Serializable
    @SerialName("LoxodromicMotion")
    data class LoxodromicMotion( // TODO: add backwards steps
        override val parameters: LoxodromicMotionParameters,
        val divergencePoint: Ix,
        val convergencePoint: Ix,
        val target: Ix,
    ) : OneToMany, ExprLike by E(parameters, listOf(divergencePoint, convergencePoint, target))
}

// performance-wise variations between:
// no-inline try-catch, inline try-catch
// no-inline return emptyList() and no-inline return
// are rather negligible
// BUT using direct access objects is much much faster (without downscale)
// with array+downscale there is also not much difference
// SO downscale is a bottleneck
// MAYBE: keep VM.objects downscaled and only upscale them for draw
inline fun Expr.eval(
    crossinline get: (Ix) -> GCircle?,
): ExprResult {
    val g = { ix: Ix ->
        get(ix) ?: throw NullPointerException() // i miss MonadError
    }
    val c = { ix: Ix ->
        get(ix) as? CircleOrLine ?: throw NullPointerException()
    }
    val p = { ix: Ix ->
        get(ix) as? Point ?: throw NullPointerException()
    }
    try {
        // idt it's worth to do normal polymorphism
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
            is PointInterpolation -> computePointInterpolation(
                parameters,
                p(startPoint),
                p(endPoint)
            )
            is CircleExtrapolation -> computeCircleExtrapolation(
                parameters,
                c(startCircle),
                c(endCircle)
            )
            is BiInversion -> computeBiInversion(
                parameters,
                g(engine1),
                g(engine2),
                g(target),
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

// this eval is 4 times to 15 times faster (but lacks downscale)
// BUT adding downscale completely cancels speed improvement
fun Expr._eval(objects: List<GCircle?>): ExprResult {
    return when (this) {
        // idt it's worth to polymorphism eval
        is OneToOne -> {
            val result = when (this) {
                is Incidence -> computeIncidence(
                    parameters,
                    objects[carrier] as? CircleOrLine ?: return emptyList(),
                )
                is CircleByCenterAndRadius -> computeCircleByCenterAndRadius(
                    objects[center] as? Point ?: return emptyList(),
                    objects[radiusPoint] as? Point ?: return emptyList(),
                )
                is CircleBy3Points -> computeCircleBy3Points(
                    objects[object1] ?: return emptyList(),
                    objects[object2] ?: return emptyList(),
                    objects[object3] ?: return emptyList(),
                )
                is CircleByPencilAndPoint -> computeCircleByPencilAndPoint(
                    objects[pencilObject1] ?: return emptyList(),
                    objects[pencilObject2] ?: return emptyList(),
                    objects[perpendicularObject] ?: return emptyList(),
                )
                is LineBy2Points -> computeLineBy2Points(
                    objects[object1] ?: return emptyList(),
                    objects[object2] ?: return emptyList(),
                )
                is CircleInversion -> computeCircleInversion(
                    objects[target] ?: return emptyList(),
                    objects[engine] ?: return emptyList(),
                )
                is CircleBy2PointsAndSagittaRatio -> computeCircleBy2PointsAndSagittaRatio(
                    parameters,
                    objects[chordStartPoint] as? Point ?: return emptyList(),
                    objects[chordEndPoint] as? Point ?: return emptyList(),
                )
            }
            listOf(result)
        }
        is Intersection -> computeIntersection(
            objects[circle1] as? CircleOrLine ?: return emptyList(),
            objects[circle2] as? CircleOrLine ?: return emptyList(),
        )
        is CircleInterpolation -> computeCircleInterpolation(
            parameters,
            objects[startCircle] as? CircleOrLine ?: return emptyList(),
            objects[endCircle] as? CircleOrLine ?: return emptyList(),
        )
        is PointInterpolation -> computePointInterpolation(
            parameters,
            objects[startPoint] as? Point ?: return emptyList(),
            objects[endPoint] as? Point ?: return emptyList(),
        )
        is CircleExtrapolation -> computeCircleExtrapolation(
            parameters,
            objects[startCircle] as? CircleOrLine ?: return emptyList(),
            objects[endCircle] as? CircleOrLine ?: return emptyList(),
        )
        is BiInversion -> computeBiInversion(
            parameters,
            objects[engine1] ?: return emptyList(),
            objects[engine2] ?: return emptyList(),
            objects[target] ?: return emptyList(),
        )
        is LoxodromicMotion -> computeLoxodromicMotion(
            parameters,
            objects[divergencePoint] as? Point ?: return emptyList(),
            objects[convergencePoint] as? Point ?: return emptyList(),
            objects[target] ?: return emptyList(),
        )
    }
}

inline fun Expr.reIndex(
    crossinline reIndexer: (Ix) -> Ix,
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
        is PointInterpolation -> copy(
            startPoint = reIndexer(startPoint),
            endPoint = reIndexer(endPoint),
        )
        is CircleExtrapolation -> copy(
            startCircle = reIndexer(startCircle),
            endCircle = reIndexer(endCircle),
        )
        is BiInversion -> copy(
            engine1 = reIndexer(engine1),
            engine2 = reIndexer(engine2),
            target = reIndexer(target)
        )
        is LoxodromicMotion -> copy(
            divergencePoint = reIndexer(divergencePoint),
            convergencePoint = reIndexer(convergencePoint),
            target = reIndexer(target)
        )
    }
