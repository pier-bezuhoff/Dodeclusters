package core.geometry

import kotlin.math.pow
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class GeneralizedCircleTest {
    @Test
    fun testInit() {
        assertFails {
            GeneralizedCircle(0.0, 0.0, 0.0, 0.0)
        }
    }

    @Test
    fun testIs_() {
        val cInf = GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY)
        val point = GeneralizedCircle.fromGCircle(Point(20.0, 30.0))
        val line = GeneralizedCircle.fromGCircle(Line(1.0, 2.0, 3.0))
        val circle = GeneralizedCircle.fromGCircle(Circle(20.0, 30.0, 40.0))
        val imCircle = GeneralizedCircle.fromGCircle(ImaginaryCircle(20.0, 30.0, 40.0))
        assertTrue(cInf.isConformalInfinity && cInf.isPoint && !cInf.isLine && !cInf.isRealCircle && !cInf.isImaginaryCircle)
        assertTrue(!point.isConformalInfinity && point.isPoint && !point.isLine && !point.isRealCircle && !point.isImaginaryCircle)
        assertTrue(!line.isConformalInfinity && !line.isPoint && line.isLine && !line.isRealCircle && !line.isImaginaryCircle)
        assertTrue(!circle.isConformalInfinity && !circle.isPoint && !circle.isLine && circle.isRealCircle && !circle.isImaginaryCircle)
        assertTrue(!imCircle.isConformalInfinity && !imCircle.isPoint && !imCircle.isLine && !imCircle.isRealCircle && imCircle.isImaginaryCircle)
    }

    @Test
    fun testNorm() {
        val r = 12.0
        val gc = GeneralizedCircle.fromGCircle(Circle(1.0, -10.0, r))
        assertEquals(r*gc.w, gc.norm, EPSILON, "$gc")
        assertEquals((r*gc.w).pow(2), gc.norm2, EPSILON, "$gc")
    }

    @Test
    fun testR2() {
        val r = 12.0
        val gc = GeneralizedCircle.fromGCircle(Circle(0.0, 0.0, r))
        assertEquals(r*r, gc.r2, EPSILON, "$gc, n2=${gc.norm2}")
    }

    // these fail after 50-100 tries (radius fluctuations)
    @Ignore
    @Test
    fun testApplyTo() {
        repeat(1000) {
            val a = GeneralizedCircle.fromGCircle(randomCircleOrLine())
            val b = GeneralizedCircle.fromGCircle(randomPointCircleOrLine())
            val c = GeneralizedCircle.fromGCircle(randomCircleOrLine())
            // involution
            assertAlmostEquals(
                b,
                a.applyTo(a.applyTo(b)),
                "a=$a, b=$b (after $it runs)\na=${a.toGCircle()}, b=${b.toGCircle()}",
                epsilon = 0.1,
            )
            // symmetry covariance
            assertAlmostEquals(
                c.applyTo(a.applyTo(b)),
                (c.applyTo(a)).applyTo(c.applyTo(b)),
                "a=$a, b=$b, c=$c (after $it runs)\na=${a.toGCircle()}, b=${b.toGCircle()}, c=${c.toGCircle()}",
                epsilon = 0.1
            )
        }
    }

    @Test
    fun testBisector() {
        repeat(100) {
            val a = GeneralizedCircle.fromGCircle(randomCircleOrLine())
            val b = GeneralizedCircle.fromGCircle(randomCircleOrLine())
            val bi1 = a.bisector(b)
            assertAlmostEquals(
                b, bi1.applyTo(a),
                "a=$a, b=$b, bi=$bi1\na=${a.toGCircle()}, b=${b.toGCircle()}, bi=${bi1.toGCircle()}",
                epsilon = 0.1
            )
            val n = Random.nextInt(2..20)
            val k = Random.nextInt(1 until n)
            // adjacent k/n-sectors are "equidistant" under inversion
            assertAlmostEquals(
                a.bisector(b, n, k).applyTo(a.bisector(b, n, k - 1)),
                a.bisector(b, n, k + 1),
                "a=$a, b=$b, n=$n, k=$k\na=${a.toGCircle()}, b=${b.toGCircle()}",
                epsilon = 0.1
            )
        }
    }

    @Test
    fun testInversiveDistance() {
        repeat(100) {
            val a = GeneralizedCircle.fromGCircle(randomCircleOrLine())
            val b = GeneralizedCircle.fromGCircle(randomCircleOrLine())
            val c = GeneralizedCircle.fromGCircle(randomCircleOrLine())
            // inversive distance is invariant under inversion
            assertAlmostEquals(
                a.inversiveDistance(b),
                c.applyTo(a).inversiveDistance(c.applyTo(b)),
                "(after $it runs)",
                epsilon = 1e-2
            )
        }
    }

    @Test
    fun testConversion() {
        val cInf = GeneralizedCircle.fromGCircle(Point.CONFORMAL_INFINITY)
        val point = GeneralizedCircle.fromGCircle(Point(20.0, 30.0))
        val line = GeneralizedCircle.fromGCircle(Line(1.0, 2.0, 3.0))
        val circle = GeneralizedCircle.fromGCircle(Circle(20.0, 30.0, 40.0))
        val imCircle = GeneralizedCircle.fromGCircle(ImaginaryCircle(20.0, 30.0, 40.0))
        // not losing too much accuracy when converting to and fro
        listOf(cInf, point, line, circle, imCircle).forEach { gc ->
            assertAlmostEquals(gc, GeneralizedCircle.fromGCircle(gc.toGCircle()), "$gc")
        }
    }

    @Test
    fun testHomogenousEquality() {
        val c1 = GeneralizedCircle(1.0, 2.0, 3.0, 4.0)
        val c2 = GeneralizedCircle(10.0, 20.0, 30.0, 40.0)
        val c3 = GeneralizedCircle(-0.1, -0.2, -0.3, -0.4)
        var c10 = c1.normalizedPreservingDirection()
        var c20 = c2.normalizedPreservingDirection()
        var c30 = c3.normalizedPreservingDirection()
        assertTrue(c10.homogenousEquals(c10))
        assertTrue(c10.homogenousEquals(c20))
        assertTrue(!c10.homogenousEquals(c30))
        c10 = c1.normalized()
        c20 = c2.normalized()
        c30 = c3.normalized()
        assertTrue(c10.homogenousEquals(c20))
        assertTrue(c10.homogenousEquals(c30))
    }
}