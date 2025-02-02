package domain

import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.EPSILON
import data.geometry.Line
import data.geometry.Point
import data.geometry.RegionPointLocation
import domain.cluster.ArcPath
import domain.cluster.ClosedArcPath

/** [ins] and [outs] delimiters must not contain `null` circles */
fun compressConstraints(
    circles: List<CircleOrLine?>,
    ins: List<Ix>,
    outs: List<Ix>,
): Pair<Set<Ix>, Set<Ix>> {
    val (sievedIns, sievedOuts) =
        compressConstraintsByRelativeContainment(circles, ins, outs)
    val (essentialInsIxs, essentialOutsIxs) =
        compressConstraintsByIntersectionPoints(
            sievedIns.map { circles[it]!! },
            sievedOuts.map { circles[it]!! }
        )
    val essentialIns = essentialInsIxs.map { sievedIns[it] }
    val essentialOuts = essentialOutsIxs.map { sievedOuts[it] }
    return Pair(essentialIns.toSet(), essentialOuts.toSet())
}

fun compressConstraintsByRelativeContainment(
    circles: List<CircleOrLine?>,
    ins: List<Ix>,
    outs: List<Ix>,
): Pair<List<Ix>, List<Ix>> {
    // NOTE: these do not take into account more complex
    //  "intersection is always inside x" type relationships,
    //  we leave it to compressRegionsByIntersectionPoints
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
fun compressConstraintsByIntersectionPoints(
    ins: List<CircleOrLine>,
    outs: List<CircleOrLine>,
): Pair<List<Ix>, List<Ix>> {

    fun testIfPointFitsOurRequirements(point: Point): Boolean =
        ins.all { it.calculateLocationEpsilon(point) != RegionPointLocation.OUT } && // inside or bordering ins
        outs.all { it.calculateLocationEpsilon(point) != RegionPointLocation.IN } // outside or bordering outs

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
                        essentialIns.all { ins[it].calculateLocationEpsilon(ip) != RegionPointLocation.OUT } && // inside or bordering ins
                        essentialOuts.all { outs[it].calculateLocationEpsilon(ip) != RegionPointLocation.IN } // outside or bordering outs
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


fun constraints2arcpaths(
    ins: List<Ix>,
    outs: List<Ix>,
    allCircles: List<CircleOrLine>,
): List<ClosedArcPath> {
    val inCircles = ins.map { allCircles[it] }
    val outCircles = outs.map { allCircles[it] }

    fun testIfPointFitsOurRequirements(point: Point): Boolean =
        inCircles.all { it.calculateLocationEpsilon(point) != RegionPointLocation.OUT } && // inside or bordering ins
        outCircles.all { it.calculateLocationEpsilon(point) != RegionPointLocation.IN } // outside or bordering outs

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
    val paths = mutableListOf<ClosedArcPath>()
    while (arcsByStart.isNotEmpty()) {
        val possibleContinuations = arcsByStart[focus] ?: emptyList()
        when (possibleContinuations.size) {
            0 -> {
                arcsByStart.remove(focus)
                if (arcPath.isNotEmpty()) {
                    val isClosed = arcPath.first().startPointIndex == arcPath.last().endPointIndex
                    if (isClosed)
                        paths.add(ClosedArcPath(
                            vertices = TODO(),
                            arcs = arcPath.map { 1 + it.circleIndex }
                        ))
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
        if (isClosed)
            paths.add(ClosedArcPath(
                vertices =
                    if (arcPath.size == 1) emptyList()
                    else TODO(),
                arcs = arcPath.map { 1 + it.circleIndex })
            )
    }
    val fullArcs = arcs.filterIsInstance<Arc.Full>()
    for (fullArc in fullArcs) {
        val i = fullArc.circleIndex
        paths.add(ClosedArcPath(
            vertices = emptyList(),
            arcs = listOf(i + 1))
        )
    }
    return paths
}