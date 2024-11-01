package domain.expressions

import androidx.compose.runtime.Immutable
import data.geometry.Point
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed interface PointConstruct {
    @Serializable
    @SerialName("ConcretePoint")
    data class Concrete(
        val point: Point
    ) : PointConstruct
    @Serializable
    @SerialName("PointExpression")
    data class Dynamic(
        val expression: Expression
    ) : PointConstruct
}