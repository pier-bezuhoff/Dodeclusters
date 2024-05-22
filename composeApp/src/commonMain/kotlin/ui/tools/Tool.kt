package ui.tools

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** Describes metadata associated with toolbar's tools (no algorithms per se, pattern-match for the algo) */
sealed interface Tool {
    val name: StringResource
    val icon: DrawableResource
    val description: StringResource

    /** Action = tool with 0 input parameters */
    sealed interface Action : Tool

    /** Switches between 2 different modes */
    sealed interface BinaryToggle : Action {
        /** When null, use [icon] when disabled and [icon] highlighted by color when enabled */
        val disabledIcon: DrawableResource?
    }

    /** Action with no preserved internal state, in contrast to [BinaryToggle] */
    sealed interface InstantAction : Action

    /** Can only be applied to non-empty active selection */
    sealed interface ActionOnSelection : InstantAction

    /** Tool that prompts selecting several items, described by MultiArgN<...> dependent types, to perform an action */
    sealed interface MultiArg : Tool {
        val nArgs: Int
        val argDescriptions: List<String> // TODO: migrate to StringResource's
    }
    sealed interface MultiArg1<T1 : InputType> : MultiArg {
        override val nArgs get() = 1
    }
    sealed interface MultiArg2<T1 : InputType, T2: InputType> : MultiArg {
        override val nArgs get() = 2
    }
    sealed interface MultiArg3<T1 : InputType, T2: InputType, T3: InputType> : MultiArg {
        override val nArgs get() = 3
    }
    sealed interface MultiArg4<T1 : InputType, T2: InputType, T3: InputType, T4: InputType> : MultiArg {
        override val nArgs get() = 4
    }
    sealed interface MultiArg5<T1 : InputType, T2: InputType, T3: InputType, T4: InputType, T5: InputType> : MultiArg {
        override val nArgs get() = 5
    }
    // potentially inf-arg for polygons & poly-arcs
}

/** Selectable item types, used by [Tool.MultiArg] tools */
sealed interface InputType {
    data object AnyPoint : InputType
    data object Circle : InputType
}

