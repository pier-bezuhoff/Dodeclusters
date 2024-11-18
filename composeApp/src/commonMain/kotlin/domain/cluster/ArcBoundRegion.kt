package domain.cluster

import androidx.compose.runtime.Immutable
import domain.ColorAsCss
import kotlinx.serialization.Serializable

@Serializable
@Immutable
/**
 * @param[arcs] [List] of indices (_starting from 1!_) of circles from which the
 * arcs are chosen, __prefixed__ by +/- depending on whether the direction of
 * the arc corresponds to the direction of the circle
 * */
data class ArcBoundRegion(
    // NOTE: cyclic order doesn't matter, but reversing it alternates between one of
    //  2 possible regions that arise when not considering circle order
    val arcs: List<Int>,
    val fillColor: ColorAsCss?,
    val borderColor: ColorAsCss?,
)