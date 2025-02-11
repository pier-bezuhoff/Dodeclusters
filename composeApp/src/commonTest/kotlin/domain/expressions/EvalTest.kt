package domain.expressions

import data.geometry.CircleOrLine
import data.geometry.Point
import data.geometry.assertAlmostEquals
import data.geometry.randomCircle
import data.geometry.randomPoint
import kotlin.test.Test
import kotlin.test.assertTrue

class EvalTest {

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
                    actual = computeCircleInversion(c1, circle)!!,
                    "double inversion $circle^2 $ $c = ^1 $ $c1 = $c",
                    epsilon = 1e-2
                )
                val p = randomPoint()
                val p1 = computeCircleInversion(p, circle)
                assertTrue(p1 is Point, "circle(point) is Point: $circle $ $p = $p1")
                assertAlmostEquals(
                    expected = p,
                    actual = computeCircleInversion(p1, circle)!!,
                    "double inversion $circle^2 $ $p = ^1 $ $p1 = $p"
                )
            }
            assertAlmostEquals(
                Point.CONFORMAL_INFINITY,
                computeCircleInversion(circle.centerPoint, circle)!!
            )
            assertAlmostEquals(
                circle.centerPoint,
                computeCircleInversion(Point.CONFORMAL_INFINITY, circle)!!
            )
        }
    }
}