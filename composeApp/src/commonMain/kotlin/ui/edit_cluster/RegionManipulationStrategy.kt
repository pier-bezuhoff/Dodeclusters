package ui.edit_cluster

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class RegionManipulationStrategy {
    /** When clicking into an existing region, intelligently prefer XOR-style replacing */
    REPLACE,
    /** Only & always add new regions within set of delimiters */
    ADD,
    /** Only & always erase minimal existing outer regions */
    ERASE,
}