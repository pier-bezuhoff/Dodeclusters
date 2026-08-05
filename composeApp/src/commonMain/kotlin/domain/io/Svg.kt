package domain.io

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.github.ajalt.colormath.model.RGB
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.ConcreteArcPath
import core.geometry.GCircle
import core.geometry.GCircleOrConcreteAcPath
import core.geometry.Line
import core.geometry.Point
import domain.ColorAsCss
import domain.ColorCssSerializer
import domain.model.ChessboardPattern
import domain.model.SaveState
import domain.model.Styling
import kotlinx.serialization.json.Json
import ui.region2path
import ui.theme.CustomColors
import ui.toPath
import kotlin.math.hypot
import kotlin.text.appendLine

// MAYBE: implement https://stackoverflow.com/a/4756461/7143065
//  <svg ... role="img" aria-label="{title + description}" >
private fun svgOpen(width: Float, height: Float) =
    """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0.0 0.0 $width $height">"""
private fun title(name: String) =
    "<title>$name</title>"
private const val desc =
    """<desc>Created in Dodeclusters.</desc>"""
private const val highlightClass = "highlightable"
private const val svgClose = "</svg>"

// NOTE: "For reliable results cross-browser, use numbers with no more
//  than 2 digits after the decimal and four digits before it." -- im gonna ignore this >.<
fun saveStateAsSvg(
    saveState: SaveState,
    width: Float, height: Float,
    customColors: CustomColors,
    encodeCirclesAndPoints: Boolean = true,
    name: String? = null,
): String = buildString {
    val visibleRect = Rect(0f, 0f, width, height)
    val inflatedVisibleRect = visibleRect.inflate(100f)
    val translation = Offset(
        x = width/2f - saveState.center.x,
        y = height/2f - saveState.center.y,
    )
    val translatedObjects: List<GCircleOrConcreteAcPath?> = saveState.objects.map { o ->
        when (o) {
            is ConcreteArcPath -> o.translated(translation)
            is GCircle -> o.translated(translation)
            null -> null
        }
    }
    appendLine(svgOpen(width, height))
    if (name != null)
        appendLine(title(name))
    appendLine(desc)
    val labelClasses = appendDefs(saveState.styling, customColors.defaultFreePointColor)
    saveState.backgroundColor?.let {
        appendLine(formatRect(visibleRect, it.asCssString()))
    }
    appendChessboard(saveState, visibleRect, translatedObjects)
    appendRegions(saveState, visibleRect, translatedObjects)
    appendArcPaths(saveState, translatedObjects, customColors.defaultArcPathColor)
    if (encodeCirclesAndPoints) {
        // colors mimic EditorCanvas setup
        appendGCircles(saveState, inflatedVisibleRect, translatedObjects,
            circleColor = customColors.defaultCircleColor,
            freeCircleColor = customColors.defaultFreeCircleColor,
            pointColor = customColors.defaultPointColor,
            freePointColor = customColors.defaultFreePointColor,
        )
    }
    appendLabels(translatedObjects, saveState.styling, labelClasses)
    appendLine(svgClose)
}

private fun StringBuilder.appendDefs(
    styling: Map<Int, Styling>,
    freePointColor: Color,
): Map<Int, String> {
    val ix2color: Map<Int, Color> = styling.entries.mapNotNull { (ix, style) ->
        if (style.label != null)
            ix to (style.borderColor ?: freePointColor)
        else null
    }.toMap()
    val color2class: Map<Color, String> = ix2color.values.toSet().mapIndexed { index, color ->
        val cls = "label-${index+1}"
        color to cls
    }.toMap()
    val labelClasses: Map<Int, String> = ix2color.mapValues { (_, color) -> color2class[color]!! }
    appendLine("<defs>")
    appendLine(value =
"""    <style><![CDATA["""
    )
    // kinda funi but since lines are 1px wide it's hard to hover over them intentionally
    appendLine(value =
"""
        .$highlightClass:hover { filter: brightness(150%); }
         
        text.label { font: icon; }
"""
    )
    for ((color, cls) in color2class) {
        appendLine(value =
"""        text.label.$cls { fill: ${color.asCssString()}; } """
        )
    }
    appendLine(value =
"""    ]]></style>"""
    )
    appendLine("</defs>")
    return labelClasses
}

private fun StringBuilder.appendChessboard(
    saveState: SaveState,
    visibleRect: Rect,
    translatedObjects: List<GCircleOrConcreteAcPath?>,
) {
    if (saveState.chessboardColor != null) {
        when (saveState.chessboardPattern) {
            ChessboardPattern.NONE -> {}
            ChessboardPattern.STARTS_COLORED -> {
                appendLine(
                    chessboardPath(
                        translatedObjects
                            .filterIndexed { ix, _ ->
                                saveState.styling[ix]?.isPhantom != true
                            }
                            .filterIsInstance<CircleOrLine>()
                        ,
                        color = saveState.chessboardColor,
                        visibleRect = visibleRect,
                        startsColored = true
                    )
                )
            }
            ChessboardPattern.STARTS_TRANSPARENT -> {
                appendLine(
                    chessboardPath(
                        translatedObjects
                            .filterIndexed { ix, _ ->
                                saveState.styling[ix]?.isPhantom != true
                            }
                            .filterIsInstance<CircleOrLine>()
                        ,
                        color = saveState.chessboardColor,
                        visibleRect = visibleRect,
                        startsColored = false
                    )
                )
            }
        }
    }
}

private fun StringBuilder.appendRegions(
    saveState: SaveState,
    visibleRect: Rect,
    translatedObjects: List<GCircleOrConcreteAcPath?>,
) {
    saveState.regions.forEach { region ->
        val fillColorString = region.fillColor.asCssString()
//        val strokeColorString = region.borderColor.asCssString()
        val path = region2path(region, translatedObjects, visibleRect)
        // NOTE: path.toSvg is bugged for elliptic/circular arcs (not yet implemented)
        //  https://youtrack.jetbrains.com/issue/CMP-7418/Path.toSvg-is-completely-broken
        val pathData = path.toCircularSvg()
        appendLine("""<path d="$pathData" fill="$fillColorString"/>""")
    }
}

private fun StringBuilder.appendArcPaths(
    saveState: SaveState,
    translatedObjects: List<GCircleOrConcreteAcPath?>,
    defaultArcPathColor: Color,
) {
    translatedObjects.forEachIndexed { ix, o ->
        when (o) {
            is ConcreteArcPath if (saveState.styling[ix]?.isPhantom != true) -> {
                val borderColor = saveState.styling[ix]?.borderColor ?: defaultArcPathColor
                val fillColor = saveState.styling[ix]?.fillColor
                appendLine(
                    formatArcPath(o, borderColor, fillColor)
                )
            }
            else -> {}
        }
    }
}

private fun StringBuilder.appendGCircles(
    saveState: SaveState,
    inflatedVisibleRect: Rect,
    translatedObjects: List<GCircleOrConcreteAcPath?>,
    circleColor: Color,
    freeCircleColor: Color,
    pointColor: Color,
    freePointColor: Color,
) {
    val pointRadius = 5f
    val highlightClassString = "" //"""class="$highlightClass" """
    val freeObjectIndices = saveState.objects.indices
        .filter { ix -> saveState.expressions[ix] == null }
    translatedObjects.forEachIndexed { ix, o ->
        if (saveState.styling[ix]?.isPhantom != true) {
            val color = saveState.styling[ix]?.borderColor ?: when {
                o is Point -> if (ix in freeObjectIndices) freePointColor else pointColor
                ix in freeObjectIndices -> freeCircleColor
                else -> circleColor
            }
            val colorString = color.asCssString()
            when (o) {
                is CircleOrLine -> appendLine(
                    formatCircleOrLineStroke(o,
                        visibleRect = inflatedVisibleRect,
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

private const val charWidth = 10f
private const val charHeight = 20f
private fun StringBuilder.appendLabels(
    translatedObjects: List<GCircleOrConcreteAcPath?>,
    styling: Map<Int, Styling>,
    labelClasses: Map<Int, String>,
) {
    for ((ix, cls) in labelClasses) {
        val point = translatedObjects[ix] as? Point ?: continue
        val style = styling[ix] ?: continue
        val label = style.label ?: continue
        // on canvas y is topLeft and x centers the texts
        val x = point.x - label.content.length * charWidth/2f
        val y = point.y + charHeight
        appendLine(value =
            """<text x="$x" y="$y" class="label $cls">${label.content}</text>"""
        )
    }
}

/** @return css color string without quotes */
private fun Color.asCssString(): String =
    RGB(red, green, blue, alpha).toHex()

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
    append("""" fill="${color.asCssString()}" fill-rule="evenodd"/>""")
}

private fun formatRect(
    visibleRect: Rect,
    fill: String = "black",
    prefix: String = "",
    postfix: String = "",
): String {
    val pre = if (prefix.isBlank()) "" else "$prefix "
    return """<rect ${pre}x="${visibleRect.left}" y="${visibleRect.top}" width="100%" height="100%" fill="$fill" $postfix/>"""
}

private fun formatCircleOrLineFill(
    circle: CircleOrLine,
    visibleRect: Rect,
    fill: String = "black",
    prefix: String = "",
    postfix: String = "",
): String {
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

private fun formatCircleOrLineStroke(
    circle: CircleOrLine,
    visibleRect: Rect,
    stroke: String,
    fill: String = "none",
    strokeWidth: Int = 2,
    prefix: String = "",
    postfix: String = "",
): String {
    val pre = if (prefix.isBlank()) "" else "$prefix "
    return when (circle) {
        is Circle -> """<circle ${pre}cx="${circle.x}" cy="${circle.y}" r="${circle.radius}" fill="$fill" stroke="$stroke" stroke-width="$strokeWidth" $postfix/>"""
        is Line -> {
            val pointClosestToScreenCenter = circle.project(visibleRect.center)
            val direction = circle.directionVector
            val diagonal = hypot(visibleRect.width, visibleRect.height)
            val farBack = pointClosestToScreenCenter - direction * diagonal
            val farForward = pointClosestToScreenCenter + direction * diagonal
            val d = "M ${farBack.x} ${farBack.y} " +
                    "L ${farForward.x} ${farForward.y} "
            """<path ${pre}d="$d" stroke="$stroke" stroke-width="$strokeWidth" $postfix/>"""
        }
    }
}

private fun formatArcPath(
    concreteArcPath: ConcreteArcPath,
    borderColor: Color,
    fillColor: Color?,
    strokeWidth: Int = 2,
    prefix: String = "",
    postfix: String = "",
): String {
    val pre = if (prefix.isBlank()) "" else "$prefix "
    val strokeString = """stroke="${borderColor.asCssString()}""""
    val fillString = """fill="${fillColor?.asCssString() ?: "none"}""""
    val d = concreteArcPath.toPath().toCircularSvg()
    return """<path ${pre}d="$d" fill-rule="evenodd" $strokeString $fillString stroke-width="$strokeWidth" fill-opacity="1.0" $postfix/>"""
}