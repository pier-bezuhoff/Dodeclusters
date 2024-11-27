package ui.edit_cluster

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import domain.cluster.LogicalRegion
import kotlinx.serialization.Serializable

@Serializable
@Immutable
/** Additional mode accompanying [Mode] and
 * carrying [SubMode]-specific relevant data, also
 * they have specific behavior for VM.[onPanZoom] */
sealed interface SubMode {
    data object None : SubMode
    // center uses absolute positioning
    /** Scale via top-right selection rect handle */
    data class Scale(val center: Offset) : SubMode
    data class ScaleViaSlider(
        val center: Offset,
        val sliderPercentage: Float = 0.5f
    ) : SubMode
    data class Rotate(
        val center: Offset,
        val angle: Double = 0.0,
        val snappedAngle: Double = 0.0
    ) : SubMode

    data class FlowSelect(
        val lastQualifiedPart: LogicalRegion? = null
    ) : SubMode
    data class FlowFill(
        val lastQualifiedPart: LogicalRegion? = null
    ) : SubMode
}