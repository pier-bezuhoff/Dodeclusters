package domain.expressions

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.GCircle
import core.geometry.Line
import core.geometry.Point
import domain.Ix
import domain.expressions.Expr.BiInversion
import domain.expressions.Expr.CircleBy2PointsAndSagittaRatio
import domain.expressions.Expr.CircleBy3Points
import domain.expressions.Expr.CircleByCenterAndRadius
import domain.expressions.Expr.CircleByPencilAndPoint
import domain.expressions.Expr.CircleExtrapolation
import domain.expressions.Expr.CircleInterpolation
import domain.expressions.Expr.CircleInversion
import domain.expressions.Expr.ConicBy5
import domain.expressions.Expr.ConicIntersection
import domain.expressions.Expr.Incidence
import domain.expressions.Expr.Intersection
import domain.expressions.Expr.LineBy2Points
import domain.expressions.Expr.LoxodromicMotion
import domain.expressions.Expr.OneToOne
import domain.expressions.Expr.PointInterpolation
import domain.expressions.Expr.PolarLine
import domain.expressions.Expr.PolarLineByCircleAndPoint
import domain.expressions.Expr.Pole
import domain.expressions.Expr.PoleByCircleAndLine
import domain.expressions.Expr.Rotation
import domain.never
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

typealias ConformalExprResult = List<GCircle?>

/**
 * Raw expression that can have one or several outputs:
 * either [OneToOne] or [OneToMany]
 * @property[parameters] Static parameters used in the expression, generally
 * a collection of numbers
 * @property[args] Indexed links to dynamic point/line/circle arguments
 */
@Immutable
@Serializable
sealed interface Expr {

    @Serializable
    sealed interface OneToOne : Expr
    @Serializable
    sealed interface OneToMany : Expr

    @Serializable
    sealed interface Conformal : Expr {
        // hand-made intersection types
        sealed interface OneToOne : Conformal, Expr.OneToOne
        sealed interface OneToMany : Conformal, Expr. OneToMany
    }
    @Serializable
    sealed interface Projective : Expr {
        sealed interface OneToOne : Projective, Expr.OneToOne
        sealed interface OneToMany : Projective, Expr. OneToMany
    }

    @Serializable
    sealed interface HasParameters : Expr {
        val parameters: Parameters
    }

    /**
     * [Expr] that applies transformation to [target] object [nSteps] times
     */
    @Serializable
    sealed interface TransformLike : Expr {
        val target: Ix
        val nSteps: Int
    }

    // NOTE: proper handling of dependent carrier requires computation of inverse function for any expr
    //  p' = f(Δ(f⁻¹(p)), where point p on dependent carrier f(<free>) moves to p' when <free> is affected by Δ

    // Conformal //

    @Serializable
    @SerialName("Intersection")
    data class Intersection(
        val circle1: Ix,
        val circle2: Ix,
    ) : Conformal.OneToMany

    @Serializable
    @SerialName("CircleByCenterAndRadius")
    data class CircleByCenterAndRadius(
        val center: Ix,
        val radiusPoint: Ix
    ) : Conformal.OneToOne

    @Serializable
    @SerialName("CircleBy3PerpendicularObjects")
    data class CircleBy3Points(
        val object1: Ix,
        val object2: Ix,
        val object3: Ix,
    ) : Conformal.OneToOne

    // TODO: replace with CircleBy3
    @Serializable
    @SerialName("LineBy2PerpendicularObjects")
    data class LineBy2Points(
        val object1: Ix,
        val object2: Ix,
    ) : Conformal.OneToOne

    @Serializable
    @SerialName("IncidentPoint")
    data class Incidence(
        val parameters: IncidenceParameters,
        val carrier: Ix,
    ) : Conformal.OneToOne, Projective.OneToOne

    @Serializable
    @SerialName("CircleBy2ObjectsFromItsPencilAndPerpendicularObject")
    data class CircleByPencilAndPoint(
        val pencilObject1: Ix,
        val pencilObject2: Ix,
        val perpendicularObject: Ix,
    ) : Conformal.OneToOne

    // TODO: replace with PolarLine
    @Serializable
    @SerialName("PolarLineByCircleAndPoint")
    data class PolarLineByCircleAndPoint( // these 2 are eh
        val circle: Ix,
        val point: Ix,
    ) : Conformal.OneToOne

    // TODO: replace with Pole
    @Serializable
    @SerialName("PoleByCircleAndLine")
    data class PoleByCircleAndLine(
        val circle: Ix,
        val line: Ix,
    ) : Conformal.OneToOne

    @Serializable
    @SerialName("CircleInversion")
    data class CircleInversion(
        override val target: Ix,
        val engine: Ix,
    ) : Conformal.OneToOne, TransformLike {
        @Transient
        override val nSteps: Int = 1
    }

    @Serializable
    @SerialName("CircleBy2PointsAndSagittaRatio")
    data class CircleBy2PointsAndSagittaRatio(
        override val parameters: SagittaRatioParameters,
        val chordStartPoint: Ix,
        val chordEndPoint: Ix,
    ) : Conformal.OneToOne, HasParameters

    @Serializable
    @SerialName("CircleInterpolation")
    data class CircleInterpolation(
        override val parameters: InterpolationParameters,
        val startCircle: Ix,
        val endCircle: Ix,
    ) : Conformal.OneToMany, HasParameters

    @Serializable
    @SerialName("PointInterpolation")
    data class PointInterpolation(
        override val parameters: InterpolationParameters,
        val startPoint: Ix,
        val endPoint: Ix,
    ) : Conformal.OneToMany, Projective.OneToMany, HasParameters

    // NOTE: deprecated, since BiInversion is more general
    @Serializable
    @SerialName("CircleExtrapolation")
    data class CircleExtrapolation(
        override val parameters: ExtrapolationParameters,
        val startCircle: Ix,
        val endCircle: Ix,
    ) : Conformal.OneToMany, HasParameters

    @Serializable
    @SerialName("Rotation")
    data class Rotation(
        override val parameters: RotationParameters,
        val pivot: Ix,
        override val target: Ix,
    ) : Conformal.OneToMany, Projective.OneToMany, HasParameters, TransformLike {
        @Transient
        override val nSteps: Int = parameters.nSteps
    }

    @Serializable
    @SerialName("BiInversion")
    data class BiInversion( // theoretically could generalize to consecutive polarity of 2 conics
        override val parameters: BiInversionParameters,
        val engine1: Ix,
        val engine2: Ix,
        override val target: Ix,
    ) : Conformal.OneToMany, HasParameters, TransformLike {
        @Transient
        override val nSteps: Int = parameters.nSteps
    }

    /**
     * @param[otherHalfStart] Index (if any) of the complementary spiral trajectory start,
     * the one going backwards (with reversed convergence-divergence points).
     * Stored to alter their parameters simultaneously for better UX.
     * It is expected to point to an object with the complementary [Expr].
     * Can get stale (eg deleted or null-ed).
     */
    @Serializable
    @SerialName("LoxodromicMotion")
    data class LoxodromicMotion( // MAYBE: add backwards steps
        override val parameters: LoxodromicMotionParameters,
        val divergencePoint: Ix,
        val convergencePoint: Ix,
        override val target: Ix,
        val otherHalfStart: Ix? = null,
    ) : Conformal.OneToMany, HasParameters, TransformLike {
        @Transient
        override val nSteps: Int = parameters.nSteps
    }

    // Projective

    @Serializable
    @SerialName("ConicIntersection")
    data class ConicIntersection( // unlike conformal intersection, this can have up to 4 points
        val conic1: Ix,
        val conic2: Ix,
    ) : Projective.OneToMany

    @Serializable
    @SerialName("ConicBy5")
    data class ConicBy5(
        val conic1: Ix,
        val conic2: Ix,
        val conic3: Ix,
        val conic4: Ix,
        val conic5: Ix,
    ) : Projective.OneToOne

    @Serializable
    @SerialName("PolarLine")
    data class PolarLine(
        val conic: Ix,
        val pole: Ix,
    ) : Projective.OneToOne, Conformal.OneToOne

    @Serializable
    @SerialName("Pole")
    data class Pole(
        val conic: Ix,
        val polarLine: Ix,
    ) : Projective.OneToOne, Conformal.OneToOne

    // each arg can in turn be computed as an expression, making up Forest-like data structure
    val args: List<Ix> get() =
        when (this) {
            is Intersection -> listOf(circle1, circle2)
            is CircleByCenterAndRadius -> listOf(center, radiusPoint)
            is CircleBy3Points -> listOf(object1, object2, object3)
            is LineBy2Points -> listOf(object1, object2)
            is Incidence -> listOf(carrier)
            is CircleByPencilAndPoint -> listOf(pencilObject1, pencilObject2, perpendicularObject)
            is PolarLineByCircleAndPoint -> listOf(circle, point)
            is PoleByCircleAndLine -> listOf(circle, line)
            is CircleInversion -> listOf(target, engine)
            is CircleBy2PointsAndSagittaRatio -> listOf(chordStartPoint, chordEndPoint)
            is CircleInterpolation -> listOf(startCircle, endCircle)
            is PointInterpolation -> listOf(startPoint, endPoint)
            is CircleExtrapolation -> listOf(startCircle, endCircle)
            is Rotation -> listOf(pivot, target)
            is BiInversion -> listOf(engine1, engine2, target)
            is LoxodromicMotion -> listOf(divergencePoint, convergencePoint, target)
            is ConicIntersection -> listOf(conic1, conic2)
            is ConicBy5 -> listOf(conic1, conic2, conic3, conic4, conic5)
            is PolarLine -> listOf(conic, pole)
            is Pole -> listOf(conic, polarLine)
        }
}

// performance-wise variations between:
// no-inline try-catch, inline try-catch
// no-inline return emptyList() and no-inline return
// are rather negligible
// BUT using direct access objects is much much faster (without downscale)
// with array+downscale there is also not much difference
// SO downscale is a bottleneck
// MAYBE: keep VM.objects downscaled and only upscale them for draw
inline fun Expr.Conformal.eval(
    crossinline get: (Ix) -> GCircle?,
): ConformalExprResult {
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
                    is LineBy2Points -> computeLineBy2Points(
                        g(object1),
                        g(object2)
                    )
                    is CircleByPencilAndPoint -> computeCircleByPencilAndPoint(
                        g(pencilObject1),
                        g(pencilObject2),
                        g(perpendicularObject),
                    )
                    is PolarLineByCircleAndPoint -> computePolarLine(
                        c(circle),
                        p(point),
                    )
                    is PoleByCircleAndLine -> computePole(
                        get(circle) as? Circle ?: throw NullPointerException(),
                        get(line) as? Line ?: throw NullPointerException(),
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
                    is PolarLine -> TODO()
                    is Pole -> TODO()
                    else -> never(this.toString())
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
            is Rotation -> computeRotation(
                parameters,
                p(pivot),
                g(target),
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
    } catch (_: Exception) { // catches NullExceptions AND unforeseen cases (safety measure)
        return emptyList()
    }
}

// this eval is 4 times to 15 times faster (but lacks downscale)
// BUT adding downscale completely cancels speed improvement
fun Expr.Conformal.eval(objects: List<GCircle?>): ConformalExprResult {
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
                is PolarLineByCircleAndPoint -> computePolarLine(
                    objects[circle] as? CircleOrLine ?: return emptyList(),
                    objects[point] as? Point ?: return emptyList(),
                )
                is PoleByCircleAndLine -> computePole(
                    objects[circle] as? Circle ?: return emptyList(),
                    objects[line] as? Line ?: return emptyList(),
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
                is PolarLine -> TODO()
                is Pole -> TODO()
                else -> never(this.toString())
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
        is Rotation -> computeRotation(
            parameters,
            objects[pivot] as? Point ?: return emptyList(),
            objects[target] ?: return emptyList(),
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

// cant reify EXPR cuz its generic in Expressions
@Suppress("UNCHECKED_CAST")
inline fun <EXPR : Expr> EXPR.reIndex(
    crossinline reIndexer: (Ix) -> Ix,
): EXPR =
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
        is PolarLineByCircleAndPoint -> copy(
            circle = reIndexer(circle),
            point = reIndexer(point),
        )
        is PoleByCircleAndLine -> copy(
            circle = reIndexer(circle),
            line = reIndexer(line),
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
        is Rotation -> copy(
            pivot = reIndexer(pivot),
            target = reIndexer(target),
        )
        is BiInversion -> copy(
            engine1 = reIndexer(engine1),
            engine2 = reIndexer(engine2),
            target = reIndexer(target),
        )
        is LoxodromicMotion -> copy(
            divergencePoint = reIndexer(divergencePoint),
            convergencePoint = reIndexer(convergencePoint),
            target = reIndexer(target),
            otherHalfStart = otherHalfStart?.let { reIndexer(it) },
        )
        is ConicBy5 -> copy(
            conic1 = reIndexer(conic1),
            conic2 = reIndexer(conic2),
            conic3 = reIndexer(conic3),
            conic4 = reIndexer(conic4),
            conic5 = reIndexer(conic5),
        )
        is ConicIntersection -> copy(
            conic1 = reIndexer(conic1),
            conic2 = reIndexer(conic2),
        )
        is PolarLine -> copy(
            conic = reIndexer(conic),
            pole = reIndexer(pole)
        )
        is Pole -> copy(
            conic = reIndexer(conic),
            polarLine = reIndexer(polarLine),
        )
    } as EXPR

/** Copies case-by-case by hard-__casting__ [newParameters] as an appropriate type for `this` */
inline fun <reified EXPR : Expr> EXPR.copyWithNewParameters(
    newParameters: Parameters
): EXPR =
    when (this) {
        is Incidence -> copy(
            parameters = newParameters as IncidenceParameters
        )
        is CircleByCenterAndRadius -> this
        is CircleBy3Points -> this
        is CircleByPencilAndPoint -> this
        is LineBy2Points -> this
        is PolarLineByCircleAndPoint -> this
        is PoleByCircleAndLine -> this
        is CircleInversion -> this
        is CircleBy2PointsAndSagittaRatio -> copy(
            parameters = newParameters as SagittaRatioParameters
        )
        is Intersection -> this
        is CircleInterpolation -> copy(
            parameters = newParameters as InterpolationParameters
        )
        is PointInterpolation -> copy(
            parameters = newParameters as InterpolationParameters
        )
        is CircleExtrapolation -> copy(
            parameters = newParameters as ExtrapolationParameters
        )
        is Rotation -> copy(
            parameters = newParameters as RotationParameters
        )
        is BiInversion -> copy(
            parameters = newParameters as BiInversionParameters
        )
        is LoxodromicMotion -> copy(
            parameters = newParameters as LoxodromicMotionParameters
        )
        is ConicBy5 -> this
        is ConicIntersection -> this
        is PolarLine -> this
        is Pole -> this
    } as EXPR