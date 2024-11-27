package domain.io

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Line
import domain.ColorCssSerializer
import domain.cluster.Cluster
import domain.cluster.LogicalRegion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ui.theme.DodeclustersColors

// MIME type: application/yaml or text/plain
// extension: .ddc or .yaml/.yml
// NOTE: do not forget to update js version JsDdc correspondingly
/** Dodeclusters' format. Aiming for a nicely-formatted, readable & extensible YAML subset */
@Serializable
@Immutable
data class DdcV2(
    val name: String = DEFAULT_NAME,
    @Serializable(ColorCssSerializer::class)
    val backgroundColor: Color = DEFAULT_BACKGROUND_COLOR,
    val bestCenterX: Float? = DEFAULT_BEST_CENTER_X,
    val bestCenterY: Float? = DEFAULT_BEST_CENTER_Y,
    val shape: Shape = DEFAULT_SHAPE,
    val drawTrace: Boolean = DEFAULT_DRAW_TRACE,
    /** indicates using all-circle chessboard pattern coloring, ignoring parts */
    val chessboardPattern: Boolean = DEFAULT_CHESSBOARD_PATTERN,
    /** one of two possible starting chessboard phases, true=colored=bg filled with color */
    val chessboardPatternStartsColored: Boolean = DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED,
    val content: List<Token>,
) {
    val nCircles: Int
        get() = content.lastOrNull()?.let {
            when (it) {
                is Token.Circle -> it.index + 1
                is Token.Line -> it.index + 1
                is Token.Cluster -> it.indices.last() + 1
            }
        } ?: 0

    constructor(cluster: Cluster) : this(
        content = listOf(
            Token.Cluster(
                if (cluster.circles.isEmpty()) emptyList()
                else listOf(0, cluster.circles.size - 1),
                cluster.circles,
                cluster.parts,
            )
        )
    )

    @Serializable
    sealed class Token {
        @SerialName("Cluster")
        @Serializable
        data class Cluster(
            /** 2 value list: [[first circle index, last circle index]] */
            val indices: List<Int>,
            val circles: List<CircleOrLine>,
            /** circle indices used parts shall be Ddc-global circle indices, the one consistent with cluster.indices */
            val parts: List<LogicalRegion>,
            val filled: Boolean = DEFAULT_CLUSTER_FILLED,
            /** circle indices used shall be Ddc-global circle indices, the one consistent with cluster.indices and circle.index */
            val rule: List<Int> = DEFAULT_CLUSTER_RULE,
        ) : Token() {
            fun toCluster(): domain.cluster.Cluster =
                Cluster(circles, parts)
        }
        @SerialName("Circle")
        @Serializable
        data class Circle(
            val index: Int,
            val x: Double,
            val y: Double,
            val radius: Double,
            val isCCW: Boolean = DEFAULT_CIRCLE_IS_CCW,
            val visible: Boolean = DEFAULT_CIRCLE_VISIBLE,
            val filled: Boolean = DEFAULT_CIRCLE_FILLED,
            @Serializable(ColorCssSerializer::class)
            val fillColor: Color? = DEFAULT_CIRCLE_FILL_COLOR,
            @Serializable(ColorCssSerializer::class)
            val borderColor: Color? = DEFAULT_CIRCLE_BORDER_COLOR,
            /** circle indices used shall be Ddc-global circle indices, the one consistent with cluster.indices and circle.index */
            val rule: List<Int> = DEFAULT_CIRCLE_RULE,
        ) : Token() {
            fun toCircle(): data.geometry.Circle =
                Circle(x, y, radius, isCCW)
        }
        @SerialName("Line")
        @Serializable
        data class Line(
            val index: Int,
            val a: Double,
            val b: Double,
            val c: Double,
            val visible: Boolean = DEFAULT_CIRCLE_VISIBLE,
            @Serializable(ColorCssSerializer::class)
            val borderColor: Color? = DEFAULT_CIRCLE_BORDER_COLOR,
            /** circle indices used shall be Ddc-global circle indices,
             * the one consistent with cluster.indices and circle.index */
            val rule: List<Int> = DEFAULT_CIRCLE_RULE,
        ) : Token() {
            fun toLine(): data.geometry.Line =
                Line(a, b, c)
        }
    }

    fun encode(): String =
        with (Indentation(0)) {
            val head = listOfNotNull(
                "---",
                "# $HEADER",
                encode("name", name),
                encode("backgroundColor", Json.encodeToString(ColorCssSerializer, backgroundColor)),
                encode("bestCenterX", bestCenterX.toString()),
                encode("bestCenterY", bestCenterY.toString()),
                encodeOptional("shape", if (shape != DEFAULT_SHAPE) Json.encodeToString(shape) else null),
                encode("drawTrace", drawTrace.toString()),
                encodeOptional("chessboardPattern",
                    if (chessboardPattern != DEFAULT_CHESSBOARD_PATTERN) chessboardPattern else null
                ),
                encodeOptional("chessboardPatternStartsWhite",
                    if (chessboardPattern) chessboardPatternStartsColored else null
                ),
                encode("content:"),
            )
            val body = content.map {
                when (it) {
                    is Token.Circle -> down().encodeCircle(it)
                    is Token.Line -> down().encodeLine(it)
                    is Token.Cluster -> down().encodeCluster(it)
                }
            }
            val end = "..."
            (head + body + listOf(end)).joinToString(separator = "\n")
        }

    companion object {
        const val HEADER = "ddc v2, generated by Dodeclusters"
        const val DEFAULT_NAME = "cluster"
        const val DEFAULT_EXTENSION = "yml"
        val DEFAULT_BACKGROUND_COLOR = Color.White
        val DEFAULT_BEST_CENTER_X: Float? = null
        val DEFAULT_BEST_CENTER_Y: Float? = null
        val DEFAULT_SHAPE = Shape.CIRCLE
        const val DEFAULT_DRAW_TRACE = true
        const val DEFAULT_CHESSBOARD_PATTERN = false
        const val DEFAULT_CHESSBOARD_PATTERN_STARTS_COLORED = true

        const val DEFAULT_CLUSTER_FILLED = true
        const val DEFAULT_CLUSTER_VISIBLE = true
        val DEFAULT_CLUSTER_FILL_COLOR = DodeclustersColors.purple // a bit of a questionable dependency here
        val DEFAULT_CLUSTER_BORDER_COLOR: Color? = null
        val DEFAULT_CLUSTER_RULE = emptyList<Int>()

        const val DEFAULT_CIRCLE_IS_CCW = true
        const val DEFAULT_CIRCLE_VISIBLE = false
        const val DEFAULT_CIRCLE_FILLED = true
        val DEFAULT_CIRCLE_FILL_COLOR: Color = Color.Black
        val DEFAULT_CIRCLE_BORDER_COLOR: Color? = null
        val DEFAULT_CIRCLE_RULE = emptyList<Int>()
    }
}

internal data class Indentation(val indentLevel: Int) {
    val indent: String = SINGLE_INDENT.repeat(indentLevel)
    val prevIndent: String
        get() = SINGLE_INDENT.repeat(indentLevel - 1)

    fun down(): Indentation =
        Indentation(indentLevel + 1)

    fun encode(key: String, value: Any): String =
        "$indent$key: $value"

    fun encode(key: String): String =
        "$indent$key"

    fun encodeOptional(key: String, value: Any?): String? =
        value?.let { encode(key, value) }

    fun Color.encodeColor(): String =
        Json.encodeToString(ColorCssSerializer, this)

    fun encodeIntSequence(ints: List<Int>): String =
        ints.joinToString(prefix = "[", postfix = "]", separator = ",")

    /** [header] is unindented */
    fun encodeListItem(header: String, vararg lines: String?): String =
        "$prevIndent- $header\n" +
            lines.filterNotNull().joinToString(separator = "\n")

    fun encodeCircle(f: DdcV2.Token.Circle): String =
        encodeListItem(
            "type: Circle",
            encode("index", f.index),
            encode("x", f.x),
            encode("y", f.y),
            encode("radius", f.radius),
            encodeOptional("isCCW", if (f.isCCW != DdcV2.DEFAULT_CIRCLE_IS_CCW) f.isCCW else null),
            encode("visible", f.visible),
            encode("filled", f.filled),
            encodeOptional("fillColor", f.fillColor?.encodeColor()),
            encodeOptional("borderColor", f.borderColor?.encodeColor()),
            encodeOptional("rule", if (f.rule.isEmpty()) null else encodeIntSequence(f.rule)),
        )

    fun encodeLine(f: DdcV2.Token.Line): String =
        encodeListItem(
            "type: Line",
            encode("index", f.index),
            encode("a", f.a),
            encode("b", f.b),
            encode("c", f.c),
            encode("visible", f.visible),
            encodeOptional("borderColor", f.borderColor?.encodeColor()),
            encodeOptional("rule", if (f.rule.isEmpty()) null else encodeIntSequence(f.rule)),
        )

    fun encodeClusterCircle(circle: Circle): String =
        encodeListItem(
            "type: circle",
            encode("x", circle.x),
            encode("y", circle.y),
            encode("radius", circle.radius),
            encodeOptional("isCCW",
                if (circle.isCCW != DdcV2.DEFAULT_CIRCLE_IS_CCW) circle.isCCW else null
            ),
        )

    fun encodeClusterLine(line: Line): String =
        encodeListItem(
            "type: line",
            encode("a", line.a),
            encode("b", line.b),
            encode("c", line.c),
        )

    fun encodeClusterPart(part: LogicalRegion): String =
        encodeListItem(
            "insides: " + encodeIntSequence(part.insides.sorted()),
            encode("outsides", encodeIntSequence(part.outsides.sorted())),
            encodeOptional("fillColor", part.fillColor.encodeColor()),
            encodeOptional("borderColor", part.borderColor?.encodeColor()),
        )

    fun encodeCluster(f: DdcV2.Token.Cluster): String =
        encodeListItem(
            "type: Cluster",
            encode("indices", "[${f.indices.first()}, ${f.indices.last()}]"),
            encode("circles:" + if (f.circles.isEmpty()) " []" else ""),
            *f.circles.map {
                when (it) {
                    is Circle -> down().encodeClusterCircle(it)
                    is Line -> down().encodeClusterLine(it)
                }
            }.toTypedArray(),
            encode("parts:" + if (f.parts.isEmpty()) " []" else ""),
            *f.parts.map { down().encodeClusterPart(it) }.toTypedArray(),
            encodeOptional("rule", if (f.rule.isEmpty()) null else encodeIntSequence(f.rule)),
        )

    companion object {
        const val SINGLE_INDENT = "  "
    }
}

