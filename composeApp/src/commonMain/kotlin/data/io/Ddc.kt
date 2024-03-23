package data.io

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import data.Circle
import data.Cluster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import utils.ColorCssSerializer

// MIME type: application/yaml
// extension: idk, either .ddc or .yaml
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
    constructor(cluster: Cluster) : this(
        content = listOf(Token.Cluster(
            if (cluster.circles.isEmpty()) emptyList()
            else listOf(0, cluster.circles.size - 1),
            cluster.circles,
            cluster.parts,
            cluster.filled
        ))
    )

    /** ddc tokens */
    @Serializable
    sealed class Token {
        @SerialName("Cluster")
        @Serializable
        data class Cluster(
            /** 2 value list: [[first circle index, last circle index]] */
            val indices: List<Int>,
            val circles: List<data.Circle>,
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
            val offset: Offset
                get() = Offset(x.toFloat(), y.toFloat())
            fun toCircle(): data.Circle =
                Circle(x, y, radius)
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
                encode("shape", Json.encodeToString(shape)),
                encode("drawTrace", drawTrace.toString()),
                encode("content:"),
            )
            val body = content.map {
                when (it) {
                    is Token.Circle -> down().encodeCircle(it)
                    is Token.Cluster -> down().encodeCluster(it)
                }
            }
            val end = "..."
            (head + body).joinToString(separator = "\n") + end
        }

    companion object {
        const val DEFAULT_NAME = "cluster"
        val DEFAULT_BACKGROUND_COLOR = Color.White
        val DEFAULT_BEST_CENTER_X: Float? = null
        val DEFAULT_BEST_CENTER_Y: Float? = null
        val DEFAULT_SHAPE = Shape.CIRCLE
        const val DEFAULT_DRAW_TRACE = true

        const val DEFAULT_CLUSTER_FILLED = true
        const val DEFAULT_CLUSTER_VISIBLE = true
        val DEFAULT_CLUSTER_FILL_COLOR = Color.Cyan
        val DEFAULT_CLUSTER_BORDER_COLOR: Color? = null
        val DEFAULT_CLUSTER_RULE = emptyList<Int>()

        const val DEFAULT_CIRCLE_VISIBLE = false
        const val DEFAULT_CIRCLE_FILLED = true
        val DEFAULT_CIRCLE_FILL_COLOR: Color = Color.Black
        val DEFAULT_CIRCLE_BORDER_COLOR: Color? = null
        val DEFAULT_CIRCLE_RULE = emptyList<Int>()
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

    fun encodeOptional(key: String, value: Any?): String =
        value?.let { encode(key, value) } ?: ""

    fun Color.encodeColor(): String =
        Json.encodeToString(ColorCssSerializer, this)

    fun encodeIntSequence(ints: List<Int>): String =
        ints.joinToString(prefix = "[", postfix = "]", separator = ",")

    /** [header] is unindented */
    fun encodeListItem(header: String, vararg lines: String): String =
        "$prevIndent- $header\n" +
            lines.joinToString(separator = "\n")

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
            encodeOptional("rule", if (f.rule.isEmpty()) null else encodeIntSequence(f.rule))
        )

    fun encodeClusterCircle(circle: Circle): String =
        encodeListItem(
            "x: ${circle.x}",
            encode("y", circle.y),
            encode("radius", circle.radius),
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
            encode("circles:"),
            *f.circles.map { down().encodeClusterCircle(it) }.toTypedArray(),
            encode("parts:"),
            *f.parts.map { down().encodeClusterPart(it) }.toTypedArray(),
            encode("filled", f.filled),
            encodeOptional("rule", if (f.rule.isEmpty()) null else encodeIntSequence(f.rule))
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