package domain.model

import androidx.compose.ui.geometry.Offset
import core.geometry.projective.Conic
import domain.Ix
import domain.expressions.Expressions

class ProjectiveObjectModel : ObjectModel<Conic>() {
    override fun transform(
        expressions: Expressions<*, *, *, Conic>,
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