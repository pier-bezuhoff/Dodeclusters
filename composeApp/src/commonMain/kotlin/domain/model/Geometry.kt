package domain.model

import domain.expressions.ConformalExpressions
import domain.expressions.Expressions
import domain.expressions.ProjectiveExpressions

sealed interface Geometry {
    val objectModel: ObjectModel<*>
    val expressions: Expressions<*, *, *, *>

    data class Conformal(
        override val objectModel: ConformalObjectModel,
        override val expressions: ConformalExpressions,
    ) : Geometry

    data class Projective(
        override val objectModel: ProjectiveObjectModel,
        override val expressions: ProjectiveExpressions,
    ) : Geometry
}