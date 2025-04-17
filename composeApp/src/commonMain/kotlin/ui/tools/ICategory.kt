package ui.tools

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

// tools, <hide>
sealed interface ICategory { // i'll be real, no clue how to name it..
    val name: StringResource
    /** null => dynamically set as [default.icon],
     * otherwise a static icon */
    val icon: DrawableResource?
    val tools: List<ITool>
    val default: ITool?
    /** Indices of tools that can be made default */
    val defaultables: List<Int>
}