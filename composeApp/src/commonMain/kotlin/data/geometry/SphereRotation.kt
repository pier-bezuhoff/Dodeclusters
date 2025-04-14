package data.geometry

import domain.radians
import domain.squareSum
import kotlin.math.cos
import kotlin.math.sin

/** Sphere (0,0,0; 1) antipodes projected stereographicly */
private inline val Point.antipodal: Point get() {
    val l2 = squareSum(x, y)
    return Point(-x/l2, -y/l2)
}

/**
 * @return bi-inversion engines, generating sphere rotation (double speed)
 */
fun calculateSphereRotationBiEngine(
    sphereProjection: Circle,
    start: Point,
    end: Point,
): Pair<CircleOrLine, CircleOrLine>? {
    // 1. Move into sphere c=(0,0,0); R=1 system
    val start0 = start.transformed(
        translationX = -sphereProjection.x,
        translationY = -sphereProjection.y,
        focusX = 0.0, // T;S;R order
        focusY = 0.0,
        zoom = 1f/sphereProjection.radius
    )
    val end0 = end.transformed(
        translationX = -sphereProjection.x,
        translationY = -sphereProjection.y,
        focusX = 0.0,
        focusY = 0.0,
        zoom = 1f/sphereProjection.radius
    )
    val startGC = GeneralizedCircle.fromGCircle(start0)
    val start2GC = GeneralizedCircle.fromGCircle(start0.antipodal)
    val endGC = GeneralizedCircle.fromGCircle(end0)
    val greatCircleGC = GeneralizedCircle.perp3(startGC, start2GC, endGC)
    var antipode1: Point? = null
    var antipode2: Point? = null
    when (val greatCircle = greatCircleGC?.toGCircle()) {
        is Circle -> {
            // this simple formula only works for antipodes of Great circles
            antipode1 = Point(greatCircle.x/(1 + greatCircle.radius), greatCircle.y/(1 + greatCircle.radius))
            antipode2 = Point(greatCircle.x/(1 - greatCircle.radius), greatCircle.y/(1 - greatCircle.radius))
        }
        is Line -> {
            antipode1 = Point(greatCircle.normalX, greatCircle.normalY)
            antipode2 = Point(-greatCircle.normalX, -greatCircle.normalY)
        }
        else -> {}
    }
    if (antipode1 == null || antipode2 == null)
        return null
    val engine1 = GeneralizedCircle.perp3(
        startGC, GeneralizedCircle.fromGCircle(antipode1), GeneralizedCircle.fromGCircle(antipode2)
    )?.toGCircle() as? CircleOrLine
    val engine2 = GeneralizedCircle.perp3(
        endGC, GeneralizedCircle.fromGCircle(antipode1), GeneralizedCircle.fromGCircle(antipode2)
    )?.toGCircle() as? CircleOrLine
    return if (engine1 != null && engine2 != null)
        Pair(
            engine1
                .scaled(0.0, 0.0, sphereProjection.radius)
                .translated(sphereProjection.x, sphereProjection.y)
            ,
            engine2
                .scaled(0.0, 0.0, sphereProjection.radius)
                .translated(sphereProjection.x, sphereProjection.y)
            ,
        )
    else null
}

/**
 * Generate stereographic images of parallels and meridians
 * @param[angleStep] in degrees
 */
fun generateSphereGrid(sphereProjection: Circle, angleStep: Int): List<CircleOrLine> {
    val lineAngles = 0 until 180 step angleStep
    val lines = lineAngles.map { angle ->
        val radians = angle.toFloat().radians
        Line(cos(radians), sin(radians), 0.0)
            .translated(sphereProjection.x, sphereProjection.y)
    }
    val circleAngles = (angleStep - 90) until 90 step angleStep
    val circles = circleAngles.map { angle ->
        val radians = angle.toFloat().radians
        sphereProjection.copy(
            radius = sphereProjection.radius * (cos(radians) /(1 - sin(radians)))
        )
    }
    return circles + lines
}
