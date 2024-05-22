package ui.tools

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

// tools, <hide>
sealed interface Category {
    val name: StringResource
    val icon: DrawableResource // possibly inherit from default
    val tools: List<Tool>
    val default: Tool
}