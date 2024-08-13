package data.geometry

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random
import kotlin.random.nextInt
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

    @Test
    fun testApplyTo() {
        repeat(1000) {
            val a = randomCircleOrLine()
            val b = randomCircleOrLine() //randomPointCircleOrLine()
            val c = randomCircleOrLine()
            // involution
            assertAlmostEquals(b, a.applyTo(a.applyTo(b)), "a=$a, b=$b (after $it runs)")
            // symmetry covariance
            assertAlmostEquals(
                c.applyTo(a.applyTo(b)),
                (c.applyTo(a)).applyTo(c.applyTo(b)),
                "a=$a, b=$b, c=$c (after $it runs)"
            )
        }
    }

    @Test
    fun testInversiveDistance() {
        repeat(100) {
            val a = randomCircleOrLine()
            val b = randomCircleOrLine()
            val c = randomCircleOrLine()
            // inversive distance is invariant under inversion
            assertAlmostEquals(
                a.inversiveDistance(b),
                c.applyTo(a).inversiveDistance(c.applyTo(b)),
                "(after $it runs)"
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
        listOf(cInf, point, line, circle, imCircle).forEach { gc ->
            assertAlmostEquals(gc, GeneralizedCircle.fromGCircle(gc.toGCircle()))
        }
    }
}

fun randomCircleOrLine(): GeneralizedCircle {
    val isCircle = Random.nextBoolean()
    return if (isCircle)
        randomCircle()
        else randomLine()
}

fun randomPointCircleOrLine(): GeneralizedCircle =
    when (Random.nextInt(1..3)) {
        1 -> randomPoint()
        2 -> randomLine()
        else -> randomCircle()
    }

fun randomCircle(): GeneralizedCircle {
    val x = Random.nextDouble(-1000.0, 1000.0)
    val y = Random.nextDouble(-1000.0, 1000.0)
    val r = Random.nextDouble(0.1, 1000.0)
    return GeneralizedCircle.fromGCircle(Circle(x, y, r))
}

fun randomLine(): GeneralizedCircle {
    val a = Random.nextDouble(-1000.0, 1000.0)
    val b = Random.nextDouble(-1000.0, 1000.0)
    val c = Random.nextDouble(-1000.0, 1000.0)
    return GeneralizedCircle.fromGCircle(Line(a, b, c))
}

fun randomPoint(): GeneralizedCircle {
    val isConformalInf = Random.nextInt(0..10) == 0
    val a = Random.nextDouble(-1000.0, 1000.0)
    val b = Random.nextDouble(-1000.0, 1000.0)
    return GeneralizedCircle.fromGCircle(
        if (isConformalInf) Point.CONFORMAL_INFINITY
        else Point(a, b)
    )
}

fun assertAlmostEquals(expected: Double, actual: Double, message: String = "") {
    assertTrue(abs(expected - actual) < 10*EPSILON, "$actual shouldBe $expected\n$message")
}

fun assertAlmostEquals(expected: GeneralizedCircle, actual: GeneralizedCircle, message: String = "") {
    assertTrue(
        expected.homogenousEquals(actual, 100*EPSILON),
        "${actual.normalized()} shouldBe ${expected.normalized()}" +
                "\nn2=${actual.norm2} vs n2=${expected.norm2}" +
                "\n${actual.toGCircle()} shouldBe ${expected.toGCircle()}" +
                "\n$message"
    )
}