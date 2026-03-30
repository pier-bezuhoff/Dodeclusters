package domain.io

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import domain.ColorAsCss
import domain.Ix
import domain.expressions.ArcPath
import domain.model.LogicalRegion
import domain.expressions.ConformalExprOutput
import domain.expressions.ExprOutput
import domain.model.SaveState
import domain.model.ChessboardPattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// unsolved: smoother evolution of expressions/parameters
// MAYBE: collapse contiguously indexed Expression.OneOf outputs
@Immutable
@Serializable
data class DdcV5(
    val name: String = DEFAULT_NAME,
    val backgroundColor: ColorAsCss? = DEFAULT_BACKGROUND_COLOR,
    val bestCenterX: Float? = DEFAULT_BEST_CENTER_X,
    val bestCenterY: Float? = DEFAULT_BEST_CENTER_Y,
    /** indicates using all-circle chessboard pattern coloring */
    val chessboardPattern: Boolean = DEFAULT_CHESSBOARD_PATTERN,
    /** one of two possible starting chessboard phases, true=colored=bg filled with color */
    val chessboardPatternStartsColored: Boolean = DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED,
    /** used with chessboard pattern, 2 colors are used: [chessboardColor] and [backgroundColor] */
    val chessboardColor: ColorAsCss? = DEFAULT_CHESSBOARD_COLOR,
    /** we use [Map]<Ix, _> instead of [List] to force use of numbered list in YAML,
     * since we use [mutableMapOf] underneath, the index order is preserved */
    val objects: Map<Ix, ObjectToken>,
    val regions: List<LogicalRegion> = emptyList(),
) {
    @Immutable
    @Serializable
    sealed interface ObjectToken

    @Immutable
    @Serializable
    @SerialName("GCircleToken")
    data class GCircleToken(
        // discriminates DdcV4.Token.Object
        val representation: GCircle?,
        val expression: ConformalExprOutput? = null,
        val label: String? = null,
        val color: ColorAsCss? = null,
        val isPhantom: Boolean = false,
    ) : ObjectToken

    @Immutable
    @Serializable
    @SerialName("ArcPathToken")
    data class ArcPathToken(
        val arcPath: ArcPath,
        val borderColor: ColorAsCss? = null,
        val fillColor: ColorAsCss? = null,
    ) : ObjectToken

    init {
        require(objects.keys == (0 until objects.size).toSet()) { "Bad object indices" }
    }

    fun toSaveState(): SaveState {
        val objs = mutableListOf<GCircle?>()
        val expressions = mutableMapOf<Ix, ConformalExprOutput?>()
        val borderColors = mutableMapOf<Ix, Color>()
        val fillColors = mutableMapOf<Ix, Color>()
        val labels = mutableMapOf<Ix, String>()
        val phantoms = mutableSetOf<Ix>()
        objects.entries
            .sortedBy { (ix, _) -> ix }
            .forEach { (ix, token) ->
                when (token) {
                    is GCircleToken -> {
                        objs.add(token.representation)
                        expressions[ix] = token.expression
                        if (token.color != null)
                            borderColors[ix] = token.color
                        if (token.label != null)
                            labels[ix] = token.label
                        if (token.isPhantom)
                            phantoms.add(ix)
                    }
                    is ArcPathToken -> {
                        objs.add(null) // concrete path needs to be reEval'd given full context
                        expressions[ix] = ExprOutput.Just(token.arcPath)
                        if (token.borderColor != null)
                            borderColors[ix] = token.borderColor
                        if (token.fillColor != null)
                            fillColors[ix] = token.fillColor
                    }
                }
            }
        return SaveState(
            objects = objs,
            expressions = expressions,
            borderColors = borderColors,
            fillColors = fillColors,
            labels = labels,
            regions = regions,
            backgroundColor = backgroundColor,
            chessboardPattern =
                if (chessboardPattern) {
                    if (chessboardPatternStartsColored)
                        ChessboardPattern.STARTS_COLORED
                    else ChessboardPattern.STARTS_TRANSPARENT
                } else ChessboardPattern.NONE
            ,
            chessboardColor = chessboardColor,
            phantoms = phantoms,
            center =
                if (bestCenterX != null && bestCenterY != null)
                    Offset(bestCenterX, bestCenterY)
                else Offset.Unspecified
            ,
        )
    }

    companion object {
        const val DEFAULT_NAME = "cluster"
        const val DEFAULT_EXTENSION = "yml"
        val DEFAULT_BACKGROUND_COLOR: Color? = null
        val DEFAULT_BEST_CENTER_X: Float? = null
        val DEFAULT_BEST_CENTER_Y: Float? = null
        const val DEFAULT_CHESSBOARD_PATTERN = false
        const val DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED = true
        val DEFAULT_CHESSBOARD_COLOR: Color? = null

        val YAML = Yaml(
            configuration = YamlConfiguration(
                encodeDefaults = false,
                strictMode = false,
                polymorphismStyle = PolymorphismStyle.Property
            )
        )

        fun fromSaveState(state: SaveState): DdcV5 {
            return with (state.compressFreeIndices()) {
                DdcV5(
                    backgroundColor = backgroundColor,
                    bestCenterX = if (center.isSpecified) center.x else null,
                    bestCenterY = if (center.isSpecified) center.y else null,
                    chessboardPattern = chessboardPattern != ChessboardPattern.NONE,
                    chessboardPatternStartsColored =
                        chessboardPattern != ChessboardPattern.STARTS_TRANSPARENT,
                    chessboardColor = chessboardColor,
                    objects = objects.withIndex().associate { (ix, obj) ->
                        ix to when (obj) {
                            is ConcreteArcPath -> ArcPathToken(
                                arcPath = expressions[ix]?.expr as ArcPath,
                                borderColor = this.borderColors[ix],
                                fillColor = this.fillColors[ix],
                            )
                            else -> GCircleToken(
                                representation = obj as? GCircle,
                                expression = expressions[ix],
                                label = this.labels[ix],
                                color = this.borderColors[ix],
                                isPhantom = ix in phantoms,
                            )
                        }
                    },
                    regions = regions,
                )
            }
        }
    }
}
