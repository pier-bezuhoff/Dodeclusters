package data

data class PartialArgList(
    val signature: Signature,
    val args: List<Arg> = emptyList()
) {
    enum class ArgType {
        XYPoint,
        CircleIndex
    }

    sealed class Arg(val argType: ArgType) {
        // MAYBE: float coords instead?
        //  idk, set double for now for accurate c-intersections & future snapping
        data class XYPoint(val x: Double, val y: Double) : Arg(ArgType.XYPoint)
        data class CircleIndex(val index: Int) : Arg(ArgType.CircleIndex)
    }

    // TODO: creation tool -> signature mapping
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
}