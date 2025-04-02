package ui.edit_cluster

import androidx.compose.runtime.Immutable
import domain.indexOfOrNull
import domain.updated
import ui.tools.EditClusterCategory
import ui.tools.EditClusterTool

@Immutable
data class CategorySetup(
    val categories: List<EditClusterCategory> = listOf(
        EditClusterCategory.Drag,
        EditClusterCategory.Multiselect,
        EditClusterCategory.Region,
        EditClusterCategory.Visibility,
        EditClusterCategory.Colors,
        EditClusterCategory.Transform,
        EditClusterCategory.Create, // FAB
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
    val activeCategory: EditClusterCategory = EditClusterCategory.Drag,
    val activeTool: EditClusterTool = EditClusterTool.Drag,
) {
    val categories = categorySetup.categories
    val panelNeedsToBeShown =
        if (activeCategory.default == null)
            activeCategory.tools.isNotEmpty()
        else
            activeCategory.tools.size > 1

    fun getDefaultTool(category: EditClusterCategory): EditClusterTool? {
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

    fun getCategory(tool: EditClusterTool): EditClusterCategory {
        val categoryIndex = categories.indexOfFirst { tool in it.tools }
        return categories[categoryIndex]
    }

    fun updateDefault(category: EditClusterCategory, newDefaultTool: EditClusterTool?): ToolbarState {
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