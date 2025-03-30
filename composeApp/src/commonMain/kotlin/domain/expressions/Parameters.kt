package domain.expressions

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// numeric values used, from the dialog or somewhere else
@Immutable
@Serializable
sealed interface Parameters {
    @Immutable
    @Serializable
    data object None : Parameters
}

@Immutable
@Serializable
data class IncidenceParameters(
    val order: Double
) : Parameters

/**
 * start = Left
 * end = Right
 */
@Immutable
@Serializable
data class ExtrapolationParameters(
    val nLeft: Int,
    val nRight: Int,
) : Parameters

/**
 * @param[nInterjacents] Number of new objects to generate in between
 * @param[inBetween] Unused, except for informing change to DefaultInterpolationParameter and
 * for backwards-compatibility
 * @param[complementary] Direction-aware; `true` indicates non-natural n-sector choice
 */
@Immutable
@Serializable
data class InterpolationParameters(
    val nInterjacents: Int,
    val inBetween: Boolean,
    val complementary: Boolean = inBetween // defaults to inBetween bc of o|o case
) : Parameters

/**
 * @param[angle] total rotation angle in degrees
 * @param[dilation] total hyperbolic angle `ln(R/r)`
 * @param[nSteps] number of intermediate steps (0 = result only)
 * */
@Immutable
@Serializable
data class LoxodromicMotionParameters(
    val angle: Float,
    val dilation: Double,
    val nSteps: Int,
) : Parameters {
    val anglePerStep: Float get() =
        angle / (nSteps + 1)
    val dilationPerStep: Double get() =
        dilation / (nSteps + 1)
    val nTotalSteps: Int get() =
        nSteps + 1
    companion object {
        fun fromDifferential(
            anglePerStep: Float,
            dilationPerStep: Double,
            nTotalSteps: Int
        ): LoxodromicMotionParameters =
            LoxodromicMotionParameters(
                anglePerStep * nTotalSteps,
                dilationPerStep * nTotalSteps,
                nTotalSteps - 1,
            )
    }
}

@Immutable
@Serializable
data class SagittaRatioParameters(
    val sagittaRatio: Double
) : Parameters

/**
 * @param[speed] Multiply inversive distance between engines by [speed] before applications.
 * Can be negative.
 * @param[nSteps] Number of times to apply the composition of inversions
 * @param[reverseSecondEngine] Not co-directed (anti-parallel) engines may lead to
 * non-intuitive behaviour
 */
@Immutable
@Serializable
data class BiInversionParameters(
    val speed: Double,
    val nSteps: Int,
    val reverseSecondEngine: Boolean
) : Parameters

/**
 * @param[angle] CCW rotation angle in degrees per each step [0; 360)
 */
@Immutable
@Serializable
data class RotationParameters(
    val angle: Float,
    val nSteps: Int,
) : Parameters
