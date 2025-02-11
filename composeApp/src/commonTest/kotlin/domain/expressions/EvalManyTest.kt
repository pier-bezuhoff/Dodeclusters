package domain.expressions

import androidx.compose.ui.geometry.Offset
import data.geometry.Circle
import data.geometry.Line
import data.geometry.assertAlmostEquals
import data.geometry.randomCircle
import data.geometry.randomLine
import data.geometry.randomPoint
import data.geometry.randomPointCircleOrLine
import kotlin.test.Test
import kotlin.test.assertTrue

class EvalManyTest {

    @Test
    fun biInversion() {
        val circle1 = Circle(4.0, -8.0, 3.0)
        val circle2 = circle1.rotated(Offset(4f, -5f), 10f)
        repeat(1_000) { // turn around
            val target =
                if (it % 2 == 0)
                    randomPoint()
                else
                    randomCircle()
            // fails for lines...
            var k = 36
            var turnAround = computeBiInversion(
                BiInversionParameters(1.0, k, true),
                circle1, circle2,
                target
            )[k-1]
            assertTrue(turnAround != null)
            assertAlmostEquals(target, turnAround)
            k = 6
            turnAround = computeBiInversion(
                BiInversionParameters(6.0, 3*k, true),
                circle1, circle2,
                target
            )[2*k-1]
            assertTrue(turnAround != null)
            assertAlmostEquals(target, turnAround)
            k = 72
            turnAround = computeBiInversion(
                BiInversionParameters(0.5, k + 3, true),
                circle1, circle2,
                target
            )[k-1]
            assertTrue(turnAround != null)
            assertAlmostEquals(target, turnAround)
            k = 12
            turnAround = computeBiInversion(
                BiInversionParameters(-3.0, k + 3, true),
                circle1, circle2,
                target
            )[k-1]
            assertTrue(turnAround != null)
            assertAlmostEquals(target, turnAround)
        }
        repeat(100) {
            val line = randomLine()
        }
    }
}