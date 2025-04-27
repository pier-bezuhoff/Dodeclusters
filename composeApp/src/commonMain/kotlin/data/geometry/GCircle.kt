package data.geometry

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/** A circle, line, imaginary circle or point (aka CLIP) */
@Immutable
sealed interface GCircle {
    fun translated(vector: Offset): GCircle
    fun translated(dx: Double, dy: Double): GCircle
    fun scaled(focusX: Double, focusY: Double, zoom: Double): GCircle
    fun rotated(focusX: Double, focusY: Double, angleInRadians: Double): GCircle
    /**
     * combined `T;S;R` (= `T;R;S`) transformation
     * @param[rotationAngle] CCW in degrees
     */
    fun transformed(
        translation: Offset,
        focus: Offset = Offset.Unspecified,
        zoom: Float = 1f,
        rotationAngle: Float = 0f
    ): GCircle
}

/** Optimized version of [GCircle.scaled]`(0.0, 0.0, zoom)` */
@Suppress("NOTHING_TO_INLINE")
inline fun GCircle.scaled00(zoom: Double): GCircle =
    when (this) {
        is Circle -> copy(x = zoom * x, y = zoom * y, radius = zoom * radius)
        is Line -> copy(c = zoom * c)
        is Point -> copy(x = zoom * x, y = zoom * y)
        is ImaginaryCircle -> copy(x = zoom * x, y = zoom * y, radius = zoom * radius)
    }

/** Optimized version of [CircleOrLine.scaled]`(0.0, 0.0, zoom)` */
@Suppress("NOTHING_TO_INLINE")
inline fun CircleOrLine.scaled00(zoom: Double): CircleOrLine =
    when (this) {
        is Circle -> copy(x = zoom * x, y = zoom * y, radius = zoom * radius)
        is Line -> copy(c = zoom * c)
    }

/** Optimized version of [Point.scaled]`(0.0, 0.0, zoom)` */
@Suppress("NOTHING_TO_INLINE")
inline fun Point.scaled00(zoom: Double): Point =
    copy(x = zoom * x, y = zoom * y)

