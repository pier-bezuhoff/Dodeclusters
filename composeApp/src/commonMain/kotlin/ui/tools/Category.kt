package ui.tools

// tools, <hide>
sealed interface Category {
    val name: String
    val iconResource: IconResource // possibly inherit from default
    val tools: List<Tool>
    val default: Tool
}