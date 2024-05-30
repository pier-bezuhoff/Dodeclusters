package ui.tools

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

// tools, <hide>
sealed interface Category {
    val name: StringResource
    /** null => dynamically set as [default.icon],
     * otherwise a static icon */
    val icon: DrawableResource?
    val tools: List<Tool>
    val default: Tool
}