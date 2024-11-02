package domain.io

import kotlinx.serialization.Serializable

@Serializable
/** Shapes to draw instead of circles */
enum class Shape {
    CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR;
}