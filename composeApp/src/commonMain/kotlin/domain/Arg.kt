package domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.geometry.ImaginaryCircle
import data.geometry.Point
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class PrimitiveArgType {
    CIRCLE,
    LINE,
    IMAGINARY_CIRCLE,
    POINT,
    NULL,
    /** Raw, untyped indices [Ix] */
    INDICES,
}

/**
 * [Arg]ument that can be any of [possibleTypes]
 * @param[possibleTypes] order of types can correspond to their priority.
 * The list must not contain dupes.
 */
@Immutable
@Serializable
data class ArgType internal constructor(
    val possibleTypes: List<PrimitiveArgType>
) {
    internal constructor(vararg possibleTypes: PrimitiveArgType) :
        this(possibleTypes.toList())

    companion object {
        val CIRCLE = ArgType(PrimitiveArgType.CIRCLE)
        val POINT = ArgType(PrimitiveArgType.POINT)
        val INDICES = ArgType(PrimitiveArgType.INDICES)
        /** Circle, Line, Imaginary circle or Point */
        val CLIP = ArgType(
            PrimitiveArgType.POINT,
            PrimitiveArgType.CIRCLE, PrimitiveArgType.LINE, PrimitiveArgType.IMAGINARY_CIRCLE,
        )
        /** Circle, Line or Imaginary circle */
        val CLI = ArgType(
            PrimitiveArgType.CIRCLE, PrimitiveArgType.LINE, PrimitiveArgType.IMAGINARY_CIRCLE,
        )
        /** Line or Point */
        val LP = ArgType(
            PrimitiveArgType.POINT,
            PrimitiveArgType.LINE,
        )
    }
}

@Immutable
sealed interface Arg {
    val primitiveArgType: PrimitiveArgType

    data class Index(val index: Ix, override val primitiveArgType: PrimitiveArgType) : Arg
    data class PointXY(val x: Double, val y: Double) : Arg {
        override val primitiveArgType: PrimitiveArgType = PrimitiveArgType.POINT
        constructor(point: Point) : this(point.x, point.y)
        fun toOffset(): Offset =
            Offset(x.toFloat(), y.toFloat())
        fun toPoint(): Point =
            Point(x, y)
    }
    data class Indices(val indices: List<Ix>) : Arg {
        override val primitiveArgType: PrimitiveArgType = PrimitiveArgType.INDICES
    }

    infix fun isType(argType: ArgType): Boolean =
        primitiveArgType in argType.possibleTypes

    companion object {
        fun PointIndex(index: Ix) : Index =
            Index(index, PrimitiveArgType.POINT)
        fun CircleIndex(index: Ix) : Index =
            Index(index, PrimitiveArgType.CIRCLE)
        fun LineIndex(index: Ix) : Index =
            Index(index, PrimitiveArgType.LINE)
        fun ImaginaryCircleIndex(index: Ix) : Index =
            Index(index, PrimitiveArgType.IMAGINARY_CIRCLE)
    }
}

@Immutable
@Serializable
data class Signature(val argTypes: List<ArgType>) {
    constructor(vararg argTypes: ArgType) : this(argTypes.toList())
}

val SIGNATURE_1_POINT = Signature(ArgType.POINT)
val SIGNATURE_2_POINTS = Signature(ArgType.POINT, ArgType.POINT)
val SIGNATURE_2_CIRCLES = Signature(ArgType.CLI, ArgType.CLI)
val SIGNATURE_2_GENERALIZED_CIRCLES = Signature(ArgType.CLIP, ArgType.CLIP)
val SIGNATURE_3_GENERALIZED_CIRCLE = Signature(ArgType.CLIP, ArgType.CLIP, ArgType.CLIP)
val SIGNATURE_REAL_CIRCLE_AND_LINE_OR_POINT = Signature(ArgType.CIRCLE, ArgType.LP)
val SIGNATURE_INDICES_AND_CIRCLE = Signature(ArgType.INDICES, ArgType.CLI)
val SIGNATURE_INDICES_AND_POINT = Signature(ArgType.INDICES, ArgType.POINT)
val SIGNATURE_INDICES_AND_2_CIRCLES = Signature(ArgType.INDICES, ArgType.CLI, ArgType.CLI)
val SIGNATURE_INDICES_AND_2_POINTS = Signature(ArgType.INDICES, ArgType.POINT, ArgType.POINT)
val SIGNATURE_N_POINTS_PLACEHOLDER = Signature(ArgType.POINT)

