package domain.io

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.GCircle
import core.geometry.Line
import core.geometry.Point
import domain.settings.ChessboardPattern
import domain.ColorCssSerializer
import domain.Ix
import domain.cluster.Constellation
import kotlinx.serialization.json.Json
import ui.region2path
import kotlin.math.hypot

// MAYBE: implement https://stackoverflow.com/a/4756461/7143065
private fun svgOpen(width: Float, height: Float) =
    """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0.0 0.0 $width $height">"""
private fun title(name: String) =
    "<title>$name</title>"
private const val desc = """<desc>Created in Dodeclusters.</desc>"""
private const val highlightClass = "highlightable"
// kinda funi but since lines are 1px wide it's hard to hover over them intentionally
private const val defs = """<defs>
    <style><![CDATA[
        .$highlightClass:hover { filter: brightness(150%); }
    ]]></style>
</defs>"""
private const val svgClose = "</svg>"

// NOTE: "For reliable results cross-browser, use numbers with no more
//  than 2 digits after the decimal and four digits before it." -- im gonna ignore this >.<
fun constellation2svg(
    constellation: Constellation,
    objects: List<GCircle?>,
    freeObjectIndices: Set<Ix>,
    width: Float, height: Float,
    encodeCirclesAndPoints: Boolean = true,
    chessboardPattern: ChessboardPattern = ChessboardPattern.NONE,
    chessboardCellColor: Color = Color.White,
    name: String? = null,
): String = buildString {
    val visibleRect = Rect(0f, 0f, width, height)
    val inflatedVisibleRect = visibleRect.inflate(100f)
    appendLine(svgOpen(width, height))
    if (name != null)
        appendLine(title(name))
    appendLine(desc)
//    appendLine(defs)
    constellation.backgroundColor?.let {
        val bg = Json.encodeToString(ColorCssSerializer, it).trim('"')
        appendLine(formatRect(visibleRect, bg))
    }
    when (chessboardPattern) {
        ChessboardPattern.NONE -> {}
        ChessboardPattern.STARTS_COLORED -> {
            appendLine(
                chessboardPath(
                    objects
                        .filterIndexed { ix, _ -> ix !in constellation.phantoms }
                        .filterIsInstance<CircleOrLine>()
                    ,
                    color = chessboardCellColor,
                    visibleRect = visibleRect,
                    startsColored = true
                )
            )
        }
        ChessboardPattern.STARTS_TRANSPARENT -> {
            appendLine(
                chessboardPath(
                    objects
                        .filterIndexed { ix, _ -> ix !in constellation.phantoms }
                        .filterIsInstance<CircleOrLine>()
                    ,
                    color = chessboardCellColor,
                    visibleRect = visibleRect,
                    startsColored = false
                )
            )
        }
    }
    val circlesOrLines = objects.map { it as? CircleOrLine }
    constellation.parts.forEach { part ->
        val fillColorString = Json.encodeToString(ColorCssSerializer, part.fillColor).trim('"')
//                val strokeColorString = Json.encodeToString(ColorCssSerializer, part.borderColor).trim('"')
        val path = region2path(circlesOrLines, part, visibleRect)
        // NOTE: path.toSvg is bugged for elliptic/circular arcs (not yet implemented)
        //  https://youtrack.jetbrains.com/issue/CMP-7418/Path.toSvg-is-completely-broken
        val pathData = path.toCircularSvg()
        appendLine("""<path d="$pathData" fill="$fillColorString"/>""")
    }
    if (encodeCirclesAndPoints) {
        // colors mimic EditorCanvas setup
        val circleColor = Color(0xFF_D4BE51).copy(alpha = 0.6f) // accentColorDark
        val freeCircleColor = Color(0xFF_F5BD6F) // highAccentColorDark
        val pointColor = Color(0xFF_D4BE51).copy(alpha = 0.7f) // accentColorDark
        val pointRadius = 5f
        val highlightClassString = "" //"""class="$highlightClass" """
        objects.forEachIndexed { ix, o ->
            if (ix !in constellation.phantoms) {
                val color = when {
                    o is Point -> pointColor // points cant be colored for now
                    ix in freeObjectIndices -> constellation.objectColors[ix] ?: freeCircleColor
                    else -> constellation.objectColors[ix] ?: circleColor
                }
                val colorString = Json.encodeToString(ColorCssSerializer, color).trim('"')
                when (o) {
                    is CircleOrLine -> appendLine(
                        formatCircleOrLineStroke(o, inflatedVisibleRect,
                            stroke = colorString,
                            prefix = highlightClassString
                        )
                    )
                    is Point -> appendLine(
                        """<circle ${highlightClassString}cx="${o.x}" cy="${o.y}" r="$pointRadius" fill="$colorString"/>"""
                    )
                    else -> {}
                }
            }
        }
    }
    appendLine(svgClose)
}

private fun chessboardPath(
    circles: List<CircleOrLine>,
    color: Color,
    visibleRect: Rect,
    startsColored: Boolean = true,
): String = buildString {
    appendLine("<path d=\"")
    if (startsColored)
        appendLine(
            "M ${visibleRect.left} ${visibleRect.top} " +
                    "L ${visibleRect.right} ${visibleRect.top} " +
                    "L ${visibleRect.right} ${visibleRect.bottom} " +
                    "L ${visibleRect.left} ${visibleRect.bottom} " +
                    "z "
        )
    for (circle in circles) {
        when (circle) {
            is Circle -> {
                // reference: https://stackoverflow.com/a/10477334
                val r = circle.radius
                appendLine(
                    "M ${circle.x} ${circle.y} " +
                            "m $r 0 " +
                            "a $r $r 0 1 0 ${-2*r} 0 " +
                            "a $r $r 0 1 0 ${2*r} 0 " +
                            "z "
                )
            }
            is Line -> {
                val pointClosestToScreenCenter = circle.project(visibleRect.center)
                val direction = circle.directionVector
                val normal = circle.normalVector
                val diagonal = hypot(visibleRect.width, visibleRect.height)
                val farBack = pointClosestToScreenCenter - direction * diagonal
                val farForward = pointClosestToScreenCenter + direction * diagonal
                val farForwardIn = farForward + normal * diagonal
                val farBackIn = farBack + normal * diagonal
                appendLine(
                    "M ${farBack.x} ${farBack.y} " +
                            "L ${farForward.x} ${farForward.y} " +
                            "L ${farForwardIn.x} ${farForwardIn.y} " +
                            "L ${farBackIn.x} ${farBackIn.y} " +
                            "z "
                )
            }
        }
    }
    val colorString = Json.encodeToString(ColorCssSerializer, color).trim('"')
    append("""" fill="$colorString" fill-rule="evenodd"/>""")
}

private fun formatRect(visibleRect: Rect, fill: String = "black", prefix: String = "", postfix: String = ""): String {
    val pre = if (prefix.isBlank()) "" else "$prefix "
    return """<rect ${pre}x="${visibleRect.left}" y="${visibleRect.top}" width="100%" height="100%" fill="$fill" $postfix/>"""
}

private fun formatCircleOrLineFill(circle: CircleOrLine, visibleRect: Rect, fill: String = "black", prefix: String = "", postfix: String = ""): String {
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

private fun formatCircleOrLineStroke(circle: CircleOrLine, visibleRect: Rect, stroke: String = "black", fill: String = "none", prefix: String = "", postfix: String = ""): String {
    val pre = if (prefix.isBlank()) "" else "$prefix "
    return when (circle) {
        is Circle -> """<circle ${pre}cx="${circle.x}" cy="${circle.y}" r="${circle.radius}" fill="$fill" stroke="$stroke" $postfix/>"""
        is Line -> {
            val pointClosestToScreenCenter = circle.project(visibleRect.center)
            val direction = circle.directionVector
            val diagonal = hypot(visibleRect.width, visibleRect.height)
            val farBack = pointClosestToScreenCenter - direction * diagonal
            val farForward = pointClosestToScreenCenter + direction * diagonal
            val d = "M ${farBack.x} ${farBack.y} " +
                    "L ${farForward.x} ${farForward.y} "
            """<path ${pre}d="$d" stroke="$stroke" $postfix/>"""
        }
    }
}