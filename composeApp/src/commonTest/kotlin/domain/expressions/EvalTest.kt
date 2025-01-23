package domain.expressions

import data.geometry.Circle
import data.geometry.CircleOrLine
import data.geometry.ImaginaryCircle
import data.geometry.Point
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class EvalTest {

    @Test
    fun circleInversion() {
        repeat(1_000) {
            val isCCW = Random.nextBoolean()
            val centerX = 500*Random.nextDouble()
            val centerY = 500*Random.nextDouble()
            val radius = 100*min(abs(Random.nextDouble()),1e-3)
            val circle = Circle(centerX, centerY, radius, isCCW)
            repeat(100) {
                val x = 500*Random.nextDouble()
                val y = 500*Random.nextDouble()
                val r = 100*min(abs(Random.nextDouble()),1e-3)
                val c = Circle(x, y, r)
                val c1 = computeCircleInversion(c, circle)
                assertTrue(c1 is CircleOrLine?, "circle(circle) is CircleOrLine?: $circle $ $c = $c1")
                val p = Point(x, y)
                val p1 = computeCircleInversion(p, circle)
                assertTrue(p1 is Point?, "circle(point) is Point?: $circle $ $p = $p1")
            }
        }
    }
}