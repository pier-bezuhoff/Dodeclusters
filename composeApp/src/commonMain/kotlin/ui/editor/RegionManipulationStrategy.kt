package ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.add_circle
import dodeclusters.composeapp.generated.resources.delta
import dodeclusters.composeapp.generated.resources.erase
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_add
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_add_postfix
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_erase
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_erase_postfix
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_replace
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_replace_postfix
import domain.filterIndices
import domain.model.LogicalRegion
import domain.model.RegionConstraints
import domain.updated
import domain.withoutElementAt
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

// TODO: symbols in place of descriptions (too long, use them as tooltips)
//  and mb diff colors?
@Immutable
@Serializable
enum class RegionManipulationStrategy(
    val iconResource: DrawableResource,
    val descriptionResource: StringResource,
    val descriptionPostfixResource: StringResource,
) {
    /** When clicking into an existing region, intelligently prefer XOR-style replacing */
    REPLACE(
        Res.drawable.delta,
        Res.string.region_manipulation_strategy_replace,
        Res.string.region_manipulation_strategy_replace_postfix,
    ),
    /** Only & always add new regions within set of delimiters */
    ADD(
        Res.drawable.add_circle,
        Res.string.region_manipulation_strategy_add,
        Res.string.region_manipulation_strategy_add_postfix,
    ),
    /** Only & always erase minimal existing outer regions */
    ERASE(
        Res.drawable.erase,
        Res.string.region_manipulation_strategy_erase,
        Res.string.region_manipulation_strategy_erase_postfix,
    ),
    ;

    companion object {
        fun updateRegionsAfterReselection(
            constraints: RegionConstraints,
            fullConstraints: RegionConstraints,
            allRegions: List<LogicalRegion>,
            regionManipulationStrategy: RegionManipulationStrategy,
            color: Color,
        ): List<LogicalRegion> {
            var resultingRegions = allRegions
            val outerRegionsIndices = allRegions.filterIndices { r ->
                constraints isTriviallyInside r || fullConstraints isTriviallyInside r
            }
            val outerRegions = outerRegionsIndices.map { allRegions[it] }
            val sameBoundsRegionsIndices = outerRegionsIndices.filter { i ->
                allRegions[i].insides == constraints.insides.toSet() &&
                allRegions[i].outsides == constraints.outsides.toSet()
            }
            val sameBoundsRegions = sameBoundsRegionsIndices.map { allRegions[it] }
            when (regionManipulationStrategy) {
                REPLACE -> {
                    if (outerRegions.isEmpty()) {
                        resultingRegions += constraints.toLogicalRegion(color)
                        println("added $constraints")
                    } else if (outerRegions.size == 1) {
                        val i = outerRegionsIndices.single()
                        val outer = outerRegions.single()
                        if (color == outer.fillColor) {
                            resultingRegions = allRegions.withoutElementAt(i)
                            println("removed singular same-color outer $outer")
                        } else { // we are trying to change the color im guessing
                            resultingRegions = allRegions.updated(i,
                                outer.copy(fillColor = color)
                            )
                            println("recolored singular $outer")
                        }
                    } else if (sameBoundsRegionsIndices.isNotEmpty()) {
                        val sameBoundsSameColorRegionsIndices = sameBoundsRegionsIndices.filter {
                            allRegions[it].fillColor == color
                        }
                        if (sameBoundsSameColorRegionsIndices.isNotEmpty()) {
                            val sameRegions = sameBoundsSameColorRegionsIndices.map { allRegions[it] }
                            resultingRegions = allRegions.filter { it !in sameRegions }
                            println("removed all same-bounds same-color $sameBoundsSameColorRegionsIndices ~ $constraints")
                        } else { // we are trying to change the color im guessing
                            val i = sameBoundsRegionsIndices.last()
                            val _regions = allRegions.toMutableList()
                            _regions[i] = constraints.toLogicalRegion(color)
                            sameBoundsRegions
                                .dropLast(1)
                                .forEach {
                                    _regions.remove(it) // cleanup
                                }
                            resultingRegions = _regions
                            println("recolored $i (same bounds ~ $constraints)")
                        }
                    } else {
                        // NOTE: click on overlapping region: contested behaviour
                        val outerRegionsOfTheSameColor = outerRegions.filter { r ->
                            r.fillColor == color
                        }
                        if (outerRegionsOfTheSameColor.isNotEmpty()) {
                            // NOTE: this removes regions of the same color that lie under
                            //  others (potentially invisible), which can be counter-intuitive
                            resultingRegions = allRegions.filter { it !in outerRegionsOfTheSameColor }
                            println("removed same color regions [${outerRegionsOfTheSameColor.joinToString(prefix = "\n", separator = ";\n")}]")
                        } else { // there are several outer regions, but none of the color of region.fillColor
                            resultingRegions += constraints.toLogicalRegion(color)
                            println("added $constraints")
                        }
                    }
                }
                ADD -> {
                    if (sameBoundsRegionsIndices.isEmpty()) {
                        resultingRegions += constraints.toLogicalRegion(color)
                        println("added $constraints")
                    } else if (sameBoundsRegions.last().fillColor == color) {
                        // im gonna cleanup same bounds until only 1 is left
                        // cleanup & skip
                        val _regions = allRegions.toMutableList()
                        sameBoundsRegions
                            .dropLast(1)
                            .forEach {
                                _regions.remove(it) // cannot use removeAll cuz it could remove the last one too
                            }
                        resultingRegions = _regions
                    } else { // same bounds, different color
                        // replace & cleanup
                        val i = sameBoundsRegionsIndices.last()
                        val _regions = allRegions.toMutableList()
                        _regions[i] = constraints.toLogicalRegion(color)
                        sameBoundsRegions
                            .dropLast(1)
                            .forEach {
                                _regions.remove(it) // cannot use removeAll cuz it could remove the last one too
                            }
                        resultingRegions = _regions
                        println("recolored $i (same bounds ~ $constraints)")
                    }
                }
                ERASE -> {
                    if (sameBoundsRegions.isNotEmpty()) {
                        resultingRegions = allRegions.filter { it !in sameBoundsRegions }
                        println("removed [${sameBoundsRegionsIndices.joinToString(prefix = "\n", separator = ";\n")}] (same bounds ~ $constraints)")
                    } else if (outerRegions.isNotEmpty()) {
                        // maybe find minimal and erase it OR remove last outer
                        // tho it would stop working like eraser then
                        resultingRegions = allRegions.filter { it !in outerRegions }
                        println("removed outer [${outerRegions.joinToString(prefix = "\n", separator = ";\n")}]")
                    } // when clicking on nowhere nothing happens
                }
            }
            return resultingRegions
        }
    }
}

