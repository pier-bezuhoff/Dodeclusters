package domain

import androidx.compose.runtime.Immutable

@Immutable
data class PartialArgList(
    val signature: Signature,
    val args: List<Arg> = emptyList(),
    val lastArgIsConfirmed: Boolean = true,
    val lastSnap: PointSnapResult? = null,
) {
    val isFull =
        args.size == signature.argTypes.size
    val isValid: Boolean =
        args.size <= signature.argTypes.size &&
        args.zip(signature.argTypes).all { (arg, argType) -> arg isType argType }
    val currentArg: Arg? =
        args.lastOrNull()
    val currentArgType: ArgType? =
        if (args.isEmpty())
            null
        else
            signature.argTypes[args.size - 1]
    val nextArgType: ArgType? =
        if (isFull)
            null
        else signature.argTypes[args.size]

    init {
        require(isValid) { "Invalid type signature $signature, with args $args" }
    }

    fun addOrUpdate(arg: Arg, confirmThisArg: Boolean = false): PartialArgList =
        if (lastArgIsConfirmed || args.isEmpty())
            addArg(arg, confirmThisArg)
        else
            updateCurrentArg(arg, confirmThisArg)

    fun updateCurrentArg(arg: Arg, confirmThisArg: Boolean = true): PartialArgList {
        require(currentArg != null && currentArgType != null) { "The PartialArgList is empty, nothing to update" }
        require(arg isType currentArgType) { "Invalid arg type, expected: $currentArgType, actual: $arg" }
        return PartialArgList(
            signature,
            args.dropLast(1) + arg,
            confirmThisArg
        )
    }

    // MAYBE: smarter currying, when arg types resolve uniquely regardless of order if applicable
    fun addArg(arg: Arg, confirmThisArg: Boolean = false): PartialArgList {
        require(!isFull && nextArgType != null) { "The $this is already full" }
        require(arg isType nextArgType) { "Invalid arg type, expected: $nextArgType, actual: $arg" }
        return PartialArgList(
            signature,
            args + arg,
            confirmThisArg
        )
    }
}