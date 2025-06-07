package domain

import androidx.compose.runtime.Immutable

@Immutable
data class PartialArgList(
    val signature: Signature,
    val nonEqualityConditions: List<NonEqualityCondition> = emptyList(),
    val args: List<Arg> = emptyList(),
    val lastArgIsConfirmed: Boolean = true,
    val lastSnap: PointSnapResult? = null,
) {
    val isFull =
        args.size == signature.size
    val isValid: Boolean =
        nonEqualityConditions.all { it.index1 < signature.size && it.index2 < signature.size } &&
        args.size <= signature.size &&
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
        return copy(
            args = args.dropLast(1) + arg,
            lastArgIsConfirmed = confirmThisArg,
        )
    }

    // MAYBE: smarter currying, when arg types resolve uniquely regardless of order if applicable
    fun addArg(arg: Arg, confirmThisArg: Boolean = false): PartialArgList {
        require(!isFull && nextArgType != null) { "The $this is already full" }
        require(arg isType nextArgType) { "Invalid arg type, expected: $nextArgType, actual: $arg" }
        return copy(
            args = args + arg,
            lastArgIsConfirmed = confirmThisArg,
        )
    }

    fun validateUpdatedArg(arg: Arg): Boolean {
        val newArgs = args.dropLast(1) + arg
        return nonEqualityConditions.all { (index1, index2) ->
            index1 >= newArgs.size ||
            index2 >= newArgs.size ||
            Arg.testInequality(newArgs[index1], newArgs[index2])
        }
    }

    fun validateNewArg(arg: Arg): Boolean {
        val newArgs = args + arg
        return nonEqualityConditions.all { (index1, index2) ->
            index1 >= newArgs.size ||
            index2 >= newArgs.size ||
            Arg.testInequality(newArgs[index1], newArgs[index2])
        }
    }

    fun copyEmpty(): PartialArgList =
        PartialArgList(signature, nonEqualityConditions)
}