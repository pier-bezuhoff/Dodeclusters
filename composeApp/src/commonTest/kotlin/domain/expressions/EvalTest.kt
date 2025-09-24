package domain.expressions

import core.geometry.Circle
import core.geometry.CircleOrLine
import core.geometry.EPSILON
import core.geometry.Point
import core.geometry.assertAlmostEquals
import core.geometry.randomCircle
import core.geometry.randomPoint
import kotlin.test.Test
import kotlin.test.assertTrue

class EvalTest {

    @Test
    fun centerRadius() {
        assertAlmostEquals(
            Circle(10.0, 20.0, 5.0),
            computeCircleByCenterAndRadius(Point(10.0, 20.0), Point(10.0, 25.0))
        )
        repeat(100) {
            val point = randomPoint()
            val radiusPoint = randomPoint()
            val d = radiusPoint.distanceFrom(point)
            if (d.isFinite() && d > EPSILON)
                assertAlmostEquals(
                    Circle(point.x, point.y, d),
                    computeCircleByCenterAndRadius(point, radiusPoint)
                )
        }
    }

    @Test
    fun circleInversion() {
        repeat(1_000) {
            val circle = randomCircle()
            repeat(100) {
                val c = randomCircle()
                val c1 = computeCircleInversion(c, circle)
                assertTrue(c1 is CircleOrLine, "circle(circle) is CircleOrLine: $circle $ $c = $c1")
                assertAlmostEquals(
                    expected = c,
                    actual = computeCircleInversion(c1, circle),
                    "double inversion $circle^2 $ $c = ^1 $ $c1 = $c",
                    epsilon = 1e-2
                )
                val p = randomPoint()
                val p1 = computeCircleInversion(p, circle)
                assertTrue(p1 is Point, "circle(point) is Point: $circle $ $p = $p1")
                assertAlmostEquals(
                    expected = p,
                    actual = computeCircleInversion(p1, circle),
                    "double inversion $circle^2 $ $p = ^1 $ $p1 = $p",
                    epsilon = 1e-2
                )
            }
            assertAlmostEquals(
                Point.CONFORMAL_INFINITY,
                computeCircleInversion(circle.centerPoint, circle)
            )
            assertAlmostEquals(
                circle.centerPoint,
                computeCircleInversion(Point.CONFORMAL_INFINITY, circle)
            )
        }
    }
}