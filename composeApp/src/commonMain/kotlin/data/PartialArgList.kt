package data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class PartialArgList(
    val signature: Signature,
    val args: List<Arg> = emptyList()
) {
    @Serializable
    enum class ArgType {
        XYPoint,
        CircleIndex
    }

    @Immutable
    sealed class Arg(val argType: ArgType) {
        // MAYBE: float coords instead?
        //  idk, i set double for now for accurate c-intersections & future snapping
        data class XYPoint(val x: Double, val y: Double) : Arg(ArgType.XYPoint)
        data class CircleIndex(val index: Int) : Arg(ArgType.CircleIndex)
    }

    // TODO: creation tool -> signature mapping
    @Serializable
    data class Signature(val argTypes: List<ArgType>) {
        constructor(vararg argTypes: ArgType) : this(argTypes.toList())
    }

    val isFull =
        args.size == signature.argTypes.size

    val isValid: Boolean =
        args.size <= signature.argTypes.size &&
        args.zip(signature.argTypes).all { (arg, argType) -> arg.argType == argType }

    val nextArgType: ArgType? =
        if (isFull) null else signature.argTypes[args.size]

    init {
        require(isValid) { "Invalid type signature $signature, with args $args" }
    }

    // MAYBE: smarter currying, when arg types resolve uniquely regardless of order
    fun addArg(arg: Arg): PartialArgList {
        require(!isFull) { "The $this is already full" }
        require(arg.argType == nextArgType) { "Invalid arg type, expected: $nextArgType, actual: $arg" }
        return PartialArgList(
            signature,
            args + arg
        )
    }

    companion object {
        val SIGNATURE_2_POINTS = Signature(ArgType.XYPoint, ArgType.XYPoint)
        val SIGNATURE_3_POINTS = Signature(ArgType.XYPoint, ArgType.XYPoint, ArgType.XYPoint)
    }
}