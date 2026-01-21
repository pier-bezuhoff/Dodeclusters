package core.geometry.projective

import androidx.compose.runtime.Immutable
import core.geometry.EPSILON
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** A point [x]:[y]:w on a 2D projective plane. [isFinite]=`true` means `w = 1`, otherwise `w = 0` */
@Immutable
@Serializable
data class PPoint(
    val x: Double,
    val y: Double,
    val isFinite: Boolean = true,
) {
    constructor(x: Double, y: Double, w: Double) : this(
        if (abs(w) < EPSILON) x else x/w,
        if (abs(w) < EPSILON) y else y/w,
        abs(w) >= EPSILON,
    )

    inline val w: Int get() =
        if (isFinite) 1 else 0

    init {
        require(
            x.isFinite() && y.isFinite() && (isFinite || x != 0.0 || y != 0.0)
        ) { "Invalid Point($x, $y, isFinite = $isFinite)" }
    }
}