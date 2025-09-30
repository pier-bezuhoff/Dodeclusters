package ui.editor

import androidx.compose.runtime.Immutable
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_add
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_erase
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_replace
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Immutable
@Serializable
enum class RegionManipulationStrategy(
    val stringResource: StringResource
) {
    /** When clicking into an existing region, intelligently prefer XOR-style replacing */
    REPLACE(Res.string.region_manipulation_strategy_replace),
    /** Only & always add new regions within set of delimiters */
    ADD(Res.string.region_manipulation_strategy_add),
    /** Only & always erase minimal existing outer regions */
    ERASE(Res.string.region_manipulation_strategy_erase),
}