package ui.tools

import androidx.compose.ui.graphics.Color
import domain.Signature
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringArrayResource
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
        val disabledDescription: StringResource
    }

    /** Cycles through 3 different states */
    sealed interface TernaryToggle : Action {
        // generally alternative stuff is shown when both enabled & alternative flags are true
        val alternativeIcon: DrawableResource
        val alternativeDescription: StringResource
        val disabledIcon: DrawableResource
        val disabledDescription: StringResource
    }
    /** Action with no preserved internal state, in contrast to [BinaryToggle] */
    sealed interface InstantAction : Action

    /** [InstantAction] that is available only in certain contexts */
    sealed interface ContextAction : InstantAction

    /** [InstantAction] that can only be applied to non-empty active selection
     * and is only available whenever it is present */
    sealed interface ActionOnSelection : ContextAction

    /** Tool that prompts selecting several items, described by MultiArgN<...> dependent types, to perform an action */
    sealed interface MultiArg : Tool {
        val signature: Signature
        val argDescriptions: StringArrayResource
        val nArgs: Int
            get() = signature.argTypes.size
    }

    sealed interface Tinted : Tool {
        val tint: Color
    }
}