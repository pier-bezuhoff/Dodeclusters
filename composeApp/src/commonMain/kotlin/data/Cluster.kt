package data

import androidx.compose.ui.graphics.Color

data class Cluster(
    val circles: List<Circle>,
    val fill: Boolean,
    val color: Color,
    val borderColor: Color?,
) {
}