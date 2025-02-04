package data.geometry

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class CirclePencilType {
    /** Lines with 1 common point, circles with 2 common points */
    ELLIPTIC,
    /** Parallel lines, circles tangential to 1 common line at 1 common point */
    PARABOLIC,
    /** Concentric circles, circles perpendicular to every circle of a fixed (dual) elliptic pencil */
    HYPERBOLIC,
}