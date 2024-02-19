package data.io

import androidx.compose.ui.graphics.Color
import data.Circle
import data.Cluster
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utils.ColorSerializer

// MIME type: application/yaml
// extension: idk, either .ddc or .yaml
/** Dodeclusters' format `.ddc`. Aiming for a nicely-formatted, readable & extendable YAML subset */
@Serializable
data class Ddc(
    val param1: String = "abc",
    val content: List<Token>,
) {
    constructor(cluster: Cluster) : this(
        content = listOf(Token.Cluster(
            if (cluster.circles.isEmpty()) emptyList() else listOf(0, cluster.circles.size),
            cluster.circles,
            cluster.parts,
            cluster.filled
        )
        )
    )

    /** ddc tokens */
    @Serializable
    sealed class Token {
        @Serializable
        data class Cluster(
            /** 2 value list: [[first circle index, last circle index]] */
            val cluster: List<Int>,
            val circles: List<data.Circle>,
            val parts: List<data.Cluster.Part>,
            val filled: Boolean = true,
            val rule: List<Int> = emptyList(),
        ) : Token() {
            fun toCluster(): data.Cluster =
                Cluster(circles, parts, filled)
        }
        @Serializable
        data class Circle(
            val circle: Int,
            val x: Double,
            val y: Double,
            val r: Double,
            val visible: Boolean = false,
            val filled: Boolean = true,
            @Serializable(ColorSerializer::class)
            val fillColor: Color? = null,
            @Serializable(ColorSerializer::class)
            val borderColor: Color? = null,
            val rule: List<Int> = emptyList(),
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
            (head + body + end).joinToString(separator = "\n")
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
        Json.encodeToString(ColorSerializer, this)
            .drop(1).dropLast(1)

    fun encodeIntSequence(ints: List<Int>): String =
        ints.joinToString(prefix = "[", postfix = "]", separator = ",")

    /** [header] is unindented */
    fun encodeListItem(header: String, vararg lines: String): String =
        "$prevIndent- $header\n" +
            lines.joinToString(separator = "\n")

    fun encodeCircle(f: Ddc.Token.Circle): String =
        encodeListItem(
            "circle: ${f.circle}",
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
            "cluster: [${f.cluster.first()}, ${f.cluster.last()}]",
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