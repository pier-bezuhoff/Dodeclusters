package domain.io

import androidx.compose.ui.graphics.Color
import data.Cluster
import data.geometry.Circle
import data.geometry.Line
import domain.ColorCssSerializer
import domain.ColorULongSerializer
import kotlinx.serialization.json.Json

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
actual fun parseDdc(content: String): Ddc {
    val jsDdc = loadYaml(content) as JsDdc
    println("js-yaml parsed yaml successfully")
    val ddc = Ddc(
        name = jsDdc.name ?: Ddc.DEFAULT_NAME,
        backgroundColor = jsDdc.backgroundColor?.parseColor() ?: Ddc.DEFAULT_BACKGROUND_COLOR,
        bestCenterX = jsDdc.bestCenterX ?: Ddc.DEFAULT_BEST_CENTER_X,
        bestCenterY = jsDdc.bestCenterY ?: Ddc.DEFAULT_BEST_CENTER_Y,
        shape = jsDdc.shape.parseShape() ?: Ddc.DEFAULT_SHAPE,
        drawTrace = jsDdc.drawTrace ?: Ddc.DEFAULT_DRAW_TRACE,
        chessboardPattern = jsDdc.chessboardPattern ?: Ddc.DEFAULT_CHESSBOARD_PATTERN,
        chessboardPatternStartsWhite = jsDdc.chessboardPatternStartsWhite ?: Ddc.DEFAULT_CHESSBOARD_PATTERN_STARTS_WHITE,
        content = jsDdc.content.map { jsFigure ->
            when {
                isCircleObject(jsFigure) -> {
                    val jsCircle = jsFigure as JsCircleFigure
                    Ddc.Token.Circle(
                        index = jsCircle.index,
                        x = jsCircle.x,
                        y = jsCircle.y,
                        radius = jsCircle.radius,
                        visible = jsCircle.visible ?: Ddc.DEFAULT_CIRCLE_VISIBLE,
                        filled = jsCircle.filled ?: Ddc.DEFAULT_CIRCLE_FILLED,
                        fillColor = jsCircle.fillColor?.parseColor() ?: Ddc.DEFAULT_CIRCLE_FILL_COLOR,
                        borderColor = jsCircle.borderColor?.parseColor() ?: Ddc.DEFAULT_CIRCLE_BORDER_COLOR,
                        rule = jsCircle.rule?.map { it.toInt() } ?: Ddc.DEFAULT_CIRCLE_RULE
                    )
                }
                isLineObject(jsFigure) -> {
                    val jsCircle = jsFigure as JsLineFigure
                    Ddc.Token.Line(
                        index = jsCircle.index,
                        a = jsCircle.a,
                        b = jsCircle.b,
                        c = jsCircle.c,
                        visible = jsCircle.visible ?: Ddc.DEFAULT_CIRCLE_VISIBLE,
                        borderColor = jsCircle.borderColor?.parseColor() ?: Ddc.DEFAULT_CIRCLE_BORDER_COLOR,
                        rule = jsCircle.rule?.map { it.toInt() } ?: Ddc.DEFAULT_CIRCLE_RULE
                    )
                }
                isClusterObject(jsFigure) -> {
                    val jsCluster = jsFigure as JsCluster
                    Ddc.Token.Cluster(
                        indices = jsCluster.indices.map { it.toInt() },
                        circles = jsCluster.circles.map {
                            when {
                                isCircleObject(it) -> {
                                    val circle = it as JsCircle
                                    Circle(circle.x, circle.y, circle.radius)
                                }
                                isLineObject(it) -> {
                                    val line = it as JsLine
                                    Line(line.a, line.b, line.c)
                                }
                                else ->
                                    throw IllegalArgumentException("Bad YAML: cannot parse\"${jsonStringify(it)}\" as CircleOrLine")
                            }
                        },
                        parts = jsCluster.parts.map { part ->
                            Cluster.Part(
                                insides = part.insides.map { it.toInt() }.toSet(),
                                outsides = part.outsides.map { it.toInt() }.toSet(),
                                fillColor = part.fillColor.parseColor() ?: Ddc.DEFAULT_CLUSTER_FILL_COLOR,
                                borderColor = part.borderColor?.parseColor() ?: Ddc.DEFAULT_CLUSTER_FILL_COLOR,
                            )
                        },
                        filled = jsCluster.filled ?: Ddc.DEFAULT_CLUSTER_FILLED,
                        rule = jsCluster.rule?.map { it.toInt() } ?: Ddc.DEFAULT_CLUSTER_RULE
                    )
                }
                else ->
                    throw IllegalArgumentException("Bad YAML: cannot parse \"${jsonStringify(jsFigure)}\" as a Ddc.Figure")
            }
        }
    )
    return ddc
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
actual fun parseOldDdc(content: String): OldDdc {
    val jsDdc = loadYaml(content) as JsDdc
    println("js-yaml parsed yaml successfully")
    val ddc = OldDdc(
        name = jsDdc.name ?: Ddc.DEFAULT_NAME,
        backgroundColor = jsDdc.backgroundColor?.parseColor() ?: Ddc.DEFAULT_BACKGROUND_COLOR,
        bestCenterX = jsDdc.bestCenterX ?: Ddc.DEFAULT_BEST_CENTER_X,
        bestCenterY = jsDdc.bestCenterY ?: Ddc.DEFAULT_BEST_CENTER_Y,
        shape = jsDdc.shape.parseShape() ?: Ddc.DEFAULT_SHAPE,
        drawTrace = jsDdc.drawTrace ?: Ddc.DEFAULT_DRAW_TRACE,
        content = jsDdc.content.map { jsFigure ->
            when {
                isCircleObject(jsFigure) -> {
                    val jsCircle = jsFigure as JsCircleFigure
                    OldDdc.Token.Circle(
                        index = jsCircle.index,
                        x = jsCircle.x,
                        y = jsCircle.y,
                        radius = jsCircle.radius,
                        visible = jsCircle.visible ?: Ddc.DEFAULT_CIRCLE_VISIBLE,
                        filled = jsCircle.filled ?: Ddc.DEFAULT_CIRCLE_FILLED,
                        fillColor = jsCircle.fillColor?.parseColor() ?: Ddc.DEFAULT_CIRCLE_FILL_COLOR,
                        borderColor = jsCircle.borderColor?.parseColor() ?: Ddc.DEFAULT_CIRCLE_BORDER_COLOR,
                        rule = jsCircle.rule?.map { it.toInt() } ?: Ddc.DEFAULT_CIRCLE_RULE
                    )
                }
                isClusterObject(jsFigure) -> {
                    val jsCluster = jsFigure as JsCluster
                    OldDdc.Token.Cluster(
                        indices = jsCluster.indices.map { it.toInt() },
                        circles = jsCluster.circles.map {
                            val circle = it as JsCircle
                            Circle(circle.x, circle.y, circle.radius)
                        },
                        parts = jsCluster.parts.map { part ->
                            Cluster.Part(
                                insides = part.insides.map { it.toInt() }.toSet(),
                                outsides = part.outsides.map { it.toInt() }.toSet(),
                                fillColor = part.fillColor.parseColor() ?: Ddc.DEFAULT_CLUSTER_FILL_COLOR,
                                borderColor = part.borderColor?.parseColor() ?: Ddc.DEFAULT_CLUSTER_FILL_COLOR,
                            )
                        },
                        filled = jsCluster.filled ?: Ddc.DEFAULT_CLUSTER_FILLED,
                        rule = jsCluster.rule?.map { it.toInt() } ?: Ddc.DEFAULT_CLUSTER_RULE
                    )
                }
                else ->
                    throw IllegalArgumentException("Bad YAML: cannot parse \"${jsonStringify(jsFigure)}\" as a Ddc.Figure")
            }
        }
    )
    return ddc
}

fun <S : JsAny, T> JsArray<S>.map(f: (S) -> T): List<T> =
    (0 until length).map { f(get(it)!!) }

fun JsString.parseColor(): Color? =
    try {
        Json.decodeFromString(ColorCssSerializer, '"' + toString() + '"')
    } catch (e: Exception) {
        try {
            Json.decodeFromString(ColorULongSerializer, toString())
        } catch (e: Exception) {
            null
        }
    }

fun JsString?.parseShape(): Shape? =
    this?.let {
        try {
            Shape.valueOf(toString())
        } catch (e: Exception) {
            null
        }
    }

@Suppress("UNUSED_PARAMETER")
fun isCircleObject(obj: JsAny): Boolean =
    js("'x' in obj")

@Suppress("UNUSED_PARAMETER")
fun isLineObject(obj: JsAny): Boolean =
    js("'a' in obj")

@Suppress("UNUSED_PARAMETER")
fun isClusterObject(obj: JsAny): Boolean =
    js("'circles' in obj")

@Suppress("UNUSED_PARAMETER")
fun jsonStringify(obj: JsAny): String =
    js("JSON.stringify(obj)")


typealias JsIntArray = JsArray<JsNumber>

// DTO model
external interface JsDdc : JsAny {
    val name: String?
    val backgroundColor: JsString?
    val bestCenterX: Float?
    val bestCenterY: Float?
    val shape: JsString?
    val drawTrace: Boolean?
    val chessboardPattern: Boolean?
    val chessboardPatternStartsWhite: Boolean?
    val content: JsArray<JsFigure>
}

external interface JsFigure : JsAny

external interface JsCircleFigure : JsFigure {
    val index: Int
    val x: Double
    val y: Double
    val radius: Double
    val visible: Boolean?
    val filled: Boolean?
    val fillColor: JsString?
    val borderColor: JsString?
    val rule: JsIntArray?
}

external interface JsLineFigure : JsFigure {
    val index: Int
    val a: Double
    val b: Double
    val c: Double
    val visible: Boolean?
    val borderColor: JsString?
    val rule: JsIntArray?
}

external interface JsCluster : JsFigure {
    val indices: JsIntArray
    val circles: JsArray<JsCircleOrLine>
    val parts: JsArray<JsClusterPart>
    val filled: Boolean?
    val rule: JsIntArray?
}

external interface JsCircleOrLine : JsAny

external interface JsCircle : JsCircleOrLine {
    val x: Double
    val y: Double
    val radius: Double
}

external interface JsLine : JsCircleOrLine {
    val a: Double
    val b: Double
    val c: Double
}

external interface JsClusterPart : JsAny {
    val insides: JsIntArray
    val outsides: JsIntArray
    val fillColor: JsString
    val borderColor: JsString?
}
