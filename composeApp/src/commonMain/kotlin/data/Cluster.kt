package data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON
import data.geometry.Line
import data.geometry.Point
import domain.ColorCssSerializer
import domain.Ix
import domain.io.Ddc
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class Cluster(
    val circles: List<CircleOrLine>,
    /** union of parts comprised of circle intersections */
    val parts: List<Part> = emptyList(),
    /** fill regions inside / wireframe */
    val filled: Boolean = Ddc.DEFAULT_CLUSTER_FILLED,
) {
    // NOTE: we can alternatively use 1 BooleanArray[circles.size] to specify part bounds
    //  out of the circles, and another BooleanArray[insides.size + outsides.size] to specify
    //  which are in and which are out
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


/** [ins] and [outs] delimiters must not contain `null` circles */
fun compressPart(
    circles: List<CircleOrLine?>,
    ins: List<Ix>,
    outs: List<Ix>,
): Pair<Set<Ix>, Set<Ix>> {
    val (sievedIns, sievedOuts) =
        compressPartByRelativeContainment(circles, ins, outs)
    val (essentialInsIxs, essentialOutsIxs) =
        compressPartByIntersectionPoints(
            sievedIns.map { circles[it]!! },
            sievedOuts.map { circles[it]!! }
        )
    val essentialIns = essentialInsIxs.map { sievedIns[it] }
    val essentialOuts = essentialOutsIxs.map { sievedOuts[it] }
    return Pair(essentialIns.toSet(), essentialOuts.toSet())
}

fun compressPartByRelativeContainment(
    circles: List<CircleOrLine?>,
    ins: List<Ix>,
    outs: List<Ix>,
): Pair<List<Ix>, List<Ix>> {
    // NOTE: these do not take into account more complex
    //  "intersection is always inside x" type relationships,
    //  we leave it to compressPartByIntersectionPoints
    val excessiveIns = ins.filter { inJ -> // NOTE: tbh idt these can occur naturally
        val circle = circles[inJ]!!
        ins.any { otherIn ->
            otherIn != inJ && circles[otherIn]!! isInside circle // we only leave the smallest 'in'
        } || outs.any { otherOut ->
            circle isInside circles[otherOut]!! // if an 'in' isInside an 'out' it is empty
        }
    }
    val excessiveOuts = outs.filter { outJ ->
        val circle = circles[outJ]!!
        outs.any { otherOut ->
            otherOut != outJ && circle isInside circles[otherOut]!! // we only leave the biggest 'out'
        } || ins.any { otherIn ->
            circle isOutside circles[otherIn]!! // if an 'out' isOutside an 'in' it is empty
        }
    }
    val sievedIns = ins.minus(excessiveIns.toSet())
    val sievedOuts = outs.minus(excessiveOuts.toSet())
    return Pair(sievedIns, sievedOuts)
}

// TODO: skip extremely small arcs (they can lead to display artifacts)
/** Filters out all unused 'in' and 'out' separators by checking intersection points */
fun compressPartByIntersectionPoints(
    ins: List<CircleOrLine>,
    outs: List<CircleOrLine>,
): Pair<List<Ix>, List<Ix>> {

    fun testIfPointFitsOurRequirements(point: Point): Boolean =
        ins.all { it.checkPositionEpsilon(point) <= 0 } && // inside or bordering ins
        outs.all { it.checkPositionEpsilon(point) >= 0 } // outside or bordering outs

    val allCircles = ins + outs
    val n = allCircles.size
    val nIns = ins.size
    val intersections = mutableListOf<Point>()
    // circle ix -> ip ixs
    val circle2points: List<MutableSet<Int>> =
        allCircles.indices.map { mutableSetOf() }
    // compute all distinct intersections bordering our region, noting which circles they belong to
    for (i in 0 until n) {
        for (j in (i+1) until n) {
            val c1 = allCircles[i]
            val c2 = allCircles[j]
            val ips = Circle.calculateIntersectionPoints(c1, c2)
            for (ip in ips) {
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
    // find all the circles arcs of which define the edges of our region
    for (i in 0 until n) {
        val c = allCircles[i]
        val orderedIPs = c.orderPoints(circle2points[i].map { intersections[it] })
        val m = orderedIPs.size
        if (m == 0) {
            val mid = c.order2point(0.0) // no ips, checking random point on c
            val itFits = testIfPointFitsOurRequirements(mid)
            if (itFits) {
                if (i < nIns)
                    essentialIns.add(i)
                else
                    essentialOuts.add(i - nIns)
            }
        } else {
            for (k in 0 until m) {
                // TODO: keep track of 'order' and calculate arc length to skip extremely small arcs
                val ip2 = orderedIPs[k]
                val mid: Point =
                    if (k == 0 && c is Line && ip2 != Point.CONFORMAL_INFINITY) {
                        c.pointInBetween(Point.CONFORMAL_INFINITY, ip2)
                    } else {
                        val prevK =
                            if (k == 0)
                                m - 1
                            else k - 1
                        val ip1 = orderedIPs[prevK]
                        c.pointInBetween(ip1, ip2)
                    }
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
    // compute extra intersections formed only by the edges
    val allEssentialCircles = essentialIns.map { ins[it] } + essentialOuts.map { outs[it] }
    val extendedIntersections = mutableListOf<Point>()
    for (i in allEssentialCircles.indices) {
        for (j in (i+1) until allEssentialCircles.size) {
            val c1 = allEssentialCircles[i]
            val c2 = allEssentialCircles[j]
            val ips = Circle.calculateIntersectionPoints(c1, c2)
            for (ip in ips) {
                val repeatIx = extendedIntersections.indexOfFirst { ip.distanceFrom(it) < EPSILON }
                if (repeatIx == -1) { // new ip
                    val itFits =
                        essentialIns.all { ins[it].checkPositionEpsilon(ip) <= 0 } && // inside or bordering ins
                        essentialOuts.all { outs[it].checkPositionEpsilon(ip) >= 0 } // outside or bordering outs
                    if (itFits)
                        extendedIntersections.add(ip)
                }
            }
        }
    }
    val unwantedIntersections = extendedIntersections.toSet() - intersections.toSet()
    // find the one additional circle isolating unwanted intersections
    if (unwantedIntersections.isNotEmpty()) {
        val inSeparator = (ins.indices - essentialIns.toSet()).firstOrNull { inIx ->
            unwantedIntersections.all { ins[inIx].hasOutsideEpsilon(it) }
        }
        if (inSeparator != null) {
            essentialIns.add(inSeparator)
        } else {
            val outSeparator =
                (outs.indices - essentialOuts.toSet()).firstOrNull { outIx ->
                    unwantedIntersections.all { outs[outIx].hasInsideEpsilon(it) }
                }
            if (outSeparator != null) {
                essentialOuts.add(outSeparator)
            } // MAYBE: otherwise artificially add a befitting separator (intersections <-|-> unwantedIntersections)
        }
    }
    return Pair(essentialIns, essentialOuts)
}