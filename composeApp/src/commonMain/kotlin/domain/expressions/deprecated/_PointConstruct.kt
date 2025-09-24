package domain.expressions.deprecated

import androidx.compose.runtime.Immutable
import core.geometry.Point
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface _PointConstruct {
    @Serializable
    @SerialName("ConcretePoint")
    data class Concrete(
        val point: Point
    ) : _PointConstruct
    @Serializable
    @SerialName("PointExpression")
    data class Dynamic(
        val expression: _Expression
    ) : _PointConstruct
}