package core.geometry.projective

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** A point (x:y:w) on a 2D projective plane. w=0 represents the points on infinity */
@Immutable
@Serializable
data class PPoint(
    val x: Double,
    val y: Double,
    val w: Double,
) {
    init {
        require(
            x.isFinite() && y.isFinite() && w.isFinite()
        ) { "Invalid Point($x, $y, $w)" }
    }
}