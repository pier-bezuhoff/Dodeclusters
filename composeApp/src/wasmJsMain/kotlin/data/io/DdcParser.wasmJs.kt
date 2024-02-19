package data.io

import androidx.compose.ui.graphics.Color
import data.Circle
import data.Cluster

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
actual fun parseDdc(content: String): Ddc {
    fun <S : JsAny, T> JsArray<S>.map(f: (S) -> T): List<T> =
        (0 until length).map { f(get(it)!!) }
    val jsDdc = loadYaml(content) as JsDdc
    val ddc = Ddc(
        param1 = jsDdc.param1 ?: "abc",
        content = jsDdc.content.map { jsFigure ->
            when {
                isCircleObject(jsFigure) -> {
                    println("circle: $jsFigure")
                    val jsCircle = jsFigure as JsCircleFigure
                    Ddc.Token.Circle(
                        circle = jsCircle.circle,
                        x = jsCircle.x,
                        y = jsCircle.y,
                        r = jsCircle.r,
                        visible = jsCircle.visible ?: false,
                        filled = jsCircle.filled ?: true,
                        fillColor = jsCircle.fillColor?.let { Color(value = it.toString().toULong()) },
                        borderColor = jsCircle.borderColor?.let { Color(value = it.toString().toULong()) },
                        rule = jsCircle.rule?.map { it.toInt() } ?: emptyList()
                    )
                }
                isClusterObject(jsFigure) -> {
                    println("cluster: $jsFigure")
                    val jsCluster = jsFigure as JsCluster
                    Ddc.Token.Cluster(
                        cluster = jsCluster.cluster.map { it.toInt() },
                        circles = jsCluster.circles.map {
                            Circle(it.x, it.y, it.r)
                        },
                        parts = jsCluster.parts.map {
                            Cluster.Part(
                                insides = it.insides.map { it.toInt() }.toSet(),
                                outsides = it.insides.map { it.toInt() }.toSet(),
                                fillColor = Color(value = it.fillColor.toString().toULong()),
                            )
                        },
                        filled = jsCluster.filled ?: true,
                        rule = jsCluster.rule?.map { it.toInt() } ?: emptyList()
                    )
                }
                else -> throw IllegalArgumentException("Bad YAML: cannot parse \"$jsFigure\" as a Ddc.Figure")
            }
        }
    )
    return ddc
}

fun isCircleObject(obj: JsAny): Boolean =
    js("'circle' in obj")

fun isClusterObject(obj: JsAny): Boolean =
    js("'cluster' in obj")

typealias JsIntArray = JsArray<JsNumber>

external interface JsDdc : JsAny {
    val param1: String?
    val content: JsArray<JsFigure>
}

external interface JsFigure : JsAny

external interface JsCircleFigure : JsFigure {
    val circle: Int
    val x: Double
    val y: Double
    val r: Double
    val visible: Boolean?
    val filled: Boolean?
    val fillColor: JsString?
    val borderColor: JsString?
    val rule: JsIntArray?
}

external interface JsCluster : JsFigure {
    val cluster: JsIntArray
    val circles: JsArray<JsCircle>
    val parts: JsArray<JsClusterPart>
    val filled: Boolean?
    val rule: JsIntArray?
}

external interface JsCircle : JsAny {
    val x: Double
    val y: Double
    val r: Double
}

external interface JsClusterPart : JsAny {
    val insides: JsIntArray
    val outsides: JsIntArray
    val fillColor: JsString
}
