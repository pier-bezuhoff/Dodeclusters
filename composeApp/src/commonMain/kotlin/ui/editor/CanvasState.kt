package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import domain.model.ChessboardPattern
import domain.settings.BlendModeType
import ui.theme.DodeclustersColors

// translation not included since it often changes continuously
/**
 * Discretely/unfrequently changing EditorCanvas parameters
 * @param[regionsOpacity] `[0; 1]` transparency of non-chessboard `regions`
 * @param[showDirectionArrows] which style to use when drawing regions: true = stroke, false = fill
 */
@Immutable
data class CanvasState(
    val canvasSize: IntSize = IntSize.Zero,
    val backgroundColor: Color? = null,
    val showCircles: Boolean = true,
    val showPhantomObjects: Boolean = false,
    val showDirectionArrows: Boolean = false,
    val regionsOpacity: Float = 1f,
    val regionsBlendModeType: BlendModeType = BlendModeType.SRC_OVER,
    val chessboardColor: Color = DodeclustersColors.deepAmethyst,
    val chessboardPattern: ChessboardPattern = ChessboardPattern.NONE,
) {
    val canvasHalfWidth: Float get() =
        canvasSize.width/2f
    val canvasHalfHeight: Float get() =
        canvasSize.height/2f
    val canvasCenter: Offset get() =
        Offset(canvasSize.width/2f, canvasSize.height/2f)
    val regionsBlendMode: BlendMode get() =
        regionsBlendModeType.blendMode
}
