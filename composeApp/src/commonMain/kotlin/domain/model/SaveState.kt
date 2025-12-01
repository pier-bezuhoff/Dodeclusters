package domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import core.geometry.GCircle
import domain.ColorAsCss
import domain.Ix
import domain.Lens
import domain.SerializableOffset
import domain.cluster.LogicalRegion
import domain.expressions.Expression
import domain.model.Diff.ChessboardPattern
import kotlinx.serialization.Serializable

@Serializable
data class SaveState(
    val objects: List<GCircle?>,
    val objectColors: Map<Ix, ColorAsCss?>,
    val objectLabels: Map<Ix, String?>,
    val expressions: Map<Ix, Expression?>,
    val regions: List<LogicalRegion>,
    val backgroundColor: ColorAsCss?,
    val chessboardPattern: ChessboardPattern,
    val chessboardColor: ColorAsCss?,
    val phantoms: Set<Ix>,
    val selection: List<Ix>,
    val center: SerializableOffset,
    val regionColor: ColorAsCss?,
) {
    /** `Lens<A, B> = (A -> B) -> SaveState -> SaveState` */
    interface Lens<A, B> : domain.Lens<SaveState, A, B>

    @Serializable
    sealed interface Change {
        @Serializable
        data class Objects(val objects: Map<Ix, GCircle?>) : Change {
            companion object : Lens<List<GCircle?>, Objects> {
                override fun get(s: SaveState) =
                    s.objects
                override fun set(s: SaveState, b: Objects): SaveState {
                    val _objects = s.objects.toMutableList()
                    for ((ix, o) in b.objects) {
                        _objects[ix] = o
                    }
                    return s.copy(objects = _objects.toList())
                }
            }
        }
        @Serializable
        data class ObjectColors(val colors: Map<Ix, ColorAsCss?>) : Change {
            companion object : Lens<Map<Ix, Color?>, ObjectColors> {
                override fun get(s: SaveState) =
                    s.objectColors
                override fun set(s: SaveState, b: ObjectColors) =
                    s.copy(objectColors = s.objectColors + b.colors)
            }
        }
        // ...
        @Serializable
        data class Center(val center: SerializableOffset) : Change {
            companion object : Lens<Offset, Center> {
                override fun get(s: SaveState) =
                    s.center
                override fun set(s: SaveState, b: Center) =
                    s.copy(center = b.center)
            }
        }
    }
}

