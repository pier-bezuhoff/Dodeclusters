package domain.expressions

import androidx.compose.runtime.Immutable
import data.geometry.CircleOrLine
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed interface CircleConstruct {
    @Serializable
    data class Concrete(
        val circleOrLine: CircleOrLine,
    ) : CircleConstruct
    @Serializable
    data class Dynamic(
        val expression: Expression
    ) : CircleConstruct
}