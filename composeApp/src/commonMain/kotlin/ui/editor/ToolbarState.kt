package ui.editor

import androidx.compose.runtime.Immutable
import domain.indexOfOrNull
import domain.updated
import ui.tools.Category
import ui.tools.Tool

@Immutable
data class CategorySetup(
    val categories: List<Category> = listOf(
        Category.Drag,
        Category.Multiselect,
        Category.Region,
        Category.Visibility,
        Category.Colors,
        Category.Transform,
        Category.Create, // FAB
    ),
)

/**
* Encapsulates all category- and tool-related info
* @param[categoryDefaultIndices] category index -> tool index among category.tools
*/
@Immutable
data class ToolbarState(
    val categorySetup: CategorySetup = CategorySetup(),
    val categoryDefaultIndices: List<Int?> =
        categorySetup.categories.map { category ->
            category.default?.let { defaultTool ->
                val ix = category.tools.indexOf(defaultTool)
                if (ix == -1)
                    null
                else ix
            }
        }
    ,
    val activeCategory: Category = Category.Drag,
    val activeTool: Tool = Tool.Drag,
) {
    val categories = categorySetup.categories
    val panelNeedsToBeShown =
        if (activeCategory.default == null)
            activeCategory.tools.isNotEmpty()
        else
            activeCategory.tools.size > 1

    fun getDefaultTool(category: Category): Tool? {
        val categoryIndex = categories.indexOf(category)
        return if (categoryIndex == -1) {
            null
        } else {
            val toolIndex = categoryDefaultIndices[categoryIndex]
            if (toolIndex == null)
                null
            else
                category.tools[toolIndex]
        }
    }

    fun getCategory(tool: Tool): Category {
        val categoryIndex = categories.indexOfFirst { tool in it.tools }
        return categories[categoryIndex]
    }

    fun updateDefault(category: Category, newDefaultTool: Tool?): ToolbarState {
        val categoryIndex = categories.indexOf(category)
        val newDefaultIndex: Int? =
            if (newDefaultTool == null)
                null
            else {
                val ix = category.tools.indexOfOrNull(newDefaultTool)
                if (ix in category.defaultables)
                    ix
                else
                    categoryDefaultIndices[categoryIndex]
            }
        return copy(
            categoryDefaultIndices =
                categoryDefaultIndices.updated(categoryIndex, newDefaultIndex)
        )
    }
}