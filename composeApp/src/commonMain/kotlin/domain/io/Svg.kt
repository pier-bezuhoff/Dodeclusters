package domain.io

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import data.Cluster
import domain.ColorCssSerializer
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.Line
import kotlinx.serialization.json.Json
import ui.theme.DodeclustersColors
import kotlin.math.hypot

// TODO: make a dialog with encodeCircles + other options and
//  a disclaimer about chessboard and border-only export being unimplemented
// NOTE: ksvg doesn't support wasm yet + my use case is relatively formulaic
// NOTE: Path.toSvg is coming soon (tm) to compose.ui.graphics
// NOTE: "For reliable results cross-browser, use numbers with no more
//  than 2 digits after the decimal and four digits before it." -- im gonna ignore this
// NOTE: this intersection-based way of rendering svg is quite slow for larger clusters
//  consider fusing part.insides into <path d="a.."/> instead of nested clipPath's
// TODO: chessboard pattern export, only-borders export
fun cluster2svg(
    cluster: Cluster,
    backgroundColor: Color?,
    startX: Float, startY: Float,
    width: Float, height: Float,
    encodeAllCircles: Boolean = false,
): String {
    val visibleRect = Rect(startX, startY, startX + width, startY + height)
    val partClipsAndMasks = cluster.parts.withIndex().joinToString("\n") { (i, part) ->
        partMask(cluster.circles, i, part, visibleRect)
    }
    val bgRect = backgroundColor?.let {
        val bg = Json.encodeToString(ColorCssSerializer, it)
        "<rect x=\"${visibleRect.left}\" y=\"${visibleRect.top}\" width=\"100%\" height=\"100%\" fill=$bg/>"
    } ?: ""
    val partRects = cluster.parts.withIndex().joinToString("\n") { (i, part) ->
        val fill = Json.encodeToString(ColorCssSerializer, part.fillColor)
        "<rect x=\"${visibleRect.left}\" y=\"${visibleRect.top}\" width=\"100%\" height=\"100%\" mask=\"url(#mask-part$i)\" fill=$fill/>"
    }
    val golden = Json.encodeToString(ColorCssSerializer, DodeclustersColors.tertiaryDark)
    val circles =
        if (encodeAllCircles) cluster.circles.joinToString("\n") { circle ->
            formatCircleOrLine(circle, visibleRect, "stroke=$golden fill=\"none\"")
        }
        else ""
    return """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="$startX $startY $width $height">
<defs>
$partClipsAndMasks
</defs>
$bgRect
$partRects
$circles
</svg>
""".trimIndent()
}

fun partMask(
    circles: List<CircleOrLine>,
    partIndex: Int,
    part: Cluster.Part,
    visibleRect: Rect
): String {
    val insides = part.insides.map { circles[it] }
    val outsides = part.outsides.map { circles[it] }
    val firstClipPath =
        if (insides.size >= 2) """
<clipPath id="clip-part$partIndex-0">
${formatCircleOrLine(insides[0], visibleRect)}
</clipPath>""".trimIndent()
        else ""
    val clipPaths = insides
        .drop(1)
        .dropLast(1)
        .withIndex()
        .joinToString("\n") { (j, c) ->
            val inJ = j + 1 // index of the circle in the list of part.insides
"""<clipPath id="clip-part$partIndex-$inJ">
${formatCircleOrLine(c, visibleRect, "clip-path=\"url(#clip-part$partIndex-$j)\"")}
</clipPath>""".trimIndent()
        }
    val insidesInMask =
        when (part.insides.size) {
            0 -> "<rect x=\"${visibleRect.left}\" y=\"${visibleRect.top}\" width=\"100%\" height=\"100%\" fill=\"white\"/>"
            1 -> formatCircleOrLine(insides.last(), visibleRect, "fill=\"white\"")
            else -> formatCircleOrLine(insides.last(), visibleRect, "clip-path=\"url(#clip-part$partIndex-${part.insides.size - 2})\" fill=\"white\"")
        }
    val outsidesInMask = outsides.joinToString("\n") {
        formatCircleOrLine(it, visibleRect, "fill=\"black\"")
    }
    val mask = """

$firstClipPath
$clipPaths
<mask id="mask-part$partIndex">
$insidesInMask
$outsidesInMask
</mask>
""".trimIndent()
    return mask
}

fun formatCircleOrLine(circle: CircleOrLine, visibleRect: Rect, postfix: String = ""): String =
    when (circle) {
        is Circle -> "<circle cx=\"${circle.x}\" cy=\"${circle.y}\" r=\"${circle.radius}\" $postfix/>"
        is Line -> {
            val pointClosestToScreenCenter = circle.project(visibleRect.center)
            val direction =  circle.directionVector
            val normal = circle.normalVector
            val diag = hypot(visibleRect.width, visibleRect.height)
            val farBack = pointClosestToScreenCenter - direction * diag
            val farForward = pointClosestToScreenCenter + direction * diag
            val farForwardIn = farForward + normal * diag
            val farBackIn = farBack + normal * diag
            val d = "M ${farBack.x} ${farBack.y} " +
                    "L ${farForward.x} ${farForward.y} " +
                    "L ${farForwardIn.x} ${farForwardIn.y} " +
                    "L ${farBackIn.x} ${farBackIn.y} " +
                    "z"
            "<path d=\"$d\" $postfix/>"
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

