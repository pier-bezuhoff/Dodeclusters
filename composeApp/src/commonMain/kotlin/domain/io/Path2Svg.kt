package domain.io

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import data.geometry.Point
import data.geometry.calculateAngle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

/** Patched version of Path.toSvg that treats all conic (aka rational quadratic bezier)
 * segments as circular arcs */
fun Path.toCircularSvg() = buildString {
    val iterator = this@toCircularSvg.iterator(conicEvaluation = PathIterator.ConicEvaluation.AsConic)
    val points = FloatArray(8)
    var lastType = PathSegment.Type.Done
    if (iterator.hasNext()) {
        while (iterator.hasNext()) {
            val type = iterator.next(points)
            when (type) {
                PathSegment.Type.Move -> {
                    append("${command(PathSegment.Type.Move, lastType)}${points[0]} ${points[1]}")
                }
                PathSegment.Type.Line -> {
                    append("${command(PathSegment.Type.Line, lastType)}${points[2]} ${points[3]}")
                }
                PathSegment.Type.Quadratic -> {
                    append(command(PathSegment.Type.Quadratic, lastType))
                    append("${points[2]} ${points[3]} ${points[4]} ${points[5]}")
                }
                PathSegment.Type.Conic ->
                    append(conicSegment2arc(points))
                PathSegment.Type.Cubic -> {
                    append(command(PathSegment.Type.Cubic, lastType))
                    append("${points[2]} ${points[3]} ")
                    append("${points[4]} ${points[5]} ")
                    append("${points[6]} ${points[7]}")
                }
                PathSegment.Type.Close -> {
                    append(command(PathSegment.Type.Close, lastType))
                }
                PathSegment.Type.Done -> continue // Won't happen inside this loop
            }
            lastType = type
        }
    }
}

private fun command(type: PathSegment.Type, lastType: PathSegment.Type) =
    if (type != lastType) {
        when (type) {
            PathSegment.Type.Move -> "M"
            PathSegment.Type.Line -> "L"
            PathSegment.Type.Quadratic -> "Q"
            PathSegment.Type.Cubic -> "C"
            PathSegment.Type.Close -> "Z"
            else -> ""
        }
    } else " "

// reference: https://www.cl.cam.ac.uk/teaching/2000/AGraphHCI/SMEG/node5.html
// rational quadratic bezier:
// P(t) = ( (1-t)^2*P0 + 2*w*t*(1-t)*P1 + t^2*P2 )/( (1-t)^2 + 2*w*t*(1-t) + t^2 )
// ex: Conic(Circle(0, 0; 1)) = (0,1), (1,1), (1,0); 1/sqrt(2)
// projective transform on control points = projective transform on the whole shape (regardless of weights)
// reference: https://pages.mtu.edu/~shene/COURSES/cs3621/NOTES/spline/NURBS/NURBS-property.html
private fun conicSegment2arc(points: FloatArray): String {
    val p0x = points[0]
    val p0y = points[1]
    val p1x = points[2]
    val p1y = points[3]
    val p2x = points[4]
    val p2y = points[5]
//    val w = points[6]
    // we assume circular arc, so the weight satisfies t4 coefficient expansion:
    // |p0 + p2 - 2*w*p1| = 2*(1-w)
    // and p1 must lie on both tangents in p0 and p2 to the circle
    val p0 = Point(p0x.toDouble(), p0y.toDouble())
    val p1 = Point(p1x.toDouble(), p1y.toDouble())
    val p2 = Point(p2x.toDouble(), p2y.toDouble())
    val p0p1p2 = calculateAngle(start = p0, center = p1, end = p2)
    if (abs(p0p1p2) > PI)
        println("conicSegment2arc: weird control point $points")
    // we average p0p1 and p2p1 just in case, they should be equal anyway
    val radius = (p0.distanceFrom(p1) + p2.distanceFrom(p1))/2.0 * abs(tan(p0p1p2/2.0))
//    val sweepAngle = PI - p0p1p2 // unless angle p1 is negative
    val xAxisAngle: Int = 0 // first axis rotation for ellipses
    val largeArcFlag: Int = 0 // 0 => small, i.e. sweepAngle < 180 (always true for non-negative weight)
    val sweepOrientationFlag: Int = if (p0p1p2 >= 0) 0 else 1 // 0 => positive orientation aka CCW, 1 => CW
    return "A $radius $radius $xAxisAngle $largeArcFlag $sweepOrientationFlag $p2x $p2y"
}