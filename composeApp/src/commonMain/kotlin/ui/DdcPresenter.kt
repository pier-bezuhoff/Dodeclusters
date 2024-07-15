package ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.colormath.transform.interpolate
import data.Cluster
import data.io.Ddc
import ui.edit_cluster.Ix
import kotlin.math.exp

/** indices */
typealias Indices = List<Int>

// naked top-level circle = cluster with 1 part = [this circle]
// Analogue to Dodeca Meditation's CircleGroup
// MAYBE: use projective circle representation that would also include true straight lines among other benefits
/** Dynamic model of [Ddc], that efficiently encapsulates [draw] and [update] operations */
class DdcPresenter(
    /** # of all clusters + visible circles */
    val clusterCount: Int,
    val partCount: Int,
    /** # of all the circles */
    val circleCount: Int,

    val clusterFilled: List<Boolean>,
    val clusterPartsIndices: List<Indices>,
    val partInsides: List<Indices>,
    val partOutsides: List<Indices>,
    val partFillColor: List<Color>,
    val partBorderColor: List<Color?>,

    // xs0 = last state, xs = current one
    val xs: DoubleArray,
    val ys: DoubleArray,
    /** negative radius = outside-circle, positive = inside-circle */
    val rs: DoubleArray,
    val rules: List<Indices>
) {
    val clusterIndices = 0 until clusterCount
    val circleIndices = 0 until circleCount
    var xs0: DoubleArray = xs.copyOf()
    var ys0: DoubleArray = ys.copyOf()
    /** negative radius = outside-circle, positive = inside-circle */
    var rs0: DoubleArray = rs.copyOf()

    fun update() {
        for (i in 0 until circleCount)
            for (j in rules[i])
                invert(i, j)
        xs0 = xs.copyOf()
        ys0 = ys.copyOf()
        rs0 = rs.copyOf()
    }

    fun draw() {
        for (i in clusterIndices)
            drawCluster(i)
    }

    private fun drawCluster(i: Int) {
        val filled = clusterFilled[i]
        for (partIx in clusterPartsIndices[i])
            drawPart(partIx, filled)
    }

    private fun drawPart(partIx: Int, filled: Boolean) {
        val path = part2path(partIx)
        TODO("draw on canvas")
    }

    // NOTE: to create proper reduce(xor):
    // 2^(# circles) -> binary -> filter even number of 1 -> to parts
    // MAYBE: move to Cluster methods
    // MAYBE: add threading cuz it can be slow (esp. 100+ circles with xor)
    private fun part2path(partIx: Int): Path {
        val insidePath: Path? = partInsides[partIx]
            .map { circle2path(it) }
            .reduceOrNull { acc: Path, anotherPath: Path ->
                Path.combine(PathOperation.Intersect, path1 = acc, path2 = anotherPath)
            }
        return if (insidePath == null) { // chessboard pattern case
            circleIndices.map { circle2path(it) }
                // TODO: encapsulate as a separate tool
                // NOTE: reduce(xor) on outsides = makes binary interlacing pattern
                // cuz it makes even # of layers = transparent, odd = filled
                // NOTE: soft limit is 500-1k circles for this to have bearable performance
                .reduceOrNull { acc: Path, anotherPath: Path ->
                    Path.combine(PathOperation.Xor, acc, anotherPath)
                } ?: Path()
        } else if (partOutsides[partIx].isEmpty())
            insidePath
        else
            partOutsides[partIx].fold(insidePath) { acc: Path, circleOutsideIx: Int ->
                Path.combine(PathOperation.Difference, acc, circle2path(circleOutsideIx))
            }
    }

    private fun circle2path(i: Int): Path =
        Path().apply {
            addOval(
                Rect(
                    center = Offset(xs0[i].toFloat(), ys0[i].toFloat()),
                    radius = rs0[i].toFloat()
                )
            )
        }

    // MAYBE: apply mixing based on initial color & radius instead: Δcolor = f(radius0, Δradius)
    private fun mixColor(baseColor: Color, radius: Double): Color {
        val base = RGB(baseColor.red, baseColor.green, baseColor.blue)
        val end = RGB("#ffffff") // TODO: choose from the UI
        val bigRadius = 1_000.0
        // t: 0 = base, 1 = end
        val t = 1/(1 + exp(-1/(radius/bigRadius)))
        // R = -0 --> 0 = base (whole screen)
        // R = +0 --> 1 = end (single dot)
        // R = +-inf --> 1/2 (line/half-screen)
        val softening = 0.9 // let's leave *some* base tint on dots
        val mixed = base.interpolate(end, softening*t)
        return Color(mixed.redInt, mixed.greenInt, mixed.blueInt)
    }

    /** invert the i-th circle with respect to the j-th *old* circle */
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
                        is Ddc.Token.Line -> TODO()
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
                    is Ddc.Token.Line -> TODO()
                    is Ddc.Token.Cluster -> {
                        val firstIndex = token.indices.first()
                        token.circles.forEachIndexed { i, circle ->
                            TODO()
//                            xs[firstIndex + i] = circle.x
//                            ys[firstIndex + i] = circle.y
//                            rs[firstIndex + i] = circle.radius
//                            rules[firstIndex + i] = token.rule
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