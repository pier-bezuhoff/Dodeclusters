package ui

import androidx.compose.ui.graphics.Color
import data.Cluster
import data.io.Ddc

/** indices */
typealias Ixs = List<Int>

// naked top-level circle = cluster with 1 part = [this circle]
// Analogue to Dodeca Meditation's CircleGroup
class DdcPresenter(
    val originalDdc: Ddc
) {
    val clusterCount: Int // # of all clusters + visible circles
    val partCount: Int
    val circleCount: Int // # of all the circles

    val clusterFilled: List<Boolean>
    val clusterPartsIxs: List<Ixs>
    val partInsides: List<Ixs>
    val partOutsides: List<Ixs>
    val partFillColor: List<Color>
    val partBorderColor: List<Color?>

    // xs0 = previous state, xs = current one
    val xs0: DoubleArray
    val ys0: DoubleArray
    val rs0: DoubleArray
    val xs: DoubleArray
    val ys: DoubleArray
    val rs: DoubleArray
    val rules: List<List<Int>>

    init {
        val clusters = originalDdc.content
            // clusterizing visible top-level circles
            .filterNot { it is Ddc.Token.Circle && !it.visible }
            .map { token ->
                when (token) {
                    is Ddc.Token.Circle -> Ddc.Token.Cluster(
                        indices = listOf(token.index, token.index),
                        circles = listOf(token.toCircle()),
                        parts = listOf(
                            Cluster.Part(
                                insides = setOf(0),
                                outsides = emptySet(),
                                fillColor = token.fillColor ?: Ddc.DEFAULT_CIRCLE_FILL_COLOR,
                                borderColor = token.borderColor
                            )
                        ),
                        filled = token.filled,
                        rule = token.rule
                    )
                    is Ddc.Token.Cluster -> token
                }
            }
        clusterCount = clusters.size
        clusterFilled = clusters.map { it.filled }
        partCount = clusters.sumOf { it.parts.size }
        var partShift = 0
        val indexedParts = clusters.map { token ->
            token.parts.mapIndexed { i, part ->
                val newIx = partShift + i
                newIx to part
            }.also {
                partShift += token.parts.size
            }
        }
        clusterPartsIxs = indexedParts.map { ps -> ps.map { it.first } }
        partInsides = clusters.flatMap { cluster ->
            cluster.parts.map { it.insides.sorted() }
        }
        partOutsides = clusters.flatMap { cluster ->
            cluster.parts.map { it.outsides.sorted() }
        }
        partFillColor = indexedParts.flatten().map { (_, part) -> part.fillColor }
        partBorderColor = indexedParts.flatten().map { (_, part) -> part.borderColor }
        circleCount = originalDdc.content
            .lastOrNull()?.let {
                if (it is Ddc.Token.Circle)
                    it.index
                else (it as Ddc.Token.Cluster).indices.last()
            } ?: 0
        xs = DoubleArray(circleCount)
        ys = DoubleArray(circleCount)
        rs = DoubleArray(circleCount)
        rules = mutableListOf()
        for (token in originalDdc.content) {
            when (token) {
                is Ddc.Token.Circle -> {
                    xs[token.index] = token.x
                    ys[token.index] = token.y
                    rs[token.index] = token.radius
                    rules[token.index] = token.rule
                }
                is Ddc.Token.Cluster -> {
                    val firstIndex = token.indices.first()
                    token.circles.forEachIndexed { i, circle ->
                        xs[firstIndex + i] = circle.x
                        ys[firstIndex + i] = circle.y
                        rs[firstIndex + i] = circle.radius
                        rules[firstIndex + i] = token.rule
                    }
                }
            }
        }
        xs0 = DoubleArray(circleCount) { i -> xs[i]}
        ys0 = DoubleArray(circleCount) { i -> ys[i]}
        rs0 = DoubleArray(circleCount) { i -> rs[i]}
    }

    fun update() {}
    fun draw() {}

    companion object {
        fun fromDdc(ddc: Ddc): DdcPresenter {

        }
    }
}