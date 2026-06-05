package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

private val DOTTED_PATH_EFFECT =
    PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

@Immutable
data class CustomStyles(
    val strokeWidth: Float,
    val circleStroke: Stroke = Stroke(width = strokeWidth),
    val thiccCircleStroke: Stroke = Stroke(width = 2 * strokeWidth),
    val dottedStroke: Stroke = Stroke(
        width = strokeWidth,
        pathEffect = DOTTED_PATH_EFFECT,
    ),
    val thiccDottedStroke: Stroke = Stroke(
        width = 2 * strokeWidth,
        pathEffect = DOTTED_PATH_EFFECT,
    ),
    val pathStroke: Stroke = Stroke(width = 1f * strokeWidth),
    val thiccPathStroke: Stroke = Stroke(
        width = 2f * strokeWidth,
        join = StrokeJoin.Round,
    ),
    val handleRadius: Float = 8f, // with (density) { 8.dp.toPx() }
    val pointRadius: Float = 2.5f * strokeWidth,
    val arcMiddlePointRadius: Float = pointRadius,
    val iconDim: Float,
) {
    companion object {
        fun fromDensity(density: Density): CustomStyles = CustomStyles(
            strokeWidth = with (density) { 2.dp.toPx() },
            iconDim = with (density) { 24.dp.toPx() },
        )
    }
}

val MaterialTheme.customStyles: CustomStyles
    @Composable
//    @ReadOnlyComposable
    get() {
        val density = LocalDensity.current
        val customStyles = remember(density) {
            CustomStyles.fromDensity(density)
        }
        return customStyles
    }
