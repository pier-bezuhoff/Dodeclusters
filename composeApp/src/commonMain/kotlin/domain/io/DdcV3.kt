package domain.io

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import domain.cluster.Cluster
import domain.ColorCssSerializer
import domain.cluster.ClusterPart
import domain.cluster.Constellation
import domain.expressions.CircleConstruct
import domain.expressions.PointConstruct
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class DdcV3(
    val name: String = DEFAULT_NAME,
    @Serializable(ColorCssSerializer::class)
    val backgroundColor: Color = DEFAULT_BACKGROUND_COLOR,
    val bestCenterX: Float? = DEFAULT_BEST_CENTER_X,
    val bestCenterY: Float? = DEFAULT_BEST_CENTER_Y,
    /** indicates using all-circle chessboard pattern coloring, ignoring parts */
    val chessboardPattern: Boolean = DEFAULT_CHESSBOARD_PATTERN,
    /** one of two possible starting chessboard phases, true=colored=bg filled with color */
    val chessboardPatternStartsColored: Boolean = DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED,
    // using Map<Int, _> instead of list to force yaml to use numbered list
    val points: Map<Int, Token.Point> = DEFAULT_POINTS,
    val circles: Map<Int, Token.Circle> = DEFAULT_CIRCLES,
    val arcPaths: List<Token.ArcPath> = DEFAULT_ARC_PATHS,
) {
    @Serializable
    @Immutable
    sealed interface Token {
        @Serializable
        data class Point(
            val point: PointConstruct,
        ) : Token
        @Serializable
        data class Circle(
            val circle: CircleConstruct,
            @Serializable(ColorCssSerializer::class)
            val borderColor: Color? = null,
        ) : Token
        @Serializable
        data class ArcPath( // TODO: replace with better suited ArcPath format
            val arcPath: ClusterPart,
            // intersections, ordered directed circles, fill&border colors, filled or nay
        ) : Token
    }

    companion object {
        fun from(constellation: Constellation): DdcV3 =
            TODO()

        const val DEFAULT_NAME = "constellation"
        const val DEFAULT_EXTENSION = "yml"
        val DEFAULT_BACKGROUND_COLOR = Color.White
        val DEFAULT_BEST_CENTER_X: Float? = null
        val DEFAULT_BEST_CENTER_Y: Float? = null
        const val DEFAULT_CHESSBOARD_PATTERN = false
        const val DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED = true
        val DEFAULT_POINTS = emptyMap<Int, Token.Point>()
        val DEFAULT_CIRCLES = emptyMap<Int, Token.Circle>()
        val DEFAULT_ARC_PATHS = emptyList<Token.ArcPath>()
    }
}