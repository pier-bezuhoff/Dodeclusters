package data.io

import androidx.compose.ui.graphics.Color
import data.Circle
import data.Cluster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utils.ColorCssSerializer

// MIME type: application/yaml
// extension: idk, either .ddc or .yaml
/** Dodeclusters' format `.ddc`. Aiming for a nicely-formatted, readable & extendable YAML subset */
@Serializable
data class Ddc(
    val param1: String = DEFAULT_PARAM1,
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
            val parts: List<data.Cluster.Part>,
            val filled: Boolean = DEFAULT_CLUSTER_FILLED,
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
            val r: Double,
            val visible: Boolean = DEFAULT_CIRCLE_VISIBLE,
            val filled: Boolean = DEFAULT_CIRCLE_FILLED,
            @Serializable(ColorCssSerializer::class)
            val fillColor: Color? = DEFAULT_CIRCLE_FILL_COLOR,
            @Serializable(ColorCssSerializer::class)
            val borderColor: Color? = DEFAULT_CIRCLE_BORDER_COLOR,
            val rule: List<Int> = DEFAULT_CIRCLE_RULE,
        ) : Token() {
            fun toCircle(): data.Circle =
                Circle(x, y, r)
        }
    }

    fun encode(): String =
        with (Indentation(0)) {
            val head = listOf(
                "---",
                encode("param1", param1),
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
        const val DEFAULT_PARAM1 = "abc"

        const val DEFAULT_CLUSTER_FILLED = true
        const val DEFAULT_CLUSTER_VISIBLE = true
        val DEFAULT_CLUSTER_FILL_COLOR = Color.Cyan
        val DEFAULT_CLUSTER_RULE = emptyList<Int>()

        const val DEFAULT_CIRCLE_VISIBLE = false
        const val DEFAULT_CIRCLE_FILLED = true
        val DEFAULT_CIRCLE_FILL_COLOR = null
        val DEFAULT_CIRCLE_BORDER_COLOR = null
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
            "!<Circle>",
            encode("index", f.index),
            encode("x", f.x),
            encode("y", f.y),
            encode("r", f.r),
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
            encode("r", circle.radius),
        )

    fun encodeClusterPart(part: Cluster.Part): String =
        encodeListItem(
            "insides: " + encodeIntSequence(part.insides.sorted()),
            encode("outsides", encodeIntSequence(part.outsides.sorted())),
            encodeOptional("fillColor", part.fillColor.encodeColor()),
//            encodeOptional("borderColor", part.borderColor?.encodeColor()),
        )

    fun encodeCluster(f: Ddc.Token.Cluster): String =
        encodeListItem(
            "!<Cluster>",
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