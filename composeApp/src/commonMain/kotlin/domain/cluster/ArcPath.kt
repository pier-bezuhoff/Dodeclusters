package domain.cluster

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import domain.ColorAsCss
import domain.Ix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface Arc {
    /**
     * @property[sagittaRatio] see computeCircleBy2PointsAndSagittaRatio
     */
    @Serializable
    @SerialName("ArcBy2Points")
    data class By2Points(
        val sagittaRatio: Double,
    ) : Arc
    @Serializable
    @SerialName("ArcBy3Points")
    data class By3Points(
        val middlePointIndex: Ix,
    ) : Arc
}

@Immutable
@Serializable
@SerialName("ArcPath")
sealed interface ArcPath {
    val vertices: List<Ix>
    val arcs: List<Arc>
    val borderColor: ColorAsCss

    @Stable
    val dependencies: Set<Ix> get() =
        vertices.toSet() + arcs.filterIsInstance<Arc.By3Points>().map { it.middlePointIndex }

    /** Looping arc-path */
    @Immutable
    @Serializable
    @SerialName("ClosedArcPath")
    data class Closed(
        override val vertices: List<Ix>,
        override val arcs: List<Arc>,
        override val borderColor: ColorAsCss,
        val fillColor: ColorAsCss? = null,
    ) : ArcPath {

        init {
            // Vertices - Edges + Faces = 2
            require(vertices.size - arcs.size + 2 == 2)
        }
    }

    /** Non-looping arc-path */
    @Immutable
    @Serializable
    @SerialName("OpenArcPath")
    data class Open(
        override val vertices: List<Ix>,
        override val arcs: List<Arc>,
        override val borderColor: ColorAsCss,
    ) : ArcPath {

        init {
            // Vertices - Edges + Faces = 2
            require(vertices.size - arcs.size + 1 == 2)
        }
    }
}

