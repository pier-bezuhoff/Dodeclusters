package domain.expressions

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.Line
import core.geometry.Point
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// for conformal geometry
@Serializable
@Immutable
sealed interface ObjectConstruct {
    @Serializable
    @SerialName("ConcretePoint")
    data class ConcretePoint(
        val point: Point,
    ) : ObjectConstruct
    @Serializable
    @SerialName("ConcreteCircle")
    data class ConcreteCircle(
        val circle: Circle,
    ) : ObjectConstruct
    @Serializable
    @SerialName("ConcreteLine")
    data class ConcreteLine(
        val line: Line,
    ) : ObjectConstruct
    @Serializable
    @SerialName("DynamicExpression")
    data class Dynamic(
        val expression: ExprOutput
    ) : ObjectConstruct
}
