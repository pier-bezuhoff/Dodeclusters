package domain.io

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import core.geometry.GCircle
import domain.ColorAsCss
import domain.Ix
import domain.model.ArcPath
import domain.model.LogicalRegion
import domain.expressions.ConformalExprOutput
import domain.model.SaveState
import domain.model.ChessboardPattern
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
    val arcPaths: List<ArcPath> = emptyList(),
) {
    @Immutable
    @Serializable
    data class ObjectToken(
        // discriminates DdcV4.Token.Object
        val representation: GCircle?,
        val expression: ConformalExprOutput? = null,
        val label: String? = null,
        val color: ColorAsCss? = null,
        val isPhantom: Boolean = false,
    )

    init {
        require(objects.keys == (0 until objects.size).toSet()) { "Bad object indices" }
    }

    fun toSaveState(): SaveState {
        val objs = mutableListOf<GCircle?>()
        val expressions = mutableMapOf<Ix, ConformalExprOutput?>()
        val objectColors = mutableMapOf<Ix, Color>()
        val objectLabels = mutableMapOf<Ix, String>()
        val phantoms = mutableSetOf<Ix>()
        objects.entries
            .sortedBy { (ix, _) -> ix }
            .forEach { (ix, objectToken) ->
                objs.add(objectToken.representation)
                expressions[ix] = objectToken.expression
                if (objectToken.color != null)
                    objectColors[ix] = objectToken.color
                if (objectToken.label != null)
                    objectLabels[ix] = objectToken.label
                if (objectToken.isPhantom)
                    phantoms.add(ix)
            }
        return SaveState(
            objects = objs,
            objectColors = objectColors,
            objectLabels = objectLabels,
            expressions = expressions,
            arcPaths = arcPaths,
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
                        ix to ObjectToken(
                            representation = obj,
                            expression = expressions[ix],
                            label = objectLabels[ix],
                            color = objectColors[ix],
                            isPhantom = ix in phantoms,
                        )
                    },
                    regions = regions,
                    arcPaths = arcPaths.filterNotNull(),
                )
            }
        }
    }
}
