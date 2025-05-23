package domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.geometry.GCircle
import data.geometry.Point
import kotlinx.serialization.Serializable

/**
 * A bit contrived, basically the value of [Arg] type X has an associated
 * [type] X.Companion (plus weird sum types magic)
 */
@Immutable
sealed interface Arg {
    @Immutable
    @Serializable
    sealed interface Type {
        // some sum types
        sealed interface Index : Type
        /** [Circle] + [Line] + [ImaginaryCircle] + [Point] */
        sealed interface CLIP : Type
        sealed interface CLI : Index, CLIP
        sealed interface Circle : CLI
        sealed interface Line : CLI
        sealed interface ImaginaryCircle : CLI
        sealed interface Point : CLIP
    }

    val type: Type

    sealed interface Index : Arg {
        val index: Ix
    }
    // jb, sum types doko?
    /** Circle, Line, Imaginary circle or Point (i.e. anything BUT [Indices]) */
    sealed interface CLIP : Arg
    sealed interface CLI : CLIP, Index
    sealed interface LP : CLIP
    sealed interface Circle : CLI
    sealed interface Line : CLI, LP
    sealed interface ImaginaryCircle : CLI
    sealed interface Point : LP
    /** non-indexed [Point] with fixed coordinates (XY or infinity) */
    sealed interface FixedPoint : Point {
        fun toPoint(): data.geometry.Point
    }

    data class CircleIndex(override val index: Ix) : Circle, Index {
        override val type: Type.Circle = Companion
        @Serializable
        companion object : Type.Circle
    }

    data class LineIndex(override val index: Ix) : Line, Index {
        override val type: Type.Line = Companion
        @Serializable
        companion object : Type.Line
    }

    data class ImaginaryCircleIndex(override val index: Ix) : ImaginaryCircle, Index {
        override val type: Type.ImaginaryCircle = Companion
        @Serializable
        companion object : Type.ImaginaryCircle
    }

    data class PointIndex(override val index: Ix) : Point, Index {
        override val type: Type.Point = Companion
        @Serializable
        companion object : Type.Point, Type.Index
    }

    data class PointXY(
        val x: Double,
        val y: Double,
    ) : FixedPoint {
        override val type: Type.Point = Companion

        constructor(point: data.geometry.Point) :
            this(point.x, point.y)

        fun toOffset(): Offset =
            Offset(x.toFloat(), y.toFloat())

        override fun toPoint(): data.geometry.Point =
            Point(x, y)

        @Serializable
        companion object : Type.Point
    }

    // value - type puning, InfinitePoint is both a type and a singular value of this type
    @Serializable
    object InfinitePoint : FixedPoint, Type.Point {
        override val type: Type.Point = InfinitePoint

        override fun toPoint(): data.geometry.Point =
            data.geometry.Point.CONFORMAL_INFINITY
    }

    data class Indices(val indices: List<Ix>) : Arg {
        override val type: Type = Companion
        @Serializable
        companion object : Type
    }

    infix fun isType(argType: ArgType): Boolean =
        type in argType.possibleTypes

    companion object {
        fun IndexOf(index: Ix, obj: GCircle): Arg.Index =
            when (obj) {
                is data.geometry.Circle -> CircleIndex(index)
                is data.geometry.Line -> LineIndex(index)
                is data.geometry.ImaginaryCircle -> ImaginaryCircleIndex(index)
                is data.geometry.Point -> PointIndex(index)
            }
    }
}

/**
 * [Arg]ument that can be any of [possibleTypes]. Not to be confused with
 * [Arg.Type] which are primitive types.
 * @param[possibleTypes] order of types *can* correspond to their priority.
 * The list must not contain dupes.
 */
@Immutable
@Serializable
data class ArgType(
    val possibleTypes: List<Arg.Type>
) {
    constructor(vararg possibleTypes: Arg.Type) :
        this(possibleTypes.toList())

    companion object {
        val CIRCLE = ArgType(Arg.CircleIndex)
        val POINT = ArgType(Arg.PointIndex, Arg.PointXY)
        val POINT_OR_INFINITY = ArgType(Arg.PointIndex, Arg.InfinitePoint, Arg.PointXY)
        val INDICES = ArgType(Arg.Indices)
        /** Circle, Line, Imaginary circle or Point */
        val CLIP = ArgType(
            Arg.PointIndex,
            Arg.CircleIndex, Arg.LineIndex, Arg.ImaginaryCircleIndex,
            Arg.PointXY,
        )
        /** Circle, Line or Imaginary circle */
        val CLI = ArgType(
            Arg.CircleIndex, Arg.LineIndex, Arg.ImaginaryCircleIndex,
        )
        /** Line or Point */
        val LP = ArgType(
            Arg.PointIndex,
            Arg.LineIndex,
            Arg.PointXY,
        )
        /** Accept nothing, used for ArcPath */
        val NOTHING = ArgType()
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
val SIGNATURE_N_POINTS_PLACEHOLDER = Signature(ArgType.NOTHING)
