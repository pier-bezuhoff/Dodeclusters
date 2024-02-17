package data

import androidx.compose.ui.graphics.Color

// MIME type: application/yaml
/** Aim for nicely-formatted, readable YAML subset */
class Dodeclusters(
    val params: Map<String, String>,
    val figures: List<Figure>,
) {
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

    fun encode(key: String, value: Any): String =
        "$indent$key: $value"

    fun encodeOptional(key: String, value: Any?): String =
        value?.let { encode(key, value) } ?: ""

    fun encodeIntSequence(ints: List<Int>): String =
        ints.joinToString(prefix = "[", postfix = "]", separator = ",")

    /** [header] is unindented */
    fun encodeListItem(header: String, vararg lines: String): String =
        "$prevIndent- $header\n" +
            lines.joinToString(prefix = "  ", separator = "\n")

    fun encodeCircle(c: Dodeclusters.Figure.Circle): String =
        encodeListItem(
            "circle: ${c.index}",
            encode("x", c.circle.x),
            encode("y", c.circle.y),
            encode("r", c.circle.radius),
            encode("visible", c.visible),
            encode("filled", c.filled),
            encodeOptional("fillColor", c.fillColor?.value?.toString(16)),
            encodeOptional("borderColor", c.borderColor?.value?.toString(16)),
            encodeOptional("rule", if (c.rule.isEmpty()) null else encodeIntSequence(c.rule))
        )

    fun encodeClusterCircle(circle: Circle): String =
        encodeListItem(
            "x: ${circle.x}",
            encode("y", circle.y),
            encode("r", circle.radius),
        )

    fun encodeClusterPart(part: Cluster.Part): String =
        encodeListItem(
            "insides:" + encodeIntSequence(part.insides.sorted()),
            encode("outsides", encodeIntSequence(part.outsides.sorted())),
            encodeOptional("fillColor", part.fillColor.value.toString(16)),
//            encodeOptional("borderColor", part.borderColor?.value?.toString(16)),
        )

    fun encodeCluster(cluster: Cluster): String =
        encodeListItem(
            "cluster:",
            "circles:",
            "parts",
            "filled",
            "rule:",
        )
}