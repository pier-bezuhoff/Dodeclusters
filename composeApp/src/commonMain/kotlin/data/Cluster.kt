package data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON
import data.geometry.Point
import data.io.Ddc
import kotlinx.serialization.Serializable
import ui.edit_cluster.Ix

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

fun compressPartToEssentials(
    ins: List<CircleOrLine>,
    outs: List<CircleOrLine>,
    pointInside: Offset
): Pair<List<Ix>, List<Ix>> {
    // compute all intersections with circles they are on
    // filter half-in's
    // filter arcs between them
    // arc => full circle is essential
    val allCircles = ins + outs
    val n = allCircles.size
    val nIns = ins.size
    val intersections = mutableListOf<Point>()
    // circle ix -> ip ixs
    val circle2points: List<MutableSet<Int>> =
        allCircles.indices.map { mutableSetOf() }
    for (i in 0 until n) {
        for (j in (i+1) until n) {
            val c1 = allCircles[i]
            val c2 = allCircles[j]
            val ips = Circle.calculateIntersectionPoints(c1, c2)
            for (ip in ips) {
                val repeatIx = intersections.indexOfFirst { ip.distanceFrom(it) < EPSILON }
                if (repeatIx == -1) { // new ip
                    val itFits =
                        ins.all { it.checkPositionEpsilon(ip) <= 0 } &&
                        outs.all { it.checkPositionEpsilon(ip) >= 0 }
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
        for (k in 0 until m) {
            val prevK =
                if (k == 0)
                    m - 1
                else k - 1
            val ip1 = orderedIPs[prevK]
            val ip2 = orderedIPs[k]
            // order matters, arc(ip1,ip2) != arc(ip2,ip1)
            // possible: ip1 == ip2
            val mid = c.midArc(ip1, ip2)
            val itFits =
                ins.all { it.checkPositionEpsilon(mid) <= 0 } &&
                outs.all { it.checkPositionEpsilon(mid) >= 0 }
            if (itFits) {
                if (i < nIns)
                    essentialIns.add(i)
                else
                    essentialOuts.add(i)
                break // the circle is in, no need to check other ip arcs
            }
        }
    }
    return Pair(essentialIns, essentialOuts)
}