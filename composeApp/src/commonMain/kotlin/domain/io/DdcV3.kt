package domain.io

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import domain.ColorAsCss
import domain.Ix
import domain.model.LogicalRegion
import domain.cluster._Constellation
import domain.expressions.deprecated._CircleConstruct
import domain.expressions.deprecated._PointConstruct
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class DdcV3(
    val name: String = DEFAULT_NAME,
    val backgroundColor: ColorAsCss = DEFAULT_BACKGROUND_COLOR,
    val bestCenterX: Float? = DEFAULT_BEST_CENTER_X,
    val bestCenterY: Float? = DEFAULT_BEST_CENTER_Y,
    /** indicates using all-circle chessboard pattern coloring, ignoring parts */
    val chessboardPattern: Boolean = DEFAULT_CHESSBOARD_PATTERN,
    /** one of two possible starting chessboard phases, true=colored=bg filled with color */
    val chessboardPatternStartsColored: Boolean = DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED,
    // using Map<Int, _> instead of list to force yaml to use numbered list
    val points: Map<Ix, Token.Point> = DEFAULT_POINTS,
    val circles: Map<Ix, Token.Circle>, // this disallows missing "circles" field, and produces exception on DdcV3
    val arcPaths: List<Token.ArcPath> = DEFAULT_ARC_PATHS,
) {
    @Serializable
    @Immutable
    sealed interface Token {
        @Serializable
        @SerialName("PointToken")
        data class Point(
            @SerialName("Point") // otherwise this duplicates "point" from PointConstruct.Concrete(point: Point)
            val point: _PointConstruct,
        ) : Token
        @Serializable
        @SerialName("CircleOrLineToken")
        data class Circle(
            @SerialName("CircleOrLine")
            val circle: _CircleConstruct,
            val borderColor: ColorAsCss? = null,
        ) : Token
        @Serializable
        @SerialName("ArcPathToken")
        data class ArcPath( // TODO: replace with better suited ArcPath format
            val arcPath: LogicalRegion,
            // intersections, ordered directed circles, fill&border colors, filled or nay
        ) : Token
    }

    fun toConstellation(): _Constellation =
        _Constellation(
            points = points.entries
                .sortedBy { (i, _) -> i }
                .map { (_, token) ->
                    token.point
                },
            circles = circles.entries
                .sortedBy { (i, _) -> i }
                .map { (_, token) ->
                    token.circle
                },
            parts = arcPaths.map { it.arcPath },
            circleColors = circles.entries
                .mapNotNull { (i, token) ->
                    if (token.borderColor == null) null
                    else i to token.borderColor
                }.toMap()
        )

    companion object {
        fun from(constellation: _Constellation): DdcV3 =
            DdcV3(
                points = constellation.points.mapIndexed { i, p -> i to Token.Point(p) }.toMap(),
                circles = constellation.circles.mapIndexed { i, c ->
                    i to Token.Circle(c, borderColor = constellation.circleColors[i])
                }.toMap(),
                arcPaths = constellation.parts.map { Token.ArcPath(it) }
            )

        const val DEFAULT_NAME = "constellation"
        const val DEFAULT_EXTENSION = "yml"
        val DEFAULT_BACKGROUND_COLOR = Color.White
        val DEFAULT_BEST_CENTER_X: Float? = null
        val DEFAULT_BEST_CENTER_Y: Float? = null
        const val DEFAULT_CHESSBOARD_PATTERN = false
        const val DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED = true
        val DEFAULT_POINTS = emptyMap<Int, Token.Point>()
        val DEFAULT_ARC_PATHS = emptyList<Token.ArcPath>()
    }
}