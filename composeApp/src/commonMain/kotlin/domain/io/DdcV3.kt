package domain.io

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import domain.ColorAsCss
import domain.Ix
import domain.cluster.Constellation
import domain.cluster.LogicalRegion
import domain.expressions.ObjectConstruct
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
    val objects: Map<Ix, Token.Object>, // this disallows missing "objects" field, and produces exception on DdcV3
    val arcPaths: List<Token.ArcPath> = DEFAULT_ARC_PATHS,
) {
    @Serializable
    @Immutable
    sealed interface Token {
        @Serializable
        @SerialName("ObjectToken")
        data class Object(
            val construct: ObjectConstruct,
            val color: ColorAsCss? = null,
        ) : Token
        @Serializable
        @SerialName("ArcPathToken")
        data class ArcPath( // TODO: replace with better suited ArcPath format
            val arcPath: LogicalRegion,
            // intersections, ordered directed circles, fill&border colors, filled or nay
        ) : Token
    }

    fun toConstellation(): Constellation {
        require(objects.keys == (0 until objects.size).toSet()) { "Bad object indices" }
        val objs: List<Token.Object> = objects.entries.sortedBy { it.key }.map { it.value }
        return Constellation(
            objects = objs.map { it.construct },
            parts = arcPaths.map { it.arcPath },
            objectColors = objects.entries
                .mapNotNull { (i, token) ->
                    if (token.color == null) null
                    else i to token.color
                }.toMap()
        )
    }

    companion object {
        fun from(constellation: Constellation): DdcV3 =
            DdcV3(
                objects = constellation.objects.mapIndexed { ix, construct ->
                    ix to Token.Object(
                        construct = construct,
                        color = constellation.objectColors[ix],
                    )
                }.toMap(),
                arcPaths = constellation.parts.map { Token.ArcPath(it) }
            )

        const val HEADER = "ddc v4, generated by Dodeclusters"
        const val DEFAULT_NAME = "constellation"
        const val DEFAULT_EXTENSION = "yml"
        val DEFAULT_BACKGROUND_COLOR = Color.White
        val DEFAULT_BEST_CENTER_X: Float? = null
        val DEFAULT_BEST_CENTER_Y: Float? = null
        const val DEFAULT_CHESSBOARD_PATTERN = false
        const val DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED = true
        val DEFAULT_ARC_PATHS = emptyList<Token.ArcPath>()
    }
}