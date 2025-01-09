package domain.expressions.deprecated

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface _CircleConstruct {
    @Serializable
    @SerialName("ConcreteCircleOrLine")
    data class Concrete(
        val circleOrLine: CircleOrLine,
    ) : _CircleConstruct
    @Serializable
    @SerialName("CircleOrLineExpression")
    data class Dynamic(
        val expression: _Expression
    ) : _CircleConstruct
}