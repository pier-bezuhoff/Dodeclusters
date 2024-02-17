package data.formats

import androidx.compose.ui.graphics.Color
import data.Circle
import data.Cluster

// MIME type: application/yaml
/** Dodeclusters format .ddc. Aiming for nicely-formatted, readable YAML subset */
class Ddc(
    val params: Map<String, String>,
    val figures: List<Figure>,
) {
    /** ddc tokens */
    sealed class Figure {
        data class Cluster(
            val cluster: data.Cluster,
            val indexRange: IntRange,
            val rule: List<Int> = emptyList(),
        ) : Figure()
        data class Circle(
            val circle: data.Circle,
            val index: Int,
            val visible: Boolean = false,
            val filled: Boolean = true,
            val fillColor: Color? = null,
            val borderColor: Color? = null,
            val rule: List<Int> = emptyList(),
        ) : Figure()
    }
}

private data class Indentation(val indentLevel: Int) {
    val indent: String = " ".repeat(2*indentLevel)
    val prevIndent: String
        get() = " ".repeat(2*(indentLevel - 1))

    fun down(): Indentation =
        Indentation(indentLevel + 1)

    fun encode(key: String, value: Any): String =
        "$indent$key: $value"

    fun encode(key: String): String =
        "$indent$key"

    fun encodeOptional(key: String, value: Any?): String =
        value?.let { encode(key, value) } ?: ""

    fun encodeIntSequence(ints: List<Int>): String =
        ints.joinToString(prefix = "[", postfix = "]", separator = ",")

    /** [header] is unindented */
    fun encodeListItem(header: String, vararg lines: String): String =
        "$prevIndent- $header\n" +
            lines.joinToString(prefix = "  ", separator = "\n")

    fun encodeCircle(f: Ddc.Figure.Circle): String =
        encodeListItem(
            "circle: ${f.index}",
            encode("x", f.circle.x),
            encode("y", f.circle.y),
            encode("r", f.circle.radius),
            encode("visible", f.visible),
            encode("filled", f.filled),
            encodeOptional("fillColor", f.fillColor?.value?.toString(16)),
            encodeOptional("borderColor", f.borderColor?.value?.toString(16)),
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
            encodeOptional("fillColor", part.fillColor.value.toString(16)),
//            encodeOptional("borderColor", part.borderColor?.value?.toString(16)),
        )

    fun encodeCluster(f: Ddc.Figure.Cluster): String =
        encodeListItem(
            "cluster: [${f.indexRange.first}, ${f.indexRange.last}]",
            encode("circles:"),
            *f.cluster.circles.map { down().encodeClusterCircle(it) }.toTypedArray(),
            encode("parts:"),
            *f.cluster.parts.map { down().encodeClusterPart(it) }.toTypedArray(),
            encode("filled", f.cluster.filled),
            encodeOptional("rule", if (f.rule.isEmpty()) null else encodeIntSequence(f.rule))
        )
}