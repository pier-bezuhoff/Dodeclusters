package data.io

import androidx.compose.ui.graphics.Color
import data.geometry.Circle
import data.Cluster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import data.ColorCssSerializer
import data.geometry.CircleOrLine
import data.geometry.Line
import ui.theme.DodeclustersColors

// MIME type: application/yaml or text/plain
// extension: .ddc or .yaml/.yml
/** Dodeclusters' format. Aiming for a nicely-formatted, readable & extensible YAML subset */
@Serializable
data class Ddc(
    val name: String = DEFAULT_NAME,
    @Serializable(ColorCssSerializer::class)
    val backgroundColor: Color = DEFAULT_BACKGROUND_COLOR,
    val bestCenterX: Float? = DEFAULT_BEST_CENTER_X,
    val bestCenterY: Float? = DEFAULT_BEST_CENTER_Y,
    val shape: Shape = DEFAULT_SHAPE,
    val drawTrace: Boolean = DEFAULT_DRAW_TRACE,
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
        content = listOf(Token.Cluster(
            if (cluster.circles.isEmpty()) emptyList()
            else listOf(0, cluster.circles.size - 1),
            cluster.circles,
            cluster.parts,
            cluster.filled
        ))
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
            val parts: List<data.Cluster.Part>,
            val filled: Boolean = DEFAULT_CLUSTER_FILLED,
            /** circle indices used shall be Ddc-global circle indices, the one consistent with cluster.indices and circle.index */
            val rule: List<Int> = DEFAULT_CLUSTER_RULE,
        ) : Token() {
            fun toCluster(): data.Cluster =
                Cluster(circles, parts, filled)
        }
        @SerialName("Circle")
        @Serializable
        data class Circle(
            val index: Int,
            val x: Double,
            val y: Double,
            val radius: Double,
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
                Circle(x, y, radius)
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
                encode("name", name),
                encode("backgroundColor", Json.encodeToString(ColorCssSerializer, backgroundColor)),
                encode("bestCenterX", bestCenterX.toString()),
                encode("bestCenterY", bestCenterY.toString()),
                encodeOptional("shape", if (shape != DEFAULT_SHAPE) Json.encodeToString(shape) else null),
                encode("drawTrace", drawTrace.toString()),
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
        const val DEFAULT_NAME = "cluster"
        const val DEFAULT_EXTENSION = "yml"
        val DEFAULT_BACKGROUND_COLOR = Color.White
        val DEFAULT_BEST_CENTER_X: Float? = null
        val DEFAULT_BEST_CENTER_Y: Float? = null
        val DEFAULT_SHAPE = Shape.CIRCLE
        const val DEFAULT_DRAW_TRACE = true

        const val DEFAULT_CLUSTER_FILLED = true
        const val DEFAULT_CLUSTER_VISIBLE = true
        val DEFAULT_CLUSTER_FILL_COLOR = DodeclustersColors.purple // a bit of a questionable dependency here
        val DEFAULT_CLUSTER_BORDER_COLOR: Color? = null
        val DEFAULT_CLUSTER_RULE = emptyList<Int>()

        const val DEFAULT_CIRCLE_VISIBLE = false
        const val DEFAULT_CIRCLE_FILLED = true
        val DEFAULT_CIRCLE_FILL_COLOR: Color = Color.Black
        val DEFAULT_CIRCLE_BORDER_COLOR: Color? = null
        val DEFAULT_CIRCLE_RULE = emptyList<Int>()

        val SAMPLE = Ddc(
            name = "circle",
            bestCenterX = 0f,
            bestCenterY = 0f,
            content = listOf(
                Token.Circle(0, 0.0, 0.0, 200.0, filled = true)
            )
        )
    }
}


/** Old, deprecated Dodeclusters' format. Left for compat */
@Serializable
data class OldDdc(
    val name: String = Ddc.DEFAULT_NAME,
    @Serializable(ColorCssSerializer::class)
    val backgroundColor: Color = Ddc.DEFAULT_BACKGROUND_COLOR,
    val bestCenterX: Float? = Ddc.DEFAULT_BEST_CENTER_X,
    val bestCenterY: Float? = Ddc.DEFAULT_BEST_CENTER_Y,
    val shape: Shape = Ddc.DEFAULT_SHAPE,
    val drawTrace: Boolean = Ddc.DEFAULT_DRAW_TRACE,
    val content: List<Token>,
) {
    val nCircles: Int
        get() = content.lastOrNull()?.let {
            when (it) {
                is Token.Circle -> it.index + 1
                is Token.Cluster -> it.indices.last() + 1
            }
        } ?: 0

    constructor(cluster: Cluster) : this(
        content = listOf(Token.Cluster(
            if (cluster.circles.isEmpty()) emptyList()
            else listOf(0, cluster.circles.size - 1),
            cluster.circles.filterIsInstance<Circle>(),
            cluster.parts,
            cluster.filled
        ))
    )

    @Serializable
    sealed class Token {
        @SerialName("Cluster")
        @Serializable
        data class Cluster(
            /** 2 value list: [[first circle index, last circle index]] */
            val indices: List<Int>,
            val circles: List<data.geometry.Circle>,
            /** circle indices used parts shall be Ddc-global circle indices, the one consistent with cluster.indices */
            val parts: List<data.Cluster.Part>,
            val filled: Boolean = Ddc.DEFAULT_CLUSTER_FILLED,
            /** circle indices used shall be Ddc-global circle indices, the one consistent with cluster.indices and circle.index */
            val rule: List<Int> = Ddc.DEFAULT_CLUSTER_RULE,
        ) : Token() {
            fun toCluster(): data.Cluster =
                Cluster(circles, parts, filled)
        }
        @SerialName("Circle")
        @Serializable
        data class Circle(
            val index: Int,
            val x: Double,
            val y: Double,
            val radius: Double,
            val visible: Boolean = Ddc.DEFAULT_CIRCLE_VISIBLE,
            val filled: Boolean = Ddc.DEFAULT_CIRCLE_FILLED,
            @Serializable(ColorCssSerializer::class)
            val fillColor: Color? = Ddc.DEFAULT_CIRCLE_FILL_COLOR,
            @Serializable(ColorCssSerializer::class)
            val borderColor: Color? = Ddc.DEFAULT_CIRCLE_BORDER_COLOR,
            /** circle indices used shall be Ddc-global circle indices, the one consistent with cluster.indices and circle.index */
            val rule: List<Int> = Ddc.DEFAULT_CIRCLE_RULE,
        ) : Token() {
            fun toCircle(): data.geometry.Circle =
                Circle(x, y, radius)
        }
    }
}

private data class Indentation(val indentLevel: Int) {
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

    fun encodeCircle(f: Ddc.Token.Circle): String =
        encodeListItem(
            "type: Circle",
            encode("index", f.index),
            encode("x", f.x),
            encode("y", f.y),
            encode("radius", f.radius),
            encode("visible", f.visible),
            encode("filled", f.filled),
            encodeOptional("fillColor", f.fillColor?.encodeColor()),
            encodeOptional("borderColor", f.borderColor?.encodeColor()),
            encodeOptional("rule", if (f.rule.isEmpty()) null else encodeIntSequence(f.rule)),
        )

    fun encodeLine(f: Ddc.Token.Line): String =
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
        )

    fun encodeClusterLine(line: Line): String =
        encodeListItem(
            "type: line",
            encode("a", line.a),
            encode("b", line.b),
            encode("c", line.c),
        )

    fun encodeClusterPart(part: Cluster.Part): String =
        encodeListItem(
            "insides: " + encodeIntSequence(part.insides.sorted()),
            encode("outsides", encodeIntSequence(part.outsides.sorted())),
            encodeOptional("fillColor", part.fillColor.encodeColor()),
            encodeOptional("borderColor", part.borderColor?.encodeColor()),
        )

    fun encodeCluster(f: Ddc.Token.Cluster): String =
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
            encode("filled", f.filled),
        )

    companion object {
        const val SINGLE_INDENT = "  "
    }
}

@Serializable
/** Shapes to draw instead of circles */
enum class Shape {
    CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR;
}