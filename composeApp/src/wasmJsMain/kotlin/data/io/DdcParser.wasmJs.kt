package data.io

import androidx.compose.ui.graphics.Color
import data.Circle
import data.Cluster
import kotlinx.serialization.json.Json
import utils.ColorCssSerializer
import utils.ColorULongSerializer

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
actual fun parseDdc(content: String): Ddc {

    fun <S : JsAny, T> JsArray<S>.map(f: (S) -> T): List<T> =
        (0 until length).map { f(get(it)!!) }

    fun JsString.parseColor(): Color =
        try {
            Json.decodeFromString(ColorCssSerializer, '"' + toString() + '"')
        } catch (e: Exception) {
            try {
                Json.decodeFromString(ColorULongSerializer, toString())
            } catch (e: Exception) {
                Color.Black
            }
        }

    val jsDdc = loadYaml(content) as JsDdc
    println("js-yaml parsed yaml successfully")
    val ddc = Ddc(
        param1 = jsDdc.param1 ?: Ddc.DEFAULT_PARAM1,
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
                isClusterObject(jsFigure) -> {
                    val jsCluster = jsFigure as JsCluster
                    Ddc.Token.Cluster(
                        indices = jsCluster.indices.map { it.toInt() },
                        circles = jsCluster.circles.map {
                            Circle(it.x, it.y, it.radius)
                        },
                        parts = jsCluster.parts.map { part ->
                            Cluster.Part(
                                insides = part.insides.map { it.toInt() }.toSet(),
                                outsides = part.outsides.map { it.toInt() }.toSet(),
                                fillColor = part.fillColor.parseColor()
                            )
                        },
                        filled = jsCluster.filled ?: Ddc.DEFAULT_CLUSTER_FILLED,
                        rule = jsCluster.rule?.map { it.toInt() } ?: Ddc.DEFAULT_CLUSTER_RULE
                    )
                }
                else -> throw IllegalArgumentException("Bad YAML: cannot parse \"$jsFigure\" as a Ddc.Figure")
            }
        }
    )
    return ddc
}

fun isCircleObject(obj: JsAny): Boolean =
    js("'x' in obj")

fun isClusterObject(obj: JsAny): Boolean =
    js("'circles' in obj")


// DTO model
typealias JsIntArray = JsArray<JsNumber>

external interface JsDdc : JsAny {
    val param1: String?
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

external interface JsCluster : JsFigure {
    val indices: JsIntArray
    val circles: JsArray<JsCircle>
    val parts: JsArray<JsClusterPart>
    val filled: Boolean?
    val rule: JsIntArray?
}

external interface JsCircle : JsAny {
    val x: Double
    val y: Double
    val radius: Double
}

external interface JsClusterPart : JsAny {
    val insides: JsIntArray
    val outsides: JsIntArray
    val fillColor: JsString
}
