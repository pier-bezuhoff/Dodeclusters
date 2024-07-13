package data.geometry

import kotlinx.serialization.Serializable

@Serializable
data class ImaginaryCircle(
    val x: Double,
    val y: Double,
    val radius: Double,
) : GCircle