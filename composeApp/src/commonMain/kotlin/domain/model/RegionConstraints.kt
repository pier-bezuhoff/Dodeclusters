package domain.model

import androidx.compose.runtime.Immutable
import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.CircleOrLineOrConcreteArcPath
import core.geometry.EPSILON
import core.geometry.EPSILON2
import core.geometry.Line
import core.geometry.Point
import core.geometry.Region
import core.geometry.isInside
import core.geometry.isOutside
import core.geometry.liesInside
import core.geometry.liesOnOrInside
import core.geometry.liesOnOrOutside
import core.geometry.liesOutside
import domain.Ix

/**
 * @param[insides] Indices of circles inside which we are
 * @param[outsides] Indices of circles outside of which we are
 */
@Immutable
data class RegionConstraints(
    /** indices of interior circles */
    val insides: List<Ix>,
    /** indices of bounding complementary circles */
    val outsides: List<Ix>,
) {
    // MAYBE: also separately process open arc-paths (potentially as outs)
    //  that have >2 intersections with the rest of essentials, or that have self-intersections
    companion object {
        /**
         * [ins] and [outs] delimiters must be indices of [CircleOrLine] or
         * closed [core.geometry.ConcreteArcPath].
         */
        fun compressConstraints(
            allObjects: List<*>,
            ins: List<Ix>,
            outs: List<Ix>,
        ): RegionConstraints {
            val (sievedIns, sievedOuts) =
                compressConstraintsByRelativeContainment(allObjects, ins, outs)
            val (essentialInsIxs, essentialOutsIxs) =
                compressConstraintsByIntersectionPoints(
                    sievedIns.map { ix ->
                        allObjects[ix] as CircleOrLineOrConcreteArcPath
                    },
                    sievedOuts.map { ix ->
                        allObjects[ix] as CircleOrLineOrConcreteArcPath
                    }
                )
            val essentialIns = essentialInsIxs.map { sievedIns[it] }
            val essentialOuts = essentialOutsIxs.map { sievedOuts[it] }
            return RegionConstraints(essentialIns, essentialOuts)
        }

    }
}

/**
 * [inConstraints] and [outConstraints] delimiters must be indices of [CircleOrLine].
 */
private fun compressConstraintsByRelativeContainment(
    allObjects: List<*>,
    inConstraints: List<Ix>,
    outConstraints: List<Ix>,
): RegionConstraints {
    // NOTE: these do not take into account more complex
    //  "intersection is always inside x" type relationships,
    //  we leave it to compressRegionsByIntersectionPoints
    val excessiveIns = inConstraints.filter { inJ -> // NOTE: tbh idt these can occur naturally
        val region = allObjects[inJ] as Region
        inConstraints.any { otherIn ->
            val otherInRegion = allObjects[otherIn] as Region
            otherIn != inJ && otherInRegion isInside region // we only leave the smallest 'in'
        } || outConstraints.any { otherOut ->
            val otherOutRegion = allObjects[otherOut] as Region
            region isInside otherOutRegion // if an 'in' isInside an 'out' it is empty
        }
    }
    val excessiveOuts = outConstraints.filter { outJ ->
        val region = allObjects[outJ] as Region
        outConstraints.any { otherOut ->
            val otherOutRegion = allObjects[otherOut] as Region
            otherOut != outJ && region isInside otherOutRegion // we only leave the biggest 'out'
        } || inConstraints.any { otherIn ->
            val otherInRegion = allObjects[otherIn] as Region
            region isOutside otherInRegion // if an 'out' isOutside an 'in' it is empty
        }
    }
    val sievedIns = inConstraints.minus(excessiveIns.toSet())
    val sievedOuts = outConstraints.minus(excessiveOuts.toSet())
    return RegionConstraints(sievedIns, sievedOuts)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun pointSatisfiesConstraints(
    point: Point,
    inConstraints: List<Region>,
    outConstraints: List<Region>,
): Boolean =
    inConstraints.all { point liesOnOrInside it } &&
    outConstraints.all { point liesOnOrOutside it }

// TODO: skip extremely small arcs (they can lead to display artifacts)
/**
 * Filters out all unused 'in' and 'out' separators by checking intersection points.
 * [inConstraints] and [outConstraints] are guaranteed to contain only closed arc-paths.
 */
private fun compressConstraintsByIntersectionPoints(
    inConstraints: List<CircleOrLineOrConcreteArcPath>,
    outConstraints: List<CircleOrLineOrConcreteArcPath>,
): RegionConstraints {
    val allConstraints = inConstraints + outConstraints
    val n = allConstraints.size
    val nIns = inConstraints.size
    val intersections = mutableListOf<Point>()
    // circle ix -> ip ixs
    val constraint2points: List<MutableSet<Int>> =
        allConstraints.indices.map { mutableSetOf() }
    // compute all distinct intersections bordering our region, noting which circles they belong to
    for (i in 0 until n) {
        for (j in (i+1) until n) {
            val c1 = allConstraints[i]
            val c2 = allConstraints[j]
            val ips = c1.calculateIntersectionPoints(c2)
            for (ip in ips) {
                val repeatIx = intersections.indexOfFirst { ip.distance2From(it) < EPSILON2 }
                if (repeatIx == -1) { // new ip
                    if (pointSatisfiesConstraints(ip, inConstraints, outConstraints)) {
                        val ix = intersections.size
                        intersections.add(ip)
                        constraint2points[i].add(ix)
                        constraint2points[j].add(ix)
                    }
                } else {
                    constraint2points[i].add(repeatIx)
                    constraint2points[j].add(repeatIx)
                }
            }
        }
    }
    val essentialIns = mutableListOf<Ix>()
    val essentialOuts = mutableListOf<Ix>()
    // find all the circles, arcs of which define the boundary of our region
    for (i in 0 until n) {
        val c = allConstraints[i]
        val orderedIPs = c.orderPoints(constraint2points[i].map { intersections[it] })
        val m = orderedIPs.size
        if (m == 0) {
            val mid = c.order2point(0.0) // no ips, checking random point on c
            if (pointSatisfiesConstraints(mid, inConstraints, outConstraints)) {
                if (i < nIns)
                    essentialIns.add(i)
                else
                    essentialOuts.add(i - nIns)
            }
        } else {
            for (k in 0 until m) {
                // TODO: keep track of 'order' to skip extremely small arcs
                val ip2 = orderedIPs[k]
                val mid: Point =
                    if (k == 0 && c is Line && ip2 != Point.CONFORMAL_INFINITY) {
                        // cyclic order is valid everywhere (on circles & lines) EXCEPT the first point on a line
                        c.pointInBetween(Point.CONFORMAL_INFINITY, ip2) // (-inf; #0)
                    } else {
                        val prevK =
                            if (k == 0)
                                m - 1
                            else k - 1
                        val ip1 = orderedIPs[prevK]
                        c.pointInBetween(ip1, ip2)
                    }
                if (pointSatisfiesConstraints(mid, inConstraints, outConstraints)) {
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
    val allEssentialCircles = essentialIns.map { inConstraints[it] } + essentialOuts.map { outConstraints[it] }
    val extendedIntersections = mutableListOf<Point>()
    for (i in allEssentialCircles.indices) {
        for (j in (i+1) until allEssentialCircles.size) {
            val c1 = allEssentialCircles[i]
            val c2 = allEssentialCircles[j]
            val ips = c1.calculateIntersectionPoints(c2)
            for (ip in ips) {
                val repeatIx = extendedIntersections.indexOfFirst { ip.distance2From(it) < EPSILON2 }
                if (repeatIx == -1) { // new ip
                    val itFits =
                        essentialIns.all { ip liesOnOrInside inConstraints[it] } && // inside or bordering ins
                        essentialOuts.all { ip liesOnOrOutside outConstraints[it] } // outside or bordering outs
                    if (itFits)
                        extendedIntersections.add(ip)
                }
            }
        }
    }
    val unwantedIntersections = extendedIntersections.toSet() - intersections.toSet()
    // try finding an additional circle, isolating unwanted intersections
    if (unwantedIntersections.isNotEmpty()) {
        val inSeparator = (inConstraints.indices - essentialIns.toSet()).firstOrNull { inIx ->
            unwantedIntersections.all { it liesOutside inConstraints[inIx] }
        }
        if (inSeparator != null) {
            essentialIns.add(inSeparator)
        } else {
            val outSeparator =
                (outConstraints.indices - essentialOuts.toSet()).firstOrNull { outIx ->
                    unwantedIntersections.all { it liesInside outConstraints[outIx] }
                }
            if (outSeparator != null) {
                essentialOuts.add(outSeparator)
            } // MAYBE: otherwise artificially add a befitting separator (intersections <-|-> unwantedIntersections)
        }
    }
    return RegionConstraints(essentialIns, essentialOuts)
}

private sealed interface Arc {
    val circleIndex: Ix
    data class Normal(
        override val circleIndex: Ix,
        val startPointIndex: Ix,
        val endPointIndex: Ix,
    ) : Arc
    data class Full(
        override val circleIndex: Ix,
    ) : Arc
}


private fun constraints2arcpaths(
    ins: List<Ix>,
    outs: List<Ix>,
    allCircles: List<CircleOrLine>,
): List<Nothing> {
    val inCircles = ins.map { allCircles[it] }
    val outCircles = outs.map { allCircles[it] }

    fun testIfPointFitsOurRequirements(point: Point): Boolean =
        inCircles.all { !it.hasOutside(point) } && // inside or bordering ins
                outCircles.all { !it.hasInside(point) } // outside or bordering outs

    val n = allCircles.size
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
    val arcs = mutableSetOf<Arc>()
    // find all the circles, arcs of which define the edges of our region
    for (i in 0 until n) {
        val c = allCircles[i]
        val orderedIPs = c.orderPoints(circle2points[i].map { intersections[it] })
        val m = orderedIPs.size
        if (m == 0) {
            val mid = c.order2point(0.0) // no ips, checking random point on c
            val itFits = testIfPointFitsOurRequirements(mid)
            if (itFits) {
                arcs.add(Arc.Full(i))
            }
        } else {
            for (k in 0 until m) {
                // TODO: keep track of 'order' and calculate arc length to skip extremely small arcs
                val ip2: Point = orderedIPs[k]
                val ip1: Point =
                    if (k == 0 && c is Line && ip2 != Point.CONFORMAL_INFINITY) {
                        // cyclic order is valid everywhere (on circles & lines) EXCEPT the first point on a line
                        Point.CONFORMAL_INFINITY // (-inf; #0)
                    } else {
                        val prevK =
                            if (k == 0)
                                m - 1
                            else k - 1
                        orderedIPs[prevK]
                    }
                val mid: Point = c.pointInBetween(ip1, ip2)
                val itFits = testIfPointFitsOurRequirements(mid)
                if (itFits) {
                    arcs.add(Arc.Normal(i, intersections.indexOf(ip1), intersections.indexOf(ip2)))
                }
            }
        }
    }
    if (arcs.isEmpty())
        return emptyList()
    val arcsByStart: MutableMap<Ix, List<Arc.Normal>> = arcs
        .filterIsInstance<Arc.Normal>()
        .distinct()
        .groupBy { it.startPointIndex }
        .toMutableMap()
    var focus = arcsByStart.keys.first()
    var arcPath: List<Arc.Normal> = emptyList()
    val paths = mutableListOf<Nothing>()
    while (arcsByStart.isNotEmpty()) {
        val possibleContinuations = arcsByStart[focus] ?: emptyList()
        when (possibleContinuations.size) {
            0 -> {
                arcsByStart.remove(focus)
                if (arcPath.isNotEmpty()) {
                    val isClosed = arcPath.first().startPointIndex == arcPath.last().endPointIndex
                    if (isClosed) {
//                        paths.add(
//                            ClosedArcPath(vertices = TODO(), arcs = arcPath.map { 1 + it.circleIndex })
//                        )
                    }
                }
                arcPath = emptyList()
                focus = arcsByStart.keys.first()
            }
            1 -> {
                val arc = possibleContinuations.single()
                arcsByStart.remove(focus)
                arcPath = arcPath + arc
                focus = arc.endPointIndex
            }
            else -> {
                val currentCircle = arcPath.lastOrNull()?.circleIndex
                if (currentCircle == null) {
                    val arc = possibleContinuations.first()
                    arcsByStart[focus] = possibleContinuations.drop(1)
                    arcPath = arcPath + arc
                    focus = arc.endPointIndex
                } else { // in general we want to hop onto a different circle each time
                    val diffContinuations = possibleContinuations.filterNot { it.circleIndex == currentCircle }
                    if (diffContinuations.isEmpty()) {
                        println("constraints2arcpaths: no diff-circle arcs but multiple choices @ ip#$focus")
                        val arc = possibleContinuations.first()
                        arcsByStart[focus] = possibleContinuations.drop(1)
                        arcPath = arcPath + arc
                        focus = arc.endPointIndex
                    } else {
                        val arc = diffContinuations.first()
                        arcsByStart[focus] = possibleContinuations - arc
                        arcPath = arcPath + arc
                        focus = arc.endPointIndex
                    }
                }
            }
        }
    }
    if (arcPath.isNotEmpty()) {
        val isClosed = arcPath.first().startPointIndex == arcPath.last().endPointIndex
        if (isClosed) {
//            paths.add(ClosedArcPath(
//                vertices =
//                    if (arcPath.size == 1) emptyList()
//                    else TODO(),
//                arcs = arcPath.map { 1 + it.circleIndex })
//            )
        }
    }
    val fullArcs = arcs.filterIsInstance<Arc.Full>()
    for (fullArc in fullArcs) {
        val i = fullArc.circleIndex
//        paths.add(ClosedArcPath(
//            vertices = emptyList(),
//            arcs = listOf(i + 1))
//        )
    }
    return paths
}
