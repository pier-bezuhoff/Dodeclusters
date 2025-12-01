package ui.editor

import androidx.compose.ui.geometry.Offset
import ui.tools.Category
import ui.tools.Tool

/**
 * Actions corresponding to:
 * - UI elements
 * - mouse/cursor actions
 * - keyboard actions
 *
 * Does not cover in-dialog actions.
 */
sealed interface UserAction {
    data class CategoryClick(val category: Category) : UserAction
    data class ToolClick(val tool: Tool) : UserAction

    data class SelectRegionManipulationStrategy(
        val regionManipulationStrategy: RegionManipulationStrategy
    ) : UserAction

    // HUD
    sealed interface LiveAdjustment : UserAction {
        sealed interface Selection : LiveAdjustment {
            data class ScaleSlider(val percentage: Float) : Selection
            data object ScaleSliderEnd : Selection
            data class RotateHandleStart(val center: Offset) : Selection
            data class RotateHandle(val angle: Float) : Selection
            data object RotateHandleEnd : Selection
            // scale/rotate handles are in Gesture.PanZoomRotate
        }

        // tools: DetailedAdjustment, InBetween, ReverseDirection, BidirectionalSpiral

        data object Confirm : LiveAdjustment

        sealed interface Interpolation : LiveAdjustment {
            data class CountSlider(val count: Int) : Interpolation
        }
        sealed interface Rotation : LiveAdjustment {
            data class AngleSlider(val angle: Float) : Rotation
            data class StepsSlider(val steps: Int) : Rotation
        }
        sealed interface BiInversion : LiveAdjustment {
            data class SpeedSlider(val speed: Double) : BiInversion
            data class StepsSlider(val steps: Int) : BiInversion
        }
        sealed interface LoxodromicMotion : LiveAdjustment {
            data class AngleSlider(val angle: Float) : LoxodromicMotion
            data class DilationSlider(val dilation: Double) : LoxodromicMotion
            data class StepsSlider(val steps: Int) : LoxodromicMotion
        }
    }

    sealed interface Gesture : UserAction {
        data class PanZoomRotate(
            val pan: Offset,
            val centroid: Offset,
            val zoom: Float,
            val rotationAngle: Float,
        ) : Gesture
        // very fast down-up cycle
        data class Tap(
            val position: Offset,
            val pointerCount: Int,
        ) : Gesture
        data class Up(
            val position: Offset
        ) : Gesture
        data class Down(
            val position: Offset
        ) : Gesture
        data class LongPress(
            val position: Offset
        ) : Gesture
        data class VerticalScroll(
            val yDelta: Float
        ) : Gesture
    }
}