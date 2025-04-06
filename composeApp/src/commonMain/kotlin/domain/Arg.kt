package domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.geometry.Point
import kotlinx.serialization.Serializable

/** NOTE: [Circle] includes lines and imaginary circles */
@Immutable
enum class ArgType {
    Point,
    Circle,
    CircleOrPoint,
    CircleAndPointIndices,
}

@Immutable
sealed class Arg(val argType: ArgType) {
    sealed class Point : Arg(ArgType.Point) {
        data class Index(val index: Ix) : Point()
        data class XY(val x: Double, val y: Double) : Point() {
            constructor(point: data.geometry.Point) : this(point.x, point.y)
            fun toOffset(): Offset =
                Offset(x.toFloat(), y.toFloat())
            fun toPoint(): data.geometry.Point =
                Point(x, y)
            companion object {
                fun fromOffset(offset: Offset): XY =
                    XY(offset.x.toDouble(), offset.y.toDouble())
            }
        }
    }
    data class CircleIndex(val index: Ix) : Arg(ArgType.Circle)
    sealed class CircleOrPoint : Arg(ArgType.CircleOrPoint) {
        data class CircleIndex(val index: Ix) : CircleOrPoint()
        sealed class Point : CircleOrPoint() {
            data class Index(val index: Ix) : Point()
            data class XY(val x: Double, val y: Double) : Point() {
                constructor(point: data.geometry.Point) : this(point.x, point.y)
                fun toOffset(): Offset =
                    Offset(x.toFloat(), y.toFloat())
                fun toPoint(): data.geometry.Point =
                    Point(x, y)
                companion object {
                    fun fromOffset(offset: Offset): XY =
                        XY(offset.x.toDouble(), offset.y.toDouble())
                }
            }
        }
    }
    data class CircleAndPointIndices(
        val circleIndices: List<Ix>,
        val pointIndices: List<Ix> = emptyList()
    ) : Arg(ArgType.CircleAndPointIndices)
}

@Immutable
@Serializable
data class Signature(val argTypes: List<ArgType>) {
    constructor(vararg argTypes: ArgType) : this(argTypes.toList())
}

val SIGNATURE_1_POINT = Signature(ArgType.Point)
val SIGNATURE_2_POINTS = Signature(ArgType.Point, ArgType.Point)
val SIGNATURE_2_CIRCLES = Signature(ArgType.Circle, ArgType.Circle)
val SIGNATURE_CIRCLE_AND_CIRCLE_OR_POINT = Signature(ArgType.Circle, ArgType.CircleOrPoint)
val SIGNATURE_INDEXED_AND_CIRCLE = Signature(ArgType.CircleAndPointIndices, ArgType.Circle)
val SIGNATURE_INDEXED_AND_POINT = Signature(ArgType.CircleAndPointIndices, ArgType.Point)
val SIGNATURE_2_GENERALIZED_CIRCLES = Signature(ArgType.CircleOrPoint, ArgType.CircleOrPoint)
val SIGNATURE_3_GENERALIZED_CIRCLE = Signature(ArgType.CircleOrPoint, ArgType.CircleOrPoint, ArgType.CircleOrPoint)
val SIGNATURE_INDEXED_AND_2_CIRCLES = Signature(ArgType.CircleAndPointIndices, ArgType.Circle, ArgType.Circle)
val SIGNATURE_INDEXED_AND_2_POINTS = Signature(ArgType.CircleAndPointIndices, ArgType.Point, ArgType.Point)
val SIGNATURE_N_POINTS_PLACEHOLDER = Signature(ArgType.Point)

