package domain.expressions

import kotlinx.serialization.Serializable

// numeric values used, from the dialog or somewhere else
@Serializable
sealed interface Parameters {
    @Serializable
    data object None : Parameters
}

/**
 * start = Left
 * end = Right
 */
@Serializable
data class ExtrapolationParameters(
    val nLeft: Int,
    val nRight: Int,
) : Parameters

@Serializable
data class InterpolationParameters(
    val nInterjacents: Int,
    val inBetween: Boolean,
) : Parameters

/**
 * @param[angle] total rotation angle in degrees
 * @param[dilation] total hyperbolic angle `ln(R/r)`
 * @param[nSteps] number of intermediate steps (0 = result only)
 * */
@Serializable
data class LoxodromicMotionParameters(
    val angle: Float,
    val dilation: Double,
    val nSteps: Int,
) : Parameters

