package domain.expressions

import androidx.compose.runtime.Immutable
import data.geometry.Point
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed interface PointConstruct {
    @Serializable
    data class Concrete(
        val point: Point
    ) : PointConstruct
    @Serializable
    data class Dynamic(
        val expression: Expression
    ) : PointConstruct
}