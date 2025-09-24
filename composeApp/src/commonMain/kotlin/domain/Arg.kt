package domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import core.geometry.GCircle
import core.geometry.Point
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A bit contrived, basically the value of [Arg] type `X` has an associated
 * [Arg.type] `X.Companion` : [Arg.Type] (plus weird sum types magic)
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
        fun toPoint(): core.geometry.Point
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

        constructor(point: core.geometry.Point) :
            this(point.x, point.y)

        fun toOffset(): Offset =
            Offset(x.toFloat(), y.toFloat())

        override fun toPoint(): core.geometry.Point =
            Point(x, y)

        @Serializable
        companion object : Type.Point
    }

    // value - type puning, InfinitePoint is both a type and a singular value of this type
    /** Prefer using [PointIndex] as a value instead */
    @Serializable
    object InfinitePoint : FixedPoint, Type.Point {
        override val type: Type.Point = InfinitePoint

        override fun toPoint(): core.geometry.Point =
            core.geometry.Point.CONFORMAL_INFINITY
    }

    data class Indices(val indices: List<Ix>) : Arg {
        override val type: Type = Companion
        @Serializable
        companion object : Type
    }

    infix fun isType(argType: ArgType): Boolean =
        type in argType.possibleTypes

    companion object {
        @Suppress("FunctionName")
        fun IndexOf(index: Ix, obj: GCircle): Arg.Index =
            when (obj) {
                is core.geometry.Circle -> CircleIndex(index)
                is core.geometry.Line -> LineIndex(index)
                is core.geometry.ImaginaryCircle -> ImaginaryCircleIndex(index)
                is core.geometry.Point -> PointIndex(index)
            }

        fun testInequality(arg1: Arg, arg2: Arg): Boolean =
            when {
                arg1 is Indices && arg2 is Indices -> {
                    val set1 = arg1.indices.toSet()
                    val set2 = arg2.indices.toSet()
                    !set1.containsAll(set2) && !set2.containsAll(set1)
                }
                arg1 is Indices && arg2 is Index ->
                    arg1.indices != listOf(arg2.index)
//                    arg2.index !in arg1.indices
                arg2 is Indices && arg1 is Index ->
//                    arg1.index !in arg2.indices
                    arg2.indices != listOf(arg1.index)
                else ->
                    arg1 != arg2
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
        val FINITE_POINT = ArgType(Arg.PointIndex, Arg.PointXY)
        val POINT = ArgType(Arg.PointIndex, Arg.InfinitePoint, Arg.PointXY)
        val INDICES = ArgType(Arg.Indices)
        /** Circle, Line, Imaginary circle or Point */
        val CLIP = ArgType(
            Arg.PointIndex,
            Arg.CircleIndex, Arg.LineIndex, Arg.ImaginaryCircleIndex,
            Arg.InfinitePoint,
            Arg.PointXY,
        )
        /** Circle, Line, Imaginary circle or Finite Point */
        val CLIFP = ArgType(
            Arg.PointIndex,
            Arg.CircleIndex, Arg.LineIndex, Arg.ImaginaryCircleIndex,
            Arg.PointXY,
        )
        /** Circle, Line or Imaginary circle */
        val CLI = ArgType(
            Arg.CircleIndex, Arg.LineIndex, Arg.ImaginaryCircleIndex,
        )
        /** Line or Finite Point */
        val LFP = ArgType(
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

    @Transient
    val size: Int = argTypes.size
}

val SIGNATURE_1_FINITE_POINT = Signature(ArgType.FINITE_POINT)
val SIGNATURE_2_FINITE_POINTS = Signature(ArgType.FINITE_POINT, ArgType.FINITE_POINT)
val SIGNATURE_2_CIRCLES = Signature(ArgType.CLI, ArgType.CLI)
val SIGNATURE_2_GENERALIZED_FINITE_CIRCLES = Signature(ArgType.CLIFP, ArgType.CLIFP)
val SIGNATURE_2_GENERALIZED_CIRCLES = Signature(ArgType.CLIP, ArgType.CLIP)
val SIGNATURE_3_GENERALIZED_CIRCLE = Signature(ArgType.CLIP, ArgType.CLIP, ArgType.CLIP)
val SIGNATURE_REAL_CIRCLE_AND_LINE_OR_FINITE_POINT = Signature(ArgType.CIRCLE, ArgType.LFP)
val SIGNATURE_INDICES_AND_CIRCLE = Signature(ArgType.INDICES, ArgType.CLI)
val SIGNATURE_INDICES_AND_FINITE_POINT = Signature(ArgType.INDICES, ArgType.FINITE_POINT)
val SIGNATURE_INDICES_AND_2_CIRCLES = Signature(ArgType.INDICES, ArgType.CLI, ArgType.CLI)
val SIGNATURE_INDICES_AND_2_POINTS = Signature(ArgType.INDICES, ArgType.POINT, ArgType.POINT)
val SIGNATURE_N_POINTS_PLACEHOLDER = Signature(ArgType.NOTHING)

@Immutable
@Serializable
data class NonEqualityCondition(
    val index1: Int,
    val index2: Int,
) {
    init {
        require(index1 >= 0 && index2 >= 0 && index1 != index2)
    }
}

fun nonEqualityConditionsOf(vararg indexPairs: Pair<Int, Int>): List<NonEqualityCondition> =
    indexPairs.map { (index1, index2) -> NonEqualityCondition(index1, index2) }