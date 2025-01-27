package domain.expressions

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// numeric values used, from the dialog or somewhere else
@Serializable
@Immutable
sealed interface Parameters {
    @Serializable
    @Immutable
    data object None : Parameters
}

@Serializable
@Immutable
data class IncidenceParameters(
    val order: Double
) : Parameters

/**
 * start = Left
 * end = Right
 */
@Serializable
@Immutable
data class ExtrapolationParameters(
    val nLeft: Int,
    val nRight: Int,
) : Parameters

@Serializable
@Immutable
data class InterpolationParameters(
    val nInterjacents: Int,
    /** Soft-deprecated.
     * Unused except for informing change to DefaultInterpolationParameter and
     * for backwards-compatibility */
    val inBetween: Boolean,
    /** Direction-aware; `true` indicates non-natural n-sector choice */
    val complementary: Boolean = inBetween // defaults to inBetween bc of o|o case
) : Parameters

/**
 * @param[angle] total rotation angle in degrees
 * @param[dilation] total hyperbolic angle `ln(R/r)`
 * @param[nSteps] number of intermediate steps (0 = result only)
 * */
@Serializable
@Immutable
data class LoxodromicMotionParameters(
    val angle: Float,
    val dilation: Double,
    val nSteps: Int,
) : Parameters

@Serializable
@Immutable
data class SagittaRatioParameters(
    val sagittaRatio: Double
) : Parameters

