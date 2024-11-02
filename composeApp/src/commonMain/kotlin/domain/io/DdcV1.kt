package domain.io

import androidx.compose.ui.graphics.Color
import domain.cluster.Cluster
import data.geometry.Circle
import domain.ColorCssSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Old, deprecated Dodeclusters' format. Left for compat */
@Serializable
data class DdcV1(
    val name: String = DdcV2.DEFAULT_NAME,
    @Serializable(ColorCssSerializer::class)
    val backgroundColor: Color = DdcV2.DEFAULT_BACKGROUND_COLOR,
    val bestCenterX: Float? = DdcV2.DEFAULT_BEST_CENTER_X,
    val bestCenterY: Float? = DdcV2.DEFAULT_BEST_CENTER_Y,
    val shape: Shape = DdcV2.DEFAULT_SHAPE,
    val drawTrace: Boolean = DdcV2.DEFAULT_DRAW_TRACE,
    val content: List<Token>,
) {
    val nCircles: Int
        get() = content.lastOrNull()?.let {
            when (it) {
                is Token.Circle -> it.index + 1
                is Token.Cluster -> it.indices.last() + 1
            }
        } ?: 0

    constructor(cluster: Cluster) : this(
        content = listOf(
            Token.Cluster(
                if (cluster.circles.isEmpty()) emptyList()
                else listOf(0, cluster.circles.size - 1),
                cluster.circles.filterIsInstance<Circle>(),
                cluster.parts,
            )
        )
    )

    @Serializable
    sealed class Token {
        @SerialName("Cluster")
        @Serializable
        data class Cluster(
            /** 2 value list: [[first circle index, last circle index]] */
            val indices: List<Int>,
            val circles: List<data.geometry.Circle>,
            /** circle indices used parts shall be Ddc-global circle indices, the one consistent with cluster.indices */
            val parts: List<domain.cluster.ClusterPart>,
            val filled: Boolean = DdcV2.DEFAULT_CLUSTER_FILLED,
            /** circle indices used shall be Ddc-global circle indices, the one consistent with cluster.indices and circle.index */
            val rule: List<Int> = DdcV2.DEFAULT_CLUSTER_RULE,
        ) : Token() {
            fun toCluster(): domain.cluster.Cluster =
                Cluster(circles, parts)
        }
        @SerialName("Circle")
        @Serializable
        data class Circle(
            val index: Int,
            val x: Double,
            val y: Double,
            val radius: Double,
            val visible: Boolean = DdcV2.DEFAULT_CIRCLE_VISIBLE,
            val filled: Boolean = DdcV2.DEFAULT_CIRCLE_FILLED,
            @Serializable(ColorCssSerializer::class)
            val fillColor: Color? = DdcV2.DEFAULT_CIRCLE_FILL_COLOR,
            @Serializable(ColorCssSerializer::class)
            val borderColor: Color? = DdcV2.DEFAULT_CIRCLE_BORDER_COLOR,
            /** circle indices used shall be Ddc-global circle indices, the one consistent with cluster.indices and circle.index */
            val rule: List<Int> = DdcV2.DEFAULT_CIRCLE_RULE,
        ) : Token() {
            fun toCircle(): data.geometry.Circle =
                Circle(x, y, radius)
        }
    }
}