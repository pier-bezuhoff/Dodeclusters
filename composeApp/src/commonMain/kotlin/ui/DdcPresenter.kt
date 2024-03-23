package ui

import androidx.compose.ui.graphics.Color
import data.Cluster
import data.io.Ddc
import kotlin.math.abs

/** indices */
typealias Indices = List<Int>

// naked top-level circle = cluster with 1 part = [this circle]
// Analogue to Dodeca Meditation's CircleGroup
class DdcPresenter(
    val clusterCount: Int, // # of all clusters + visible circles
    val partCount: Int,
    val circleCount: Int, // # of all the circles

    val clusterFilled: List<Boolean>,
    val clusterPartsIndices: List<Indices>,
    val partInsides: List<Indices>,
    val partOutsides: List<Indices>,
    val partFillColor: List<Color>,
    val partBorderColor: List<Color?>,

    // xs0 = previous state, xs = current one
    val xs: DoubleArray,
    val ys: DoubleArray,
    /** negative radius = outside-circle, positive = inside-circle */
    val rs: DoubleArray,
    val rules: List<Indices>
) {
    val xs0: DoubleArray = xs.copyOf()
    val ys0: DoubleArray = ys.copyOf()
    val rs0: DoubleArray = rs.copyOf()

    fun update() {}
    fun draw() {}

    /* invert i-th circle with respect to j-th old circle */
    private fun invert(i: Ix, j: Ix) {
        val xi = xs[i]
        val yi = ys[i]
        val ri = rs[i]
        val xj = xs0[j]
        val yj = ys0[j]
        val rj = rs0[j]
        when {
            rj == 0.0 -> {
                xs[i] = xj
                ys[i] = yj
                rs[i] = 0.0
            }
            xi == xj && yi == yj ->
                rs[i] = rj * rj / ri
            else -> {
                val dx = xi - xj
                val dy = yi - yj
                var d2 = dx * dx + dy * dy
                val r2 = rj * rj
                val r02 = ri * ri
                if (d2 == r02) // if result should be a line
                    d2 += 1e-6f
                val ratio = r2 / (d2 - r02)
                xs[i] = xj + dx * ratio
                ys[i] = yj + dy * ratio
//                rs[i] = abs(ratio) * ri
                rs[i] = ratio * ri // NOTE: negative radius = outside-circle
            }
        }
    }

    companion object {
        fun fromDdc(ddc: Ddc): DdcPresenter {
            val clusters = ddc.content
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
            val clusterCount = clusters.size
            val clusterFilled = clusters.map { it.filled }
            val partCount = clusters.sumOf { it.parts.size }
            var partShift = 0
            val indexedParts = clusters.map { token ->
                token.parts.mapIndexed { i, part ->
                    val newIx = partShift + i
                    newIx to part
                }.also {
                    partShift += token.parts.size
                }
            }
            val clusterPartsIxs = indexedParts.map { ps -> ps.map { it.first } }
            val partInsides = clusters.flatMap { cluster ->
                cluster.parts.map { it.insides.sorted() }
            }
            val partOutsides = clusters.flatMap { cluster ->
                cluster.parts.map { it.outsides.sorted() }
            }
            val partFillColor = indexedParts.flatten().map { (_, part) -> part.fillColor }
            val partBorderColor = indexedParts.flatten().map { (_, part) -> part.borderColor }
            val circleCount = ddc.content
                .lastOrNull()?.let {
                    if (it is Ddc.Token.Circle)
                        it.index
                    else (it as Ddc.Token.Cluster).indices.last()
                } ?: 0
            val xs = DoubleArray(circleCount)
            val ys = DoubleArray(circleCount)
            val rs = DoubleArray(circleCount)
            val rules = mutableListOf<List<Int>>()
            for (token in ddc.content) {
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
            return DdcPresenter(
                clusterCount, partCount, circleCount, clusterFilled, clusterPartsIxs, partInsides, partOutsides, partFillColor, partBorderColor, xs, ys, rs, rules
            )
        }
    }
}