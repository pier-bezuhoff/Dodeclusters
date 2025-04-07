package domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.geometry.Point
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class PrimitiveArgType {
    CIRCLE,
    LINE,
    IMAGINARY_CIRCLE,
    POINT_XY,
    POINT_INDEX,
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
        val POINT = ArgType(PrimitiveArgType.POINT_INDEX, PrimitiveArgType.POINT_XY)
        val INDICES = ArgType(PrimitiveArgType.INDICES)
        /** Circle, Line, Imaginary circle or Point */
        val CLIP = ArgType(
            PrimitiveArgType.POINT_INDEX,
            PrimitiveArgType.CIRCLE, PrimitiveArgType.LINE, PrimitiveArgType.IMAGINARY_CIRCLE,
            PrimitiveArgType.POINT_XY,
        )
        /** Circle, Line or Imaginary circle */
        val CLI = ArgType(
            PrimitiveArgType.CIRCLE, PrimitiveArgType.LINE, PrimitiveArgType.IMAGINARY_CIRCLE,
        )
        /** Line or Point */
        val LP = ArgType(
            PrimitiveArgType.POINT_INDEX,
            PrimitiveArgType.LINE,
            PrimitiveArgType.POINT_XY,
        )
    }
}

@Immutable
sealed interface Arg {
    val primitiveArgType: PrimitiveArgType

    sealed interface Index : Arg {
        val index: Ix
    }
    // jb, sum types doko?
    /** Circle, Line, Imaginary circle or Point (i.e. anything BUT [Indices]) */
    sealed interface CLIP : Arg
    sealed interface CLI : CLIP, Index
    sealed interface LP : CLIP
    sealed interface Point : LP

    data class CircleIndex(override val index: Ix) : Index, CLI {
        override val primitiveArgType: PrimitiveArgType = PrimitiveArgType.CIRCLE
    }
    data class LineIndex(override val index: Ix) : Index, CLI, LP {
        override val primitiveArgType: PrimitiveArgType = PrimitiveArgType.LINE
    }
    data class ImaginaryCircleIndex(override val index: Ix) : Index, CLI {
        override val primitiveArgType: PrimitiveArgType = PrimitiveArgType.IMAGINARY_CIRCLE
    }
    data class PointIndex(override val index: Ix) : Point, Index, CLI {
        override val primitiveArgType: PrimitiveArgType = PrimitiveArgType.POINT_INDEX
    }
    data class PointXY(
        val x: Double,
        val y: Double
    ) : Point {
        override val primitiveArgType: PrimitiveArgType = PrimitiveArgType.POINT_XY
        constructor(point: data.geometry.Point) : this(point.x, point.y)
        fun toOffset(): Offset =
            Offset(x.toFloat(), y.toFloat())
        fun toPoint(): data.geometry.Point =
            Point(x, y)
    }
    data class Indices(val indices: List<Ix>) : Arg {
        override val primitiveArgType: PrimitiveArgType = PrimitiveArgType.INDICES
    }

    infix fun isType(argType: ArgType): Boolean =
        primitiveArgType in argType.possibleTypes
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

