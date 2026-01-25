package domain.model

import androidx.compose.ui.geometry.Offset
import core.geometry.projective.Conic
import domain.Ix
import domain.expressions.ProjectiveExpressions

class ProjectiveObjectModel : ObjectModel<Conic>() {

    override val expressions: ProjectiveExpressions = ProjectiveExpressions(emptyMap(), mutableListOf())

    override fun transform(
        targets: List<Ix>,
        translation: Offset,
        focus: Offset,
        zoom: Float,
        rotationAngle: Float
    ): Set<Ix> {
        TODO("not implemented")
    }

    override fun Conic.downscale(): Conic {
        TODO("not implemented")
    }

    override fun Conic.upscale(): Conic {
        TODO("not implemented")
    }
}