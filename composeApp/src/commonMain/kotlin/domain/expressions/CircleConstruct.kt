package domain.expressions

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed interface CircleConstruct {
    @Serializable
    @SerialName("ConcreteCircleOrLine")
    data class Concrete(
        val circleOrLine: CircleOrLine,
    ) : CircleConstruct
    @Serializable
    @SerialName("CircleOrLineExpression")
    data class Dynamic(
        val expression: Expression
    ) : CircleConstruct
}