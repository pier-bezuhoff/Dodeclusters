package core.geometry

import core.geometry.conformal.GeneralizedCircle
import domain.never
import kotlin.math.abs
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.assertTrue

fun assertAlmostEquals(
    expected: Double,
    actual: Double,
    message: String = "",
    epsilon: Double = 1e-3
) {
    assertTrue(
        abs(expected - actual) < epsilon* abs(actual) + epsilon,
        "$actual shouldBe $expected\n$message"
    )
}

fun assertAlmostEquals(
    expected: GCircle,
    actual: GCircle?,
    message: String = "",
    epsilon: Double = 1e-3
) = assertAlmostEquals(
    GeneralizedCircle.fromGCircle(expected),
    actual?.let { GeneralizedCircle.fromGCircle(actual) },
    message, epsilon
)

fun assertAlmostEquals(
    expected: GeneralizedCircle,
    actual: GeneralizedCircle?,
    message: String = "",
    epsilon: Double = 1e-3
) {
    val expectedNormalized = expected.normalized()
    val actualNormalized = actual?.normalized()
    assertTrue(
        actualNormalized != null && (expectedNormalized.homogenousEquals(actualNormalized, epsilon) || run {
            // TODO: also include BIG circle <=> line equivalence
            val a = expectedNormalized.toGCircle()
            val b = actualNormalized.toGCircleAs(a)
            // yes, im desperate
            a is Circle && b is Circle &&
                    abs(a.x - b.x) < epsilon + abs(b.x) *epsilon &&
                    abs(a.y - b.y) < epsilon + abs(b.y) *epsilon &&
                    abs(a.radius - b.radius) < epsilon + abs(b.radius) *epsilon ||
                    a is Line && b is Line &&
                    abs(a.a - b.a) < epsilon + abs(b.a) *epsilon &&
                    abs(a.b - b.b) < epsilon + abs(b.b) *epsilon &&
                    abs(a.c - b.c) < epsilon + abs(b.c) *epsilon ||
                    a is Point && b is Point &&
                    abs(a.x - b.x) < epsilon + abs(b.x) *epsilon &&
                    abs(a.y - b.y) < epsilon + abs(b.y) *epsilon
        }),
        "$actualNormalized shouldBe $expectedNormalized" +
                "\n${actualNormalized?.toGCircle()} or ${actualNormalized?.toGCircleAs(expectedNormalized.toGCircle())} " +
                "shouldBe ${expectedNormalized.toGCircle()}" +
                "\n\n$message"
    )
}

fun randomCircleOrLine(maxAmplitude: Double = 16.0): CircleOrLine {
    val isCircle = Random.nextBoolean()
    return if (isCircle)
        randomCircle(maxAmplitude)
    else randomLine(maxAmplitude)
}

fun randomPointCircleOrLine(maxAmplitude: Double = 16.0): GCircle =
    when (Random.nextInt(1..3)) {
        1 -> randomPoint(maxAmplitude)
        2 -> randomLine(maxAmplitude)
        3 -> randomCircle(maxAmplitude)
        else -> never()
    }

fun randomCircle(maxAmplitude: Double = 16.0): Circle {
    val x = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val y = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val r = Random.nextDouble(1.0/maxAmplitude, maxAmplitude)
    return Circle(x, y, r)
}

fun randomLine(maxAmplitude: Double = 16.0): Line {
    val a = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val b = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val c = Random.nextDouble(-maxAmplitude, maxAmplitude)
    return when (Random.nextInt(0..10)) {
        1 -> Line(if (a != 0.0) a else 1.0, 0.0, c) // vertical
        2 -> Line(0.0, if (b != 0.0) b else 1.0, c) // horizontal
        else ->
            if (a == 0.0 && b == 0.0)
                Line(1.0, 0.0, 0.0)
            else
                Line(a, b, c)
    }
}

fun randomPoint(maxAmplitude: Double = 16.0): Point {
    val isConformalInf = Random.nextInt(0..10) == 0
    val a = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val b = Random.nextDouble(-maxAmplitude, maxAmplitude)
    return if (isConformalInf) Point.CONFORMAL_INFINITY
    else Point(a, b)
}

