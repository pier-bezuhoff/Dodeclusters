package ui.tools

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.DrawableResource

sealed interface Tool {
    val name: String
    val iconResource: IconResource
    val description: String // MAYBE: R.string.id instead

    /** Action = tool with 0 input parameters */
    sealed interface Action : Tool
    sealed interface BinaryToggle : Action {
        val disabledIconResource: IconResource
    }

    /** Can only be applied to a non-empty active selection */
    sealed interface ActionOnSelection : Tool

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
}

sealed interface IconResource {
    class AsImageVector(val imageVector: ImageVector) : IconResource
    class AsDrawable(val drawableResource: DrawableResource) : IconResource
}

sealed interface InputType {
    data object AnyPoint : InputType
    data object Circle : InputType
}

