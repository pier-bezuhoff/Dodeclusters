package data.geometry

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
    expected: GeneralizedCircle,
    actual: GeneralizedCircle,
    message: String = "",
    epsilon: Double = 1e-3
) {
    val expectedNormalized = expected.normalized()
    val actualNormalized = actual.normalized()
    assertTrue(
        expectedNormalized.homogenousEquals(actualNormalized, epsilon) || run {
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
        },
        "$actualNormalized shouldBe $expectedNormalized" +
                "\n${actualNormalized.toGCircle()} or ${actualNormalized.toGCircleAs(expectedNormalized.toGCircle())} " +
                "shouldBe ${expectedNormalized.toGCircle()}" +
                "\n\n$message"
    )
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

fun randomCircle(maxAmplitude: Double = 16.0): GeneralizedCircle {
    val x = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val y = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val r = Random.nextDouble(0.01, maxAmplitude)
    return GeneralizedCircle.fromGCircle(Circle(x, y, r))
}

fun randomLine(maxAmplitude: Double = 16.0): GeneralizedCircle {
    val a = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val b = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val c = Random.nextDouble(-maxAmplitude, maxAmplitude)
    return if (a == 0.0 && b == 0.0)
        GeneralizedCircle.fromGCircle(Line(1.0, 0.0, 0.0))
    else
        GeneralizedCircle.fromGCircle(Line(a, b, c))
}

fun randomPoint(maxAmplitude: Double = 16.0): GeneralizedCircle {
    val isConformalInf = Random.nextInt(0..10) == 0
    val a = Random.nextDouble(-maxAmplitude, maxAmplitude)
    val b = Random.nextDouble(-maxAmplitude, maxAmplitude)
    return GeneralizedCircle.fromGCircle(
        if (isConformalInf) Point.CONFORMAL_INFINITY
        else Point(a, b)
    )
}

