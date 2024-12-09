package data.geometry

import androidx.compose.runtime.Immutable

@Immutable
sealed interface UndirectedCircle : CircleOrLine {
    val x: Double
    val y: Double
    val radius: Double
}