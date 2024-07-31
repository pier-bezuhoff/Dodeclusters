package data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON
import data.geometry.Line
import data.geometry.Point
import data.io.Ddc
import kotlinx.serialization.Serializable
import ui.edit_cluster.Ix
import kotlin.math.abs

@Serializable
@Immutable
data class Cluster(
    val circles: List<CircleOrLine>,
    /** union of parts comprised of circle intersections */
    val parts: List<Part> = emptyList(),
    /** fill regions inside / wireframe */
    val filled: Boolean = Ddc.DEFAULT_CLUSTER_FILLED,
) {
    /** intersection of insides and outside of circles of a cluster */
    @Serializable
    @Immutable
    data class Part(
        /** indices of interior circles */
        val insides: Set<Int>,
        /** indices of bounding complementary circles */
        val outsides: Set<Int>,
        @Serializable(ColorCssSerializer::class)
        val fillColor: Color = Ddc.DEFAULT_CLUSTER_FILL_COLOR,
        @Serializable(ColorCssSerializer::class)
        val borderColor: Color? = Ddc.DEFAULT_CLUSTER_BORDER_COLOR,
    ) {
        override fun toString(): String =
            "Cluster.Part(in = [${insides.joinToString()}],\nout = [${outsides.joinToString()}],\ncolor = $fillColor)"

        /** ruff semiorder ⊆ on delimited regions; only goes off indices */
        infix fun isObviouslyInside(otherPart: Part): Boolean =
            // the more intersections the smaller the delimited region is
            insides.containsAll(otherPart.insides) &&
            outsides.containsAll(otherPart.outsides)
    }

    companion object {
        val SAMPLE = Cluster(
            circles = listOf(Circle(200.0, 100.0, 50.0)),
            parts = emptyList(),
            filled = true
        )
    }
}

@Serializable
@Immutable
data class OldCluster(
    val circles: List<Circle>,
    /** union of parts comprised of circle intersections */
    val parts: List<Cluster.Part> = emptyList(),
    /** fill regions inside / wireframe */
    val filled: Boolean = Ddc.DEFAULT_CLUSTER_FILLED,
)

// BUG: with "[" shape
fun compressPartToEssentials(
    ins: List<CircleOrLine>,
    outs: List<CircleOrLine>,
): Triple<List<Ix>, List<Ix>, List<Point>> {
//    ): Pair<List<Ix>, List<Ix>> {

    fun testIfPointFitsOurRequirements(point: Point): Boolean =
        ins.all { it.checkPositionEpsilon(point) <= 0 } && // inside or bordering ins
        outs.all { it.checkPositionEpsilon(point) >= 0 } // outside or bordering outs

    val allCircles = ins + outs
    val n = allCircles.size
    val nIns = ins.size
    val intersections = mutableListOf<Point>()
    val _intersections = mutableListOf<Point>()
    // circle ix -> ip ixs
    val circle2points: List<MutableSet<Int>> =
        allCircles.indices.map { mutableSetOf() }
    for (i in 0 until n) {
        for (j in (i+1) until n) {
            val c1 = allCircles[i]
            val c2 = allCircles[j]
            val ips = Circle.calculateIntersectionPoints(c1, c2)
            for (ip in ips) {
//                _intersections.add(ip)
                val repeatIx = intersections.indexOfFirst { ip.distanceFrom(it) < EPSILON }
                if (repeatIx == -1) { // new ip
                    val itFits = testIfPointFitsOurRequirements(ip)
                    if (itFits) {
                        val ix = intersections.size
                        intersections.add(ip)
                        circle2points[i].add(ix)
                        circle2points[j].add(ix)
                    }
                } else {
                    circle2points[i].add(repeatIx)
                    circle2points[j].add(repeatIx)
                }
            }
        }
    }
    val essentialIns = mutableListOf<Ix>()
    val essentialOuts = mutableListOf<Ix>()
    for (i in 0 until n) {
        val c = allCircles[i]
        val orderedIPs = c.orderPoints(circle2points[i].map { intersections[it] })
        val m = orderedIPs.size
        if (m == 0) {
            val mid = c.order2point(0.0) // no ips, checking random point on c
            _intersections.add(mid)
            val itFits = testIfPointFitsOurRequirements(mid)
            if (itFits) {
                if (i < nIns)
                    essentialIns.add(i)
                else
                    essentialOuts.add(i - nIns)
            }
        } else {
            for (k in 0 until m) {
                val mid: Point =
                    if (k == 0 && c is Line) {
                        val ip2 = orderedIPs[k]
                        c.pointInBetween(Point.CONFORMAL_INFINITY, ip2)
                    } else {
                        val prevK =
                            if (k == 0)
                                m - 1
                            else k - 1
                        val ip1 = orderedIPs[prevK]
                        val ip2 = orderedIPs[k]
                        c.pointInBetween(ip1, ip2)
                    }
                _intersections.add(mid)
                val itFits = testIfPointFitsOurRequirements(mid)
                if (itFits) {
                    if (i < nIns)
                        essentialIns.add(i)
                    else
                        essentialOuts.add(i - nIns)
                    break // the circle is in, no need to check other ip arcs
                }
            }
        }
    }
    // salvaging all-concave (not strictly convex) parts
    val allConcave = essentialIns.none { ins[it] is Circle }
    if (allConcave && ins.isNotEmpty())
        essentialIns.add(
            ins.withIndex().minBy { (ix, circle) ->
                when (circle) {
                    is Line -> Double.POSITIVE_INFINITY
                    is Circle -> abs(circle.radius)
                }
            }.index
        )
//    return Pair(essentialIns, essentialOuts)
    return Triple(essentialIns, essentialOuts, _intersections)
}