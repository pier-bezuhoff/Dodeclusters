package domain.io

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import domain.cluster.Cluster
import domain.ColorCssSerializer
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Line
import domain.cluster.ClusterPart
import kotlinx.serialization.json.Json
import ui.theme.DodeclustersColors
import kotlin.math.hypot

private const val INDENT = "  "
private const val INDENT1 = INDENT
private const val INDENT2 = INDENT + INDENT
private const val desc = """<desc>Created in Dodeclusters.</desc>"""

// TODO: make a dialog with encodeCircles + other options and
//  a disclaimer about border-only export being unimplemented
// NOTE: ksvg doesn't support wasm yet + my use case is relatively formulaic
// NOTE: Path.toSvg is coming soon (tm) to compose.ui.graphics
// NOTE: "For reliable results cross-browser, use numbers with no more
//  than 2 digits after the decimal and four digits before it." -- im gonna ignore this
// NOTE: this intersection-based way of rendering svg is quite slow for larger clusters
//  consider fusing part.insides into <path d="a.."/> instead of nested clipPath's
// TODO: only-borders export
fun cluster2svg(
    cluster: Cluster,
    backgroundColor: Color?,
    startX: Float, startY: Float,
    width: Float, height: Float,
    encodeAllCircles: Boolean = false,
): String {
    val visibleRect = Rect(0f, 0f, width, height)
    val tr = Offset(-startX, -startY)
    val circles = cluster.circles.map { it.translate(tr) }
    val partClipsAndMasks = cluster.parts.withIndex()
        .joinToString("\n") { (i, part) ->
            partMask(circles, i, part, visibleRect)
        }
    val bgRect = backgroundColor?.let {
        val bg = Json.encodeToString(ColorCssSerializer, it).trim('"')
        formatRect(visibleRect, bg)
    } ?: ""
    val partRects = cluster.parts.withIndex()
        .joinToString("\n") { (i, part) ->
            val fill = Json.encodeToString(ColorCssSerializer, part.fillColor).trim('"')
            formatRect(visibleRect, fill, postfix = """mask="url(#mask-part$i)"""")
        }
    val golden = Json.encodeToString(ColorCssSerializer, DodeclustersColors.tertiaryDark)
    val allCircles =
        if (encodeAllCircles) circles.joinToString("\n") { circle ->
            formatCircleOrLine(circle, visibleRect, "none", postfix = "stroke=$golden")
        }
        else ""
    return """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0.0 0.0 $width $height">
$desc
<defs>
$partClipsAndMasks
</defs>
$bgRect
$partRects
$allCircles
</svg>"""
}

fun partMask(
    circles: List<CircleOrLine>,
    partIndex: Int,
    part: ClusterPart,
    visibleRect: Rect
): String {
    val insides = part.insides.map { circles[it] }
    val outsides = part.outsides.map { circles[it] }
    val firstClipPath =
        if (insides.size >= 2) """$INDENT1<clipPath id="clip-part$partIndex-0">
$INDENT2${formatCircleOrLine(insides[0], visibleRect)}
$INDENT1</clipPath>${"\n"}"""
        else ""
    val clipPaths = insides
        .drop(1)
        .dropLast(1)
        .withIndex()
        .joinToString("\n") { (j, c) ->
            val inJ = j + 1 // index of the circle in the list of part.insides
"""$INDENT1<clipPath id="clip-part$partIndex-$inJ">
$INDENT2${formatCircleOrLine(c, visibleRect, postfix = "clip-path=\"url(#clip-part$partIndex-$j)\"")}
$INDENT1</clipPath>"""
        }
    val insidesInMask =
        INDENT2 + when (part.insides.size) {
            0 -> formatRect(visibleRect, "white")
            1 -> formatCircleOrLine(insides.last(), visibleRect, "white")
            else -> formatCircleOrLine(insides.last(), visibleRect, "white",
                """clip-path="url(#clip-part$partIndex-${part.insides.size - 2})""""
            )
        }
    val outsidesInMask = outsides.joinToString("\n") {
        INDENT2 + formatCircleOrLine(it, visibleRect, "black")
    }
    val mask = """$firstClipPath$clipPaths
$INDENT1<mask id="mask-part$partIndex">
$insidesInMask${if (outsidesInMask.isBlank()) "" else "\n" + outsidesInMask}
$INDENT1</mask>"""
    return mask
}

private fun formatRect(visibleRect: Rect, fill: String = "black", prefix: String = "", postfix: String = ""): String {
    val pre = if (prefix.isBlank()) "" else "$prefix "
    return """<rect ${pre}x="${visibleRect.left}" y="${visibleRect.top}" width="100%" height="100%" fill="$fill" $postfix/>"""
}

private fun formatCircleOrLine(circle: CircleOrLine, visibleRect: Rect, fill: String = "black", prefix: String = "", postfix: String = ""): String {
    val pre = if (prefix.isBlank()) "" else "$prefix "
    return when (circle) {
        is Circle -> """<circle ${pre}cx="${circle.x}" cy="${circle.y}" r="${circle.radius}" fill="$fill" $postfix/>"""
        is Line -> {
            val pointClosestToScreenCenter = circle.project(visibleRect.center)
            val direction = circle.directionVector
            val normal = circle.normalVector
            val diagonal = hypot(visibleRect.width, visibleRect.height)
            val farBack = pointClosestToScreenCenter - direction * diagonal
            val farForward = pointClosestToScreenCenter + direction * diagonal
            val farForwardIn = farForward + normal * diagonal
            val farBackIn = farBack + normal * diagonal
            val d = "M ${farBack.x} ${farBack.y} " +
                    "L ${farForward.x} ${farForward.y} " +
                    "L ${farForwardIn.x} ${farForwardIn.y} " +
                    "L ${farBackIn.x} ${farBackIn.y} " +
                    "z"
            """<path ${pre}d="$d" fill="$fill" $postfix/>"""
        }
    }
}

// example:
// <svg viewBox ="0 0 400 400">
//   <defs>
//     <clipPath id="clip-part0-0">
//       <circle cx="100" cy="100" r="50"/>
//     </clipPath>
//     <clipPath id="clip-part0-1">
//       <circle cx="120" cy="100" r="50" clip-path="url(#clip-part0-0)"/>
//     </clipPath>
//     <mask id="mask-part0">
//       <circle cx="105" cy="80" r="50" clip-path="url(#clip-part0-1)" fill="white"/>
//       <circle cx="110" cy="130" r="20" fill="black"/>
//     </mask>
//   </defs>
//   <rect width="100%" height="100%" fill="black"/>
//   <rect width="100%" height="100%" mask="url(#mask-part0)" fill="deepskyblue"/>
// </svg>


// small bug: sometimes some circles are slightly clipped with random rects, cmp dl/a.svg
// beware of ram consumption spikes when zooming in
fun cluster2svgCheckPattern(
    cluster: Cluster,
    backgroundColor: Color,
    chessboardPatternStartsColored: Boolean,
//    fillInsides: Boolean = true,
    startX: Float, startY: Float,
    width: Float, height: Float,
): String {
    val circleNamePrefix = "circle"
    val visibleRect = Rect(0f, 0f, width, height)
    val bg = Json.encodeToString(ColorCssSerializer, backgroundColor).trim('"')
    val n = cluster.circles.size
    val tr = Offset(-startX, -startY)
    val circles = cluster.circles
        .mapIndexed { i, c ->
            INDENT1 + formatCircleOrLine(
                c.translate(tr), visibleRect,
                fill = bg,
                prefix = "id=\"$circleNamePrefix$i\""
            )
        }.joinToString(separator = "\n")
    val feImages = cluster.circles.indices
        .joinToString(separator = "\n") { i ->
            """$INDENT2<feImage href="#$circleNamePrefix$i" result="$circleNamePrefix$i"/>"""
        }
    var s = "" // "0123..."
    val feComposites = cluster.circles.indices.toList().dropLast(1)
        .joinToString(separator = "\n") { i ->
            s += "$i"
            val compoundName = "$circleNamePrefix$s"
            """$INDENT2<feComposite in="$compoundName" in2="$circleNamePrefix${i+1}" operator="xor" result="$compoundName${i+1}"/>"""
        }
    val lastOp =
        if (chessboardPatternStartsColored) "xor"
        else "in"
    return """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0.0 0.0 $width $height">
$desc
<defs>
$circles
$INDENT1<filter id="check-pattern">
$feImages
$feComposites
$INDENT2<feComposite in="$circleNamePrefix$s${n-1}" in2="SourceGraphic" operator="$lastOp"/>
$INDENT1</filter>
</defs>
${formatRect(visibleRect, bg, postfix = """filter="url(#check-pattern)"""")}
</svg>"""
}

// check pattern example:
// <svg viewBox="0 0 400 300" xmlns="http://www.w3.org/2000/svg" >
// <defs>
//   <circle id="c1" r="100" cx="200" cy="100"/>
//   <circle id="c2" r="90" cx="150" cy="100"/>
//   <circle id="c3" r="70" cx="150" cy="150"/>
//   <filter id="xor">
//     <feImage href="#c1" result="c1"/>
//     <feImage href="#c2" result="c2"/>
//     <feImage href="#c3" result="c3"/>
//     <feComposite in="c1" in2="c2" operator="xor" result="c12" />
//     <feComposite in="c12" in2="c3" operator="xor" result="c123" />
//     <feComposite in="c123" in2="SourceGraphic" operator="xor" />
//   </filter>
// </defs>
// <rect width="100%" height="100%" fill="green" filter="url(#xor)" />
// </svg>