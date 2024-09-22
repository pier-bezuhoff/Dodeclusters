package data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import data.geometry.GCircle
import data.geometry.Point
import domain.Indices
import kotlinx.serialization.Serializable

@Immutable
data class PartialArgList(
    val signature: Signature,
    val args: List<Arg> = emptyList(),
    val lastArgIsConfirmed: Boolean = true
) {
    enum class ArgType {
        XYPoint,
        CircleIndex,
        GeneralizedCircle, // primarily point/circle/line for the perp3 instrument
        CircleAndPointIndices,
    }

    @Immutable
    sealed class Arg(val argType: ArgType) {
        // MAYBE: float coords instead?
        //  idk, i set double for now for accurate c-intersections & future snapping
        data class XYPoint(val x: Double, val y: Double) : Arg(ArgType.XYPoint) {
            constructor(point: Point) : this(point.x, point.y)

            fun toOffset(): Offset =
                Offset(x.toFloat(), y.toFloat())

            fun toPoint(): Point =
                Point(x, y)

            companion object {
                fun fromOffset(offset: Offset): XYPoint =
                    XYPoint(offset.x.toDouble(), offset.y.toDouble())
            }
        }
        data class CircleIndex(val index: Int) : Arg(ArgType.CircleIndex)
        data class GeneralizedCircle(val gCircle: GCircle) : Arg(ArgType.GeneralizedCircle)
        data class CircleAndPointIndices(
            val circleIndices: Indices,
            val pointIndices: Indices = emptyList()
        ) : Arg(ArgType.CircleAndPointIndices)
    }

    @Serializable
    data class Signature(val argTypes: List<ArgType>) {
        constructor(vararg argTypes: ArgType) : this(argTypes.toList())
    }

    val isFull =
        args.size == signature.argTypes.size

    val isValid: Boolean =
        args.size <= signature.argTypes.size &&
        args.zip(signature.argTypes).all { (arg, argType) -> arg.argType == argType }

    val currentArg: Arg? =
        args.lastOrNull()
    val currentArgType: ArgType? =
        currentArg?.argType

    val nextArgType: ArgType? =
        if (isFull) null else signature.argTypes[args.size]

    init {
        require(isValid) { "Invalid type signature $signature, with args $args" }
    }

    fun addOrUpdate(arg: Arg, confirmThisArg: Boolean = false): PartialArgList =
        if (lastArgIsConfirmed || args.isEmpty())
            addArg(arg, confirmThisArg)
        else
            updateCurrentArg(arg, confirmThisArg)

    fun updateCurrentArg(arg: Arg, confirmThisArg: Boolean = true): PartialArgList {
        require(currentArg != null) { "The PartialArgList is empty, nothing to update" }
        require(currentArg.argType == arg.argType) { "Invalid arg type, expected: $currentArgType, actual: $arg" }
        return PartialArgList(
            signature,
            args.dropLast(1) + arg,
            confirmThisArg
        )
    }

    // MAYBE: smarter currying, when arg types resolve uniquely regardless of order if applicable
    fun addArg(arg: Arg, confirmThisArg: Boolean = false): PartialArgList {
        require(!isFull) { "The $this is already full" }
        require(arg.argType == nextArgType) { "Invalid arg type, expected: $nextArgType, actual: $arg" }
        return PartialArgList(
            signature,
            args + arg,
            confirmThisArg
        )
    }

    companion object {
        val SIGNATURE_1_POINT = Signature(ArgType.XYPoint)
        val SIGNATURE_2_POINTS = Signature(ArgType.XYPoint, ArgType.XYPoint)
        val SIGNATURE_3_POINTS = Signature(ArgType.XYPoint, ArgType.XYPoint, ArgType.XYPoint)
        val SIGNATURE_2_CIRCLES = Signature(ArgType.CircleIndex, ArgType.CircleIndex)
        val SIGNATURE_INDEXED_AND_CIRCLE = Signature(ArgType.CircleAndPointIndices, ArgType.CircleIndex)
        val SIGNATURE_2_GENERALIZED_CIRCLE = Signature(ArgType.GeneralizedCircle, ArgType.GeneralizedCircle)
        val SIGNATURE_3_GENERALIZED_CIRCLE = Signature(ArgType.GeneralizedCircle, ArgType.GeneralizedCircle, ArgType.GeneralizedCircle)
        val SIGNATURE_INDEXED_AND_2_POINTS = Signature(ArgType.CircleAndPointIndices, ArgType.XYPoint, ArgType.XYPoint)
        val SIGNATURE_N_POINTS_PLACEHOLDER = Signature(ArgType.XYPoint)
    }
}