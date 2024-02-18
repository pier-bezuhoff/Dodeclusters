package data.io

import androidx.compose.ui.graphics.Color
import data.Circle
import data.Cluster
import data.ColorSerializer
import kotlinx.serialization.Serializable

// MIME type: application/yaml
// extension: idk, either .ddc or .yaml
/** Dodeclusters' format `.ddc`. Aiming for a nicely-formatted, readable & extendable YAML subset */
@Serializable
data class Ddc(
    val param1: String = "abc",
    val content: List<Figure>,
) {
    /** ddc tokens */
    @Serializable
    sealed class Figure {
        @Serializable
        data class Cluster(
            /** 2 value list: [[first circle index, last circle index]] */
            val cluster: List<Int>,
            val circles: List<data.Circle>,
            val parts: List<data.Cluster.Part>,
            val filled: Boolean = true,
            val rule: List<Int> = emptyList(),
        ) : Figure() {
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
        ) : Figure() {
            fun toCircle(): data.Circle =
                Circle(x, y, r)
        }
    }

    fun encode(): String =
        with (Indentation(0)) {
            listOf(
                "---",
                encode("param1", param1),
                encode("content:"),
                content.map {
                    when (it) {
                        is Figure.Circle -> down().encodeCircle(it)
                        is Figure.Cluster -> down().encodeCluster(it)
                    }
                },
                "..."
            ).joinToString(separator = "\n")
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

    fun encodeIntSequence(ints: List<Int>): String =
        ints.joinToString(prefix = "[", postfix = "]", separator = ",")

    /** [header] is unindented */
    fun encodeListItem(header: String, vararg lines: String): String =
        "$prevIndent- $header\n" +
            lines.joinToString(prefix = SINGLE_INDENT, separator = "\n")

    fun encodeCircle(f: Ddc.Figure.Circle): String =
        encodeListItem(
            "circle: ${f.circle}",
            encode("x", f.x),
            encode("y", f.y),
            encode("r", f.r),
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