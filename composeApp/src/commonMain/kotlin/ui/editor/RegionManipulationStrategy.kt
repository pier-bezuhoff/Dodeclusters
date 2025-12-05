package ui.editor

import androidx.compose.runtime.Immutable
import dodeclusters.composeapp.generated.resources.Res
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_add
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_erase
import dodeclusters.composeapp.generated.resources.region_manipulation_strategy_replace
import domain.Ix
import domain.cluster.LogicalRegion
import domain.filterIndices
import domain.updated
import domain.withoutElementAt
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
    ERASE(Res.string.region_manipulation_strategy_erase), ;

    companion object {
        internal inline fun updateRegionsAfterReselection(
            compressedRegion: LogicalRegion,
            uncompressedRegion: LogicalRegion,
            allRegions: List<LogicalRegion>,
            regionManipulationStrategy: RegionManipulationStrategy,
            shouldUpdateSelection: Boolean,
            crossinline setSelection: (List<Ix>) -> Unit,
        ): List<LogicalRegion> {
            var resultingRegions = allRegions
            val outerRegionsIndices = allRegions.filterIndices { compressedRegion isObviouslyInside it || uncompressedRegion isObviouslyInside it  }
            val outerRegions = outerRegionsIndices.map { allRegions[it] }
            val sameBoundsRegionsIndices = outerRegionsIndices.filter {
                allRegions[it].insides == compressedRegion.insides && allRegions[it].outsides == compressedRegion.outsides
            }
            val sameBoundsRegions = sameBoundsRegionsIndices.map { allRegions[it] }
            when (regionManipulationStrategy) {
                RegionManipulationStrategy.REPLACE -> {
                    if (outerRegions.isEmpty()) {
                        resultingRegions += compressedRegion
                        if (shouldUpdateSelection) {
                            setSelection((compressedRegion.insides + compressedRegion.outsides).toList())
                        }
                        println("added $compressedRegion")
                    } else if (outerRegions.size == 1) {
                        val i = outerRegionsIndices.single()
                        val outer = outerRegions.single()
                        if (compressedRegion.fillColor == outer.fillColor) {
                            resultingRegions = allRegions.withoutElementAt(i)
                            println("removed singular same-color outer $outer")
                        } else { // we are trying to change the color im guessing
                            resultingRegions = allRegions.updated(i, outer.copy(fillColor = compressedRegion.fillColor))
                            if (shouldUpdateSelection) {
                                setSelection((compressedRegion.insides + compressedRegion.outsides).toList())
                            }
                            println("recolored singular $outer")
                        }
                    } else if (sameBoundsRegionsIndices.isNotEmpty()) {
                        val sameBoundsSameColorRegionsIndices = sameBoundsRegionsIndices.filter {
                            allRegions[it].fillColor == compressedRegion.fillColor
                        }
                        if (sameBoundsSameColorRegionsIndices.isNotEmpty()) {
                            val sameRegions = sameBoundsSameColorRegionsIndices.map { allRegions[it] }
                            resultingRegions = allRegions.filter { it !in sameRegions }
                            println("removed all same-bounds same-color $sameBoundsSameColorRegionsIndices ~ $compressedRegion")
                        } else { // we are trying to change the color im guessing
                            val i = sameBoundsRegionsIndices.last()
                            val _regions = allRegions.toMutableList()
                            _regions[i] = compressedRegion
                            sameBoundsRegions
                                .dropLast(1)
                                .forEach {
                                    _regions.remove(it) // cleanup
                                }
                            resultingRegions = _regions
                            if (shouldUpdateSelection) {
                                setSelection((compressedRegion.insides + compressedRegion.outsides).toList())
                            }
                            println("recolored $i (same bounds ~ $compressedRegion)")
                        }
                    } else {
                        // NOTE: click on overlapping region: contested behaviour
                        val outerRegionsOfTheSameColor = outerRegions.filter { it.fillColor == compressedRegion.fillColor }
                        if (outerRegionsOfTheSameColor.isNotEmpty()) {
                            // NOTE: this removes regions of the same color that lie under
                            //  others (potentially invisible), which can be counter-intuitive
                            resultingRegions = allRegions.filter { it !in outerRegionsOfTheSameColor }
                            println("removed same color regions [${outerRegionsOfTheSameColor.joinToString(prefix = "\n", separator = ";\n")}]")
                        } else { // there are several outer regions, but none of the color of region.fillColor
                            resultingRegions += compressedRegion
                            if (shouldUpdateSelection) {
                                setSelection((compressedRegion.insides + compressedRegion.outsides).toList())
                            }
                            println("added $compressedRegion")
                        }
                    }
                }
                RegionManipulationStrategy.ADD -> {
                    if (sameBoundsRegionsIndices.isEmpty()) {
                        resultingRegions += compressedRegion
                        if (shouldUpdateSelection) {
                            setSelection((compressedRegion.insides + compressedRegion.outsides).toList())
                        }
                        println("added $compressedRegion")
                    } else if (sameBoundsRegions.last().fillColor == compressedRegion.fillColor) {
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
                        _regions[i] = compressedRegion
                        if (shouldUpdateSelection) {
                            setSelection((compressedRegion.insides + compressedRegion.outsides).toList())
                        }
                        sameBoundsRegions
                            .dropLast(1)
                            .forEach {
                                _regions.remove(it) // cannot use removeAll cuz it could remove the last one too
                            }
                        resultingRegions = _regions
                        println("recolored $i (same bounds ~ $compressedRegion)")
                    }
                }
                RegionManipulationStrategy.ERASE -> {
                    if (sameBoundsRegions.isNotEmpty()) {
                        resultingRegions = allRegions.filter { it !in sameBoundsRegions }
                        println("removed [${sameBoundsRegionsIndices.joinToString(prefix = "\n", separator = ";\n")}] (same bounds ~ $compressedRegion)")
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

