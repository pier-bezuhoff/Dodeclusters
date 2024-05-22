package ui.tools

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.DrawableResource

/** Describes metadata associated with toolbar's tools (no algorithms per se) */
sealed interface Tool {
    val name: String
    val iconResource: IconResource
    val description: String // MAYBE: R.string.id instead

    /** Action = tool with 0 input parameters */
    sealed interface Action : Tool
    /** Switches between 2 different modes */
    sealed interface BinaryToggle : Action {
        val disabledIconResource: IconResource? // null => un-highlight icon instead
    }

    /** Can only be applied to a non-empty active selection */
    sealed interface ActionOnSelection : Tool

    /** Tool that prompts selecting several items, described by MultiArgN<...> dependent types, to perform an action */
    sealed interface MultiArg : Tool {
        val nArgs: Int
        val argDescriptions: List<String>
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
    // potentially inf-arg for polygons
}

sealed interface IconResource {
    class AsImageVector(val imageVector: ImageVector) : IconResource
    class AsDrawable(val drawableResource: DrawableResource) : IconResource
}

/** Selectable item types, used by some tools */
sealed interface InputType {
    data object AnyPoint : InputType
    data object Circle : InputType
}

