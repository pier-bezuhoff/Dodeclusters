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

